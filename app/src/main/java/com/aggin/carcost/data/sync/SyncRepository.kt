package com.aggin.carcost.data.sync

import android.util.Log
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Состояние синхронизации
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String = "Синхронизация завершена") : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Репозиторий для синхронизации локальных и удаленных данных
 */
class SyncRepository(
    private val localCarRepo: CarRepository,
    private val localExpenseRepo: ExpenseRepository,
    private val localReminderRepo: MaintenanceReminderRepository,
    private val localTagRepo: ExpenseTagRepository,
    private val supabaseAuthRepo: SupabaseAuthRepository,
    private val supabaseCarRepo: SupabaseCarRepository,
    private val supabaseExpenseRepo: SupabaseExpenseRepository,
    private val supabaseReminderRepo: SupabaseMaintenanceReminderRepository,
    private val supabaseTagRepo: SupabaseExpenseTagRepository
) {

    companion object {
        private const val TAG = "SyncRepository"
        private const val SYNC_TIMESTAMP_KEY = "last_sync_timestamp"
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: Flow<SyncState> = _syncState.asStateFlow()

    /**
     * Безопасная начальная синхронизация после входа/регистрации
     * Синхронизирует сначала автомобили, затем только если они есть - расходы и напоминания
     */
    suspend fun safeInitialSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing
            Log.d(TAG, "Starting safe initial sync...")

            // 1. Сначала синхронизируем автомобили
            syncCars()

            // 2. Затем остальное только если есть автомобили
            val localCars = localCarRepo.getAllCars().first()
            if (localCars.isNotEmpty()) {
                Log.d(TAG, "Found ${localCars.size} cars, syncing expenses and reminders...")
                syncExpenses()
                syncReminders()
            } else {
                Log.d(TAG, "No cars found, skipping expenses and reminders sync")
            }

            // 3. Теги синхронизируем всегда
            syncTags()

            _syncState.value = SyncState.Success()
            Log.d(TAG, "Safe initial sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации")
            Log.e(TAG, "Safe initial sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Полная синхронизация всех данных
     */
    suspend fun fullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing
            Log.d(TAG, "Starting full sync...")

            // Синхронизируем в правильном порядке (с учетом зависимостей)
            syncCars()
            syncExpenses()
            syncReminders()
            syncTags()

            _syncState.value = SyncState.Success()
            Log.d(TAG, "Full sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации")
            Log.e(TAG, "Full sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Синхронизация автомобилей
     */
    private suspend fun syncCars() {
        Log.d(TAG, "Syncing cars...")

        // 1. Pull - получаем данные с сервера
        val remoteCarsResult = supabaseCarRepo.getAllCars()
        val remoteCars = remoteCarsResult.getOrNull() ?: emptyList()

        // 2. Push - отправляем локальные изменения
        val localCars = localCarRepo.getAllCars().first()

        for (localCar in localCars) {
            val remoteCar = remoteCars.find { it.id == localCar.id }

            when {
                // Новый автомобиль (только локально)
                remoteCar == null -> {
                    Log.d(TAG, "Pushing new car: ${localCar.id}")
                    val result = supabaseCarRepo.insertCar(localCar)
                    result.getOrNull()?.let { insertedCar ->
                        // Обновляем локальный ID, если сервер вернул другой
                        if (insertedCar.id != localCar.id) {
                            localCarRepo.updateCar(localCar.copy(id = insertedCar.id))
                        }
                    }
                }
                // Автомобиль изменен локально позже
                localCar.updatedAt > remoteCar.updatedAt -> {
                    Log.d(TAG, "Updating car on server: ${localCar.id}")
                    supabaseCarRepo.updateCar(localCar)
                }
                // Автомобиль изменен на сервере позже
                remoteCar.updatedAt > localCar.updatedAt -> {
                    Log.d(TAG, "Updating car locally: ${localCar.id}")
                    localCarRepo.updateCar(remoteCar)
                }
            }
        }

        // 3. Pull новых автомобилей с сервера
        for (remoteCar in remoteCars) {
            if (localCars.none { it.id == remoteCar.id }) {
                Log.d(TAG, "Pulling new car from server: ${remoteCar.id}")
                localCarRepo.insertCar(remoteCar)
            }
        }
    }

    /**
     * Синхронизация расходов
     */
    private suspend fun syncExpenses() {
        Log.d(TAG, "Syncing expenses...")

        val localCars = localCarRepo.getAllCars().first()

        // Проверяем, что автомобили уже синхронизированы
        if (localCars.isEmpty()) {
            Log.w(TAG, "No cars found locally, skipping expense sync")
            return
        }

        for (car in localCars) {
            // Проверяем, что автомобиль существует на сервере
            val remoteCarResult = supabaseCarRepo.getCarById(car.id)
            if (remoteCarResult.isFailure) {
                Log.w(TAG, "Car ${car.id} not found on server, skipping its expenses")
                continue
            }

            // 1. Pull - получаем расходы с сервера
            val remoteExpensesResult = supabaseExpenseRepo.getExpensesByCarId(car.id)
            val remoteExpenses = remoteExpensesResult.getOrNull() ?: emptyList()

            // 2. Push - отправляем локальные изменения
            val localExpenses = localExpenseRepo.getExpensesByCarId(car.id).first()

            for (localExpense in localExpenses) {
                val remoteExpense = remoteExpenses.find { it.id == localExpense.id }

                when {
                    // Новый расход (только локально)
                    remoteExpense == null -> {
                        Log.d(TAG, "Pushing new expense: ${localExpense.id}")
                        val result = supabaseExpenseRepo.insertExpense(localExpense)
                        result.getOrNull()?.let { insertedExpense ->
                            if (insertedExpense.id != localExpense.id) {
                                localExpenseRepo.updateExpense(localExpense.copy(id = insertedExpense.id))
                            }
                        }
                    }
                    // Расход изменен локально позже
                    localExpense.updatedAt > remoteExpense.updatedAt -> {
                        Log.d(TAG, "Updating expense on server: ${localExpense.id}")
                        supabaseExpenseRepo.updateExpense(localExpense)
                    }
                    // Расход изменен на сервере позже
                    remoteExpense.updatedAt > localExpense.updatedAt -> {
                        Log.d(TAG, "Updating expense locally: ${localExpense.id}")
                        localExpenseRepo.updateExpense(remoteExpense)
                    }
                }
            }

            // 3. Pull новых расходов с сервера
            for (remoteExpense in remoteExpenses) {
                if (localExpenses.none { it.id == remoteExpense.id }) {
                    Log.d(TAG, "Pulling new expense from server: ${remoteExpense.id}")
                    localExpenseRepo.insertExpense(remoteExpense)
                }
            }
        }
    }

    /**
     * Синхронизация напоминаний о техобслуживании
     */
    private suspend fun syncReminders() {
        Log.d(TAG, "Syncing reminders...")

        val localCars = localCarRepo.getAllCars().first()

        // Проверяем, что автомобили уже синхронизированы
        if (localCars.isEmpty()) {
            Log.w(TAG, "No cars found locally, skipping reminder sync")
            return
        }

        for (car in localCars) {
            // Проверяем, что автомобиль существует на сервере
            val remoteCarResult = supabaseCarRepo.getCarById(car.id)
            if (remoteCarResult.isFailure) {
                Log.w(TAG, "Car ${car.id} not found on server, skipping its reminders")
                continue
            }

            // 1. Pull - получаем напоминания с сервера
            val remoteRemindersResult = supabaseReminderRepo.getRemindersByCarId(car.id)
            val remoteReminders = remoteRemindersResult.getOrNull() ?: emptyList()

            // 2. Push - отправляем локальные изменения
            val localReminders = localReminderRepo.getActiveReminders(car.id).first()

            for (localReminder in localReminders) {
                val remoteReminder = remoteReminders.find { it.id == localReminder.id }

                when {
                    // Новое напоминание (только локально)
                    remoteReminder == null -> {
                        Log.d(TAG, "Pushing new reminder: ${localReminder.id}")
                        val result = supabaseReminderRepo.insertReminder(localReminder)
                        result.getOrNull()?.let { insertedReminder ->
                            if (insertedReminder.id != localReminder.id) {
                                localReminderRepo.updateReminder(localReminder.copy(id = insertedReminder.id))
                            }
                        }
                    }
                    // Напоминание изменено локально позже
                    localReminder.updatedAt > remoteReminder.updatedAt -> {
                        Log.d(TAG, "Updating reminder on server: ${localReminder.id}")
                        supabaseReminderRepo.updateReminder(localReminder)
                    }
                    // Напоминание изменено на сервере позже
                    remoteReminder.updatedAt > localReminder.updatedAt -> {
                        Log.d(TAG, "Updating reminder locally: ${localReminder.id}")
                        localReminderRepo.updateReminder(remoteReminder)
                    }
                }
            }

            // 3. Pull новых напоминаний с сервера
            for (remoteReminder in remoteReminders) {
                if (localReminders.none { it.id == remoteReminder.id }) {
                    Log.d(TAG, "Pulling new reminder from server: ${remoteReminder.id}")
                    localReminderRepo.insertReminder(remoteReminder)
                }
            }
        }
    }

    /**
     * Синхронизация тегов
     */
    private suspend fun syncTags() {
        Log.d(TAG, "Syncing tags...")

        val userId = supabaseAuthRepo.getUserId() ?: return

        // 1. Pull - получаем теги с сервера
        val remoteTagsResult = supabaseTagRepo.getAllTags()
        val remoteTags = remoteTagsResult.getOrNull() ?: emptyList()

        // 2. Push - отправляем локальные изменения
        val localTags = localTagRepo.getTagsByUser(userId).first()

        for (localTag in localTags) {
            val remoteTag = remoteTags.find { it.id == localTag.id }

            when {
                // Новый тег (только локально)
                remoteTag == null -> {
                    Log.d(TAG, "Pushing new tag: ${localTag.id}")
                    val result = supabaseTagRepo.insertTag(localTag)
                    result.getOrNull()?.let { insertedTag ->
                        if (insertedTag.id != localTag.id) {
                            // Обновляем ID, если нужно
                            // Примечание: для тегов может потребоваться дополнительная логика
                        }
                    }
                }
                // Теги не имеют поля updatedAt, поэтому просто перезаписываем
                localTag != remoteTag -> {
                    Log.d(TAG, "Updating tag on server: ${localTag.id}")
                    supabaseTagRepo.updateTag(localTag)
                }
            }
        }

        // 3. Pull новых тегов с сервера
        for (remoteTag in remoteTags) {
            if (localTags.none { it.id == remoteTag.id }) {
                Log.d(TAG, "Pulling new tag from server: ${remoteTag.id}")
                // Вставка тега в локальную БД
                // Примечание: требуется метод insertTag в localTagRepo
            }
        }
    }

    /**
     * Синхронизация только автомобилей
     */
    suspend fun syncCarsOnly(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing
            syncCars()
            _syncState.value = SyncState.Success("Автомобили синхронизированы")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации автомобилей")
            Result.failure(e)
        }
    }

    /**
     * Синхронизация только расходов для конкретного автомобиля
     */
    suspend fun syncExpensesForCar(carId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing

            // Проверяем, что автомобиль существует на сервере
            val remoteCarResult = supabaseCarRepo.getCarById(carId)
            if (remoteCarResult.isFailure) {
                return@withContext Result.failure(Exception("Автомобиль не найден на сервере"))
            }

            val remoteExpensesResult = supabaseExpenseRepo.getExpensesByCarId(carId)
            val remoteExpenses = remoteExpensesResult.getOrNull() ?: emptyList()
            val localExpenses = localExpenseRepo.getExpensesByCarId(carId).first()

            // Синхронизация расходов
            for (localExpense in localExpenses) {
                val remoteExpense = remoteExpenses.find { it.id == localExpense.id }

                when {
                    remoteExpense == null -> {
                        supabaseExpenseRepo.insertExpense(localExpense)
                    }
                    localExpense.updatedAt > remoteExpense.updatedAt -> {
                        supabaseExpenseRepo.updateExpense(localExpense)
                    }
                    remoteExpense.updatedAt > localExpense.updatedAt -> {
                        localExpenseRepo.updateExpense(remoteExpense)
                    }
                }
            }

            for (remoteExpense in remoteExpenses) {
                if (localExpenses.none { it.id == remoteExpense.id }) {
                    localExpenseRepo.insertExpense(remoteExpense)
                }
            }

            _syncState.value = SyncState.Success("Расходы синхронизированы")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации расходов")
            Result.failure(e)
        }
    }

    /**
     * Очистка локальных данных при выходе
     */
    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Clearing local data...")
            // Очистка локальной базы данных
            // Примечание: требуется реализовать методы удаления во всех репозиториях
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear local data", e)
        }
    }

    /**
     * Проверка, требуется ли синхронизация
     */
    fun needsSync(): Boolean {
        return supabaseAuthRepo.isUserLoggedIn()
    }
}