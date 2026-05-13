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
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseFluidLevelRepository
import com.aggin.carcost.data.remote.repository.SupabaseGpsTripRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarIncidentRepository
import com.aggin.carcost.data.remote.repository.SupabaseInsurancePolicyRepository
import com.aggin.carcost.data.remote.repository.SupabaseSavingsGoalRepository
import com.aggin.carcost.data.remote.repository.SupabaseCategoryBudgetRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarDocumentRepository
import com.aggin.carcost.data.remote.repository.SupabaseAchievementRepository
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    private val localTagDao: com.aggin.carcost.data.local.database.dao.ExpenseTagDao,
    private val localPlannedExpenseRepo: PlannedExpenseRepository,
    private val supabaseAuthRepo: SupabaseAuthRepository,
    private val supabaseCarRepo: SupabaseCarRepository,
    private val supabaseExpenseRepo: SupabaseExpenseRepository,
    private val supabaseReminderRepo: SupabaseMaintenanceReminderRepository,
    private val supabaseTagRepo: SupabaseExpenseTagRepository,
    private val supabasePlannedExpenseRepo: SupabasePlannedExpenseRepository,
    // ── Extended sync ────────────────────────────────────────────────────────
    private val localDb: com.aggin.carcost.data.local.database.AppDatabase? = null,
    private val supabaseFluidLevelRepo: SupabaseFluidLevelRepository? = null,
    private val supabaseGpsTripRepo: SupabaseGpsTripRepository? = null,
    private val supabaseIncidentRepo: SupabaseCarIncidentRepository? = null,
    private val supabaseInsuranceRepo: SupabaseInsurancePolicyRepository? = null,
    private val supabaseSavingsGoalRepo: SupabaseSavingsGoalRepository? = null,
    private val supabaseCategoryBudgetRepo: SupabaseCategoryBudgetRepository? = null,
    private val supabaseCarDocumentRepo: SupabaseCarDocumentRepository? = null,
    private val supabaseAchievementRepo: SupabaseAchievementRepository? = null
) {

    companion object {
        private const val TAG = "SyncRepository"
        private const val SYNC_TIMESTAMP_KEY = "last_sync_timestamp"
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: Flow<SyncState> = _syncState.asStateFlow()

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
            syncTagLinks()
            syncPlannedExpenses()
            syncFluidLevels()
            syncGpsTrips()
            syncIncidents()
            syncInsurancePolicies()
            syncSavingsGoals()
            syncCategoryBudgets()
            syncCarDocuments()
            syncAchievements()

            _syncState.value = SyncState.Success()
            Log.d(TAG, "Full sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации")
            Log.e(TAG, "Full sync failed", e)
            Result.failure(e)
        }
    }

    @Serializable
    private data class CarMemberIdDto(
        @SerialName("car_id") val carId: String
    )

    /** Возвращает set car_id машин, где текущий пользователь — OWNER. */
    private suspend fun fetchOwnedCarIds(userId: String): Set<String> {
        return try {
            supabase.from("car_members")
                .select { filter { eq("user_id", userId); eq("role", "OWNER") } }
                .decodeList<CarMemberIdDto>()
                .map { it.carId }
                .toSet()
        } catch (e: Exception) {
            Log.w(TAG, "fetchOwnedCarIds failed, defaulting to empty: ${e.message}")
            emptySet()
        }
    }

    /**
     * Синхронизация автомобилей
     */
    private suspend fun syncCars() {
        Log.d(TAG, "Syncing cars...")

        val userId = supabaseAuthRepo.getUserId() ?: run {
            Log.w(TAG, "syncCars: user not authenticated, skipping")
            return
        }

        // Получаем машины, где текущий пользователь — OWNER, чтобы не затирать чужих владельцев
        val ownedCarIds = fetchOwnedCarIds(userId)
        Log.d(TAG, "Owned car ids: $ownedCarIds")

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
                    if (localCar.id !in ownedCarIds) {
                        // Это shared-машина (мы DRIVER) — не пушим, чтобы не затереть владельца
                        Log.d(TAG, "Skipping push for shared car: ${localCar.id}")
                    } else {
                        Log.d(TAG, "Pushing new car: ${localCar.id}")
                        val result = supabaseCarRepo.insertCar(localCar)
                        result.getOrNull()?.let { insertedCar ->
                            if (insertedCar.id != localCar.id) {
                                localCarRepo.updateCar(localCar.copy(id = insertedCar.id))
                            }
                        }
                    }
                }
                // Автомобиль изменен локально позже — обновляем только если мы владелец
                localCar.updatedAt > remoteCar.updatedAt -> {
                    if (localCar.id in ownedCarIds) {
                        Log.d(TAG, "Updating car on server: ${localCar.id}")
                        supabaseCarRepo.updateCar(localCar)
                    } else {
                        Log.d(TAG, "Skipping update for shared car: ${localCar.id}")
                    }
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

        for (car in localCars) {
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

        for (car in localCars) {
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
        val remoteTagsResult = supabaseTagRepo.getAllTags() // ✅ ИСПРАВЛЕНО
        val remoteTags = remoteTagsResult.getOrNull() ?: emptyList()

        // 2. Push - отправляем локальные теги
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
                try {
                    localTagRepo.insertTag(remoteTag)
                    Log.d(TAG, "✅ Tag saved locally: ${remoteTag.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to save tag locally: ${remoteTag.id}", e)
                }
            }
        }

        Log.d(TAG, "✅ Tags sync completed: ${remoteTags.size} remote, ${localTags.size} local")
    }

    /**
     * Синхронизация связей тегов с расходами
     */
    private suspend fun syncTagLinks() {
        Log.d(TAG, "Syncing tag links (batch)...")
        try {
            // Single API call instead of N+1 — fetch all cross-refs the user can see
            val crossRefs = supabaseTagRepo.getAllCrossRefs().getOrNull() ?: return
            Log.d(TAG, "Found ${crossRefs.size} tag cross-refs from Supabase")
            for (ref in crossRefs) {
                try {
                    localTagDao.insertExpenseTagCrossRef(ref)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to save tag link: ${e.message}")
                }
            }
            Log.d(TAG, "✅ Tag links sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Tag links sync failed", e)
        }
    }

    /**
     * ✅ НОВОЕ: Синхронизация запланированных покупок
     */
    private suspend fun syncPlannedExpenses() {
        Log.d(TAG, "Syncing planned expenses...")

        val localCars = localCarRepo.getAllCars().first()

        for (car in localCars) {
            try {
                // 1. Pull - получаем планы с сервера
                val remotePlansResult = supabasePlannedExpenseRepo.getPlannedExpensesByCarId(car.id)
                val remotePlans = remotePlansResult.getOrNull() ?: emptyList()

                // 2. Push - отправляем локальные изменения
                val localPlans = localPlannedExpenseRepo.getPlannedExpensesByCarId(car.id).first()

                for (localPlan in localPlans) {
                    val remotePlan = remotePlans.find { it.id == localPlan.id }

                    when {
                        // Новый план (только локально)
                        remotePlan == null -> {
                            Log.d(TAG, "Pushing new planned expense: ${localPlan.id}")
                            val result = supabasePlannedExpenseRepo.insertPlannedExpense(localPlan)
                            result.fold(
                                onSuccess = {
                                    // Помечаем как синхронизированный
                                    localPlannedExpenseRepo.updateSyncStatus(localPlan.id, true)
                                    Log.d(TAG, "✅ Planned expense synced: ${localPlan.id}")
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "❌ Failed to push planned expense: ${localPlan.id}", error)
                                }
                            )
                        }
                        // План изменен локально позже
                        localPlan.updatedAt > remotePlan.updatedAt -> {
                            Log.d(TAG, "Updating planned expense on server: ${localPlan.id}")
                            val result = supabasePlannedExpenseRepo.updatePlannedExpense(localPlan)
                            result.fold(
                                onSuccess = {
                                    localPlannedExpenseRepo.updateSyncStatus(localPlan.id, true)
                                    Log.d(TAG, "✅ Planned expense updated: ${localPlan.id}")
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "❌ Failed to update planned expense: ${localPlan.id}", error)
                                }
                            )
                        }
                        // План изменен на сервере позже
                        remotePlan.updatedAt > localPlan.updatedAt -> {
                            Log.d(TAG, "Updating planned expense locally: ${localPlan.id}")
                            localPlannedExpenseRepo.updatePlannedExpense(remotePlan)
                        }
                    }
                }

                // 3. Pull новых планов с сервера
                for (remotePlan in remotePlans) {
                    if (localPlans.none { it.id == remotePlan.id }) {
                        Log.d(TAG, "Pulling new planned expense from server: ${remotePlan.id}")
                        localPlannedExpenseRepo.insertPlannedExpense(remotePlan)
                    }
                }

                Log.d(TAG, "✅ Planned expenses sync completed for car: ${car.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Planned expenses sync failed for car: ${car.id}", e)
            }
        }

        Log.d(TAG, "✅ All planned expenses synced")
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
    suspend fun syncExpensesForCar(carId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing

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
     * ✅ НОВОЕ: Синхронизация только запланированных покупок для конкретного автомобиля
     */
    suspend fun syncPlannedExpensesForCar(carId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))
            }

            _syncState.value = SyncState.Syncing

            val remotePlansResult = supabasePlannedExpenseRepo.getPlannedExpensesByCarId(carId)
            val remotePlans = remotePlansResult.getOrNull() ?: emptyList()
            val localPlans = localPlannedExpenseRepo.getPlannedExpensesByCarId(carId).first()

            // Push локальных изменений
            for (localPlan in localPlans) {
                val remotePlan = remotePlans.find { it.id == localPlan.id }

                when {
                    remotePlan == null -> {
                        supabasePlannedExpenseRepo.insertPlannedExpense(localPlan)
                            .onSuccess { localPlannedExpenseRepo.updateSyncStatus(localPlan.id, true) }
                    }
                    localPlan.updatedAt > remotePlan.updatedAt -> {
                        supabasePlannedExpenseRepo.updatePlannedExpense(localPlan)
                            .onSuccess { localPlannedExpenseRepo.updateSyncStatus(localPlan.id, true) }
                    }
                    remotePlan.updatedAt > localPlan.updatedAt -> {
                        localPlannedExpenseRepo.updatePlannedExpense(remotePlan)
                    }
                }
            }

            // Pull новых планов
            for (remotePlan in remotePlans) {
                if (localPlans.none { it.id == remotePlan.id }) {
                    localPlannedExpenseRepo.insertPlannedExpense(remotePlan)
                }
            }

            _syncState.value = SyncState.Success("Планы синхронизированы")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Ошибка синхронизации планов")
            Result.failure(e)
        }
    }

    // ── Extended sync methods ─────────────────────────────────────────────────

    private suspend fun syncFluidLevels() {
        val db = localDb ?: return
        val repo = supabaseFluidLevelRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getFluidLevelsByCarId(car.id).getOrNull() ?: continue
            val local = db.fluidLevelDao().getFluidLevelsByCarIdSync(car.id)
            // Push local → remote
            for (item in local) {
                if (remote.none { it.id == item.id } || remote.firstOrNull { it.id == item.id }?.updatedAt?.let { it < item.updatedAt } == true) {
                    repo.upsertFluidLevel(item)
                }
            }
            // Pull remote → local
            for (item in remote) {
                val existing = local.firstOrNull { it.id == item.id }
                if (existing == null) db.fluidLevelDao().insert(item)
                else if (item.updatedAt > existing.updatedAt) db.fluidLevelDao().update(item)
            }
        }
    }

    private suspend fun syncGpsTrips() {
        val db = localDb ?: return
        val repo = supabaseGpsTripRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.gpsTripDao().getTripsByCarIdSync(car.id)
            for (item in local) {
                if (remote.none { it.id == item.id }) repo.upsert(item)
            }
            for (item in remote) {
                if (local.none { it.id == item.id }) db.gpsTripDao().insert(item)
            }
        }
    }

    private suspend fun syncIncidents() {
        val db = localDb ?: return
        val repo = supabaseIncidentRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.carIncidentDao().getIncidentsByCarIdSync(car.id)
            for (item in local) {
                if (remote.none { it.id == item.id }) repo.upsert(item)
            }
            for (item in remote) {
                if (local.none { it.id == item.id }) db.carIncidentDao().insertIncident(item)
            }
        }
    }

    private suspend fun syncInsurancePolicies() {
        val db = localDb ?: return
        val repo = supabaseInsuranceRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.insurancePolicyDao().getPoliciesForCarSync(car.id)
            for (item in local) {
                if (remote.none { it.id == item.id }) repo.upsert(item)
            }
            for (item in remote) {
                if (local.none { it.id == item.id }) db.insurancePolicyDao().insert(item)
            }
        }
    }

    private suspend fun syncSavingsGoals() {
        val db = localDb ?: return
        val repo = supabaseSavingsGoalRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.savingsGoalDao().getGoalsByCarIdSync(car.id)
            for (item in local) {
                val remoteItem = remote.firstOrNull { it.id == item.id }
                if (remoteItem == null || item.updatedAt > remoteItem.updatedAt) repo.upsert(item)
            }
            for (item in remote) {
                val localItem = local.firstOrNull { it.id == item.id }
                if (localItem == null) db.savingsGoalDao().insert(item)
                else if (item.updatedAt > localItem.updatedAt) db.savingsGoalDao().update(item)
            }
        }
    }

    private suspend fun syncCategoryBudgets() {
        val db = localDb ?: return
        val repo = supabaseCategoryBudgetRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.categoryBudgetDao().getAllForCarSync(car.id)
            for (item in local) {
                val remoteItem = remote.firstOrNull { it.id == item.id }
                if (remoteItem == null || item.updatedAt > remoteItem.updatedAt) repo.upsert(item)
            }
            for (item in remote) {
                val localItem = local.firstOrNull { it.id == item.id }
                if (localItem == null) db.categoryBudgetDao().insertBudget(item)
                else if (item.updatedAt > localItem.updatedAt) db.categoryBudgetDao().updateBudget(item)
            }
        }
    }

    private suspend fun syncCarDocuments() {
        val db = localDb ?: return
        val repo = supabaseCarDocumentRepo ?: return
        val cars = localCarRepo.getAllCars().first()
        for (car in cars) {
            val remote = repo.getByCarId(car.id).getOrNull() ?: continue
            val local = db.carDocumentDao().getDocumentsByCarIdSync(car.id)
            for (item in local) {
                val remoteItem = remote.firstOrNull { it.id == item.id }
                if (remoteItem == null || item.updatedAt > remoteItem.updatedAt) repo.upsert(item)
            }
            for (item in remote) {
                val localItem = local.firstOrNull { it.id == item.id }
                if (localItem == null) db.carDocumentDao().insertDocument(item)
                else if (item.updatedAt > localItem.updatedAt) db.carDocumentDao().updateDocument(item)
            }
        }
    }

    private suspend fun syncAchievements() {
        val db = localDb ?: return
        val repo = supabaseAchievementRepo ?: return
        val userId = supabaseAuthRepo.getUserId() ?: return
        val remote = repo.getByUserId(userId).getOrNull() ?: return
        val local = db.achievementDao().getAchievementsSync(userId)
        // Push local → remote (achievements are append-only)
        for (item in local) {
            if (remote.none { it.id == item.id }) repo.upsert(item)
        }
        // Pull remote → local
        for (item in remote) {
            if (local.none { it.id == item.id }) db.achievementDao().upsert(item)
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
     * Безопасная начальная синхронизация (не бросает исключения)
     */
    suspend fun safeInitialSync() = withContext(Dispatchers.IO) {
        try {
            if (!supabaseAuthRepo.isUserLoggedIn()) {
                Log.w(TAG, "User not logged in - skipping sync")
                return@withContext
            }

            Log.d(TAG, "Starting safe initial sync...")
            _syncState.value = SyncState.Syncing

            try { syncCars(); Log.d(TAG, "✅ Cars synced") } catch (e: Exception) { Log.e(TAG, "❌ Cars failed", e) }
            try { syncExpenses(); Log.d(TAG, "✅ Expenses synced") } catch (e: Exception) { Log.e(TAG, "❌ Expenses failed", e) }
            try { syncReminders(); Log.d(TAG, "✅ Reminders synced") } catch (e: Exception) { Log.e(TAG, "❌ Reminders failed", e) }
            try { syncTags(); Log.d(TAG, "✅ Tags synced") } catch (e: Exception) { Log.e(TAG, "❌ Tags failed", e) }
            try { syncPlannedExpenses(); Log.d(TAG, "✅ Planned expenses synced") } catch (e: Exception) { Log.e(TAG, "❌ Planned expenses failed", e) }
            try { syncFluidLevels(); Log.d(TAG, "✅ Fluid levels synced") } catch (e: Exception) { Log.e(TAG, "❌ Fluid levels failed", e) }
            try { syncGpsTrips(); Log.d(TAG, "✅ GPS trips synced") } catch (e: Exception) { Log.e(TAG, "❌ GPS trips failed", e) }
            try { syncIncidents(); Log.d(TAG, "✅ Incidents synced") } catch (e: Exception) { Log.e(TAG, "❌ Incidents failed", e) }
            try { syncInsurancePolicies(); Log.d(TAG, "✅ Insurance synced") } catch (e: Exception) { Log.e(TAG, "❌ Insurance failed", e) }
            try { syncSavingsGoals(); Log.d(TAG, "✅ Goals synced") } catch (e: Exception) { Log.e(TAG, "❌ Goals failed", e) }
            try { syncCategoryBudgets(); Log.d(TAG, "✅ Budgets synced") } catch (e: Exception) { Log.e(TAG, "❌ Budgets failed", e) }
            try { syncCarDocuments(); Log.d(TAG, "✅ Documents synced") } catch (e: Exception) { Log.e(TAG, "❌ Documents failed", e) }
            try { syncAchievements(); Log.d(TAG, "✅ Achievements synced") } catch (e: Exception) { Log.e(TAG, "❌ Achievements failed", e) }

            _syncState.value = SyncState.Success()
            Log.d(TAG, "✅ Safe initial sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Safe sync failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Ошибка")
        }
    }

    /**
     * Проверка, требуется ли синхронизация
     */
    fun needsSync(): Boolean {
        return supabaseAuthRepo.isUserLoggedIn()
    }
}