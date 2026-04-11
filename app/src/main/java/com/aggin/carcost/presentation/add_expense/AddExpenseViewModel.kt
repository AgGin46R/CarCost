package com.aggin.carcost.presentation.screens.add_expense

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.*
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.domain.categorization.ExpenseCategoryClassifier
import com.aggin.carcost.domain.gamification.AchievementChecker
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.supabase
import io.github.jan.supabase.storage.storage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

data class AddExpenseUiState(
    val expenseId: String = java.util.UUID.randomUUID().toString(),
    val category: ExpenseCategory = ExpenseCategory.FUEL,
    val amount: String = "",
    val odometer: String = "",
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val location: String = "",

    // Для топлива
    val fuelLiters: String = "",
    val isFullTank: Boolean = false,

    // Для обслуживания
    val serviceType: ServiceType? = null,
    val workshopName: String = "",
    val maintenanceParts: String = "",

    // Чек
    val receiptPhotoUri: String? = null,
    val isUploadingReceipt: Boolean = false,

    // Теги
    val availableTags: List<ExpenseTag> = emptyList(),
    val selectedTags: List<ExpenseTag> = emptyList(),

    // Связь с планом
    val plannedExpenseId: String? = null,
    val isFromPlannedExpense: Boolean = false,

    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = "",
    val categorySetManually: Boolean = false,
    val suggestedOdometer: Int? = null
)

class AddExpenseViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)
    private val carId: String = savedStateHandle.get<String>("carId") ?: "" // ✅ String UUID
    private val plannedId: String? = savedStateHandle.get<String>("plannedId")
    private val initialCategory: String? = savedStateHandle.get<String>("category")

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagDao = database.expenseTagDao()
    private val plannedExpenseRepository = PlannedExpenseRepository(database.plannedExpenseDao())

    // Supabase репозитории
    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        android.util.Log.d("AddExpense", "=== AddExpenseViewModel init ===")
        android.util.Log.d("AddExpense", "carId: $carId")
        android.util.Log.d("AddExpense", "plannedId: $plannedId")

        viewModelScope.launch {
            // Применяем начальную категорию из Quick Add чипов
            initialCategory?.let { cat ->
                runCatching { ExpenseCategory.valueOf(cat) }.getOrNull()?.let { category ->
                    _uiState.value = _uiState.value.copy(category = category)
                }
            }

            // Загружаем текущий пробег автомобиля
            val car = carRepository.getCarById(carId)
            car?.let {
                _uiState.value = _uiState.value.copy(odometer = it.currentOdometer.toString())

                // Подсказка одометра: базовый одометр + км из GPS-поездок с последней заправки
                try {
                    val gpsTripDao = AppDatabase.getDatabase(getApplication()).gpsTripDao()
                    val lastFuelExpense = database.expenseDao()
                        .getExpensesByCar(carId).firstOrNull()
                        ?.filter { e -> e.category == com.aggin.carcost.data.local.database.entities.ExpenseCategory.FUEL }
                        ?.maxByOrNull { e -> e.date }
                    val since = lastFuelExpense?.date ?: 0L
                    val tripsResult = gpsTripDao.getTripsSince(carId, since).firstOrNull()
                    val kmSinceLastFuel = tripsResult?.sumOf { t -> t.distanceKm }?.toInt() ?: 0
                    val suggested = it.currentOdometer + kmSinceLastFuel
                    if (kmSinceLastFuel > 0) {
                        _uiState.value = _uiState.value.copy(suggestedOdometer = suggested)
                    }
                } catch (_: Exception) {}
            }

            // ✅ СНАЧАЛА синхронизируем теги из Supabase
            val userId = supabaseAuth.getUserId()
            if (userId != null) {
                try {
                    val supabaseTagRepo = com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository(supabaseAuth)
                    val remoteTagsResult = supabaseTagRepo.getAllTags()

                    remoteTagsResult.getOrNull()?.let { remoteTags ->
                        // Сохраняем удаленные теги в локальную БД
                        for (remoteTag in remoteTags) {
                            try {
                                tagDao.insertTag(remoteTag)
                            } catch (e: Exception) {
                                android.util.Log.e("AddExpense", "Error saving tag: ${e.message}")
                            }
                        }
                        android.util.Log.d("AddExpense", "✅ Synced ${remoteTags.size} tags from Supabase")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddExpense", "Failed to sync tags from Supabase", e)
                }


                // ✅ ВАЖНО: Загружаем данные из плана ПЕРЕД collect (иначе код не выполнится)
                if (plannedId != null) {
                    try {
                        val plannedExpense = plannedExpenseRepository.getPlannedExpenseById(plannedId)
                        if (plannedExpense != null) {
                            android.util.Log.d("AddExpense", "✅ Loaded planned expense: ${plannedExpense.title}")
                            _uiState.value = _uiState.value.copy(
                                category = plannedExpense.category,
                                amount = plannedExpense.estimatedAmount?.toString() ?: "",
                                date = plannedExpense.targetDate ?: System.currentTimeMillis(),
                                description = plannedExpense.description ?: plannedExpense.title,
                                odometer = plannedExpense.targetOdometer?.toString() ?: _uiState.value.odometer,
                                plannedExpenseId = plannedId,
                                isFromPlannedExpense = true
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AddExpense", "Error loading planned expense", e)
                    }
                }

                // ПОТОМ загружаем из локальной БД
                tagDao.getAllTags(userId).collect { tags ->
                    _uiState.value = _uiState.value.copy(availableTags = tags)
                }
            }
        }
    }

    fun updateCategory(value: ExpenseCategory) {
        _uiState.value = _uiState.value.copy(category = value, showError = false, categorySetManually = true)
    }

    fun updateAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(amount = value, showError = false)
        }
    }

    fun updateOdometer(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(odometer = value, showError = false)
        }
    }

    fun applySuggestedOdometer() {
        val suggested = _uiState.value.suggestedOdometer ?: return
        _uiState.value = _uiState.value.copy(odometer = suggested.toString(), suggestedOdometer = null)
    }

    fun updateDate(value: Long) {
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun updateDescription(value: String) {
        val current = _uiState.value
        val suggested = if (!current.categorySetManually) {
            ExpenseCategoryClassifier.classify(value)
        } else null
        _uiState.value = current.copy(
            description = value,
            category = suggested ?: current.category
        )
    }

    fun updateLocation(value: String) {
        _uiState.value = _uiState.value.copy(location = value)
    }

    fun updateFuelLiters(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(fuelLiters = value)
        }
    }

    fun updateIsFullTank(value: Boolean) {
        _uiState.value = _uiState.value.copy(isFullTank = value)
    }

    fun updateServiceType(value: ServiceType?) {
        _uiState.value = _uiState.value.copy(serviceType = value)
    }

    fun updateWorkshopName(value: String) {
        _uiState.value = _uiState.value.copy(workshopName = value)
    }

    fun updateMaintenanceParts(value: String) {
        _uiState.value = _uiState.value.copy(maintenanceParts = value)
    }

    fun updateReceiptPhoto(uri: Uri) {
        val expenseId = _uiState.value.expenseId
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isUploadingReceipt = true)
            }
            try {
                val inputStream = (getApplication() as android.app.Application).contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val maxSize = 1024
                val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height, 1f)
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                bitmap.recycle(); scaled.recycle()

                val bucket = supabase.storage.from("receipts")
                val fileName = "$expenseId.jpg"
                bucket.upload(path = fileName, data = out.toByteArray(), upsert = true)
                val url = bucket.publicUrl(fileName)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(receiptPhotoUri = url, isUploadingReceipt = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("AddExpense", "Receipt upload failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploadingReceipt = false)
                }
            }
        }
    }

    fun addTag(tag: ExpenseTag) {
        val currentTags = _uiState.value.selectedTags
        if (currentTags.none { it.id == tag.id }) {
            _uiState.value = _uiState.value.copy(selectedTags = currentTags + tag)
        }
    }

    fun removeTag(tag: ExpenseTag) {
        _uiState.value = _uiState.value.copy(
            selectedTags = _uiState.value.selectedTags.filter { it.id != tag.id }
        )
    }

    fun saveExpense(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Валидация
        if (state.amount.isBlank() || state.amount.toDoubleOrNull() == null) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите сумму")
            return
        }
        if (state.odometer.isBlank() || state.odometer.toIntOrNull() == null) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите пробег")
            return
        }

        _uiState.value = state.copy(isSaving = true)

        // Получаем геолокацию с таймаутом
        viewModelScope.launch {
            val location = getLocationWithTimeout()
            saveExpenseWithLocation(state, location, onSuccess)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getLocationWithTimeout(): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        return try {
            withTimeoutOrNull(3000L) {
                var result: Location? = null
                val cancellationToken = CancellationTokenSource()

                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellationToken.token
                    ).addOnSuccessListener { location ->
                        result = location
                    }.addOnFailureListener {
                        result = null
                    }

                    var attempts = 0
                    while (result == null && attempts < 30) {
                        delay(100)
                        attempts++
                    }

                    cancellationToken.cancel()
                } catch (e: SecurityException) {
                    null
                }

                result
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Проверяет и синхронизирует автомобиль с Supabase перед добавлением расхода
     */
    private suspend fun ensureCarSyncedToSupabase(carId: String): Boolean {
        return try {
            android.util.Log.d("AddExpense", "Checking if car $carId exists on server...")

            // Проверяем, есть ли автомобиль на сервере
            val remoteCarResult = supabaseCarRepo.getCarById(carId)

            if (remoteCarResult.isFailure) {
                android.util.Log.w("AddExpense", "Car not found on server, syncing...")

                // Если автомобиля нет на сервере - синхронизируем его
                val localCar = carRepository.getCarById(carId)
                if (localCar != null) {
                    val insertResult = supabaseCarRepo.insertCar(localCar)
                    insertResult.fold(
                        onSuccess = {
                            android.util.Log.d("AddExpense", "✅ Car synced to server successfully")
                            true
                        },
                        onFailure = { error ->
                            android.util.Log.e("AddExpense", "❌ Failed to sync car: ${error.message}")
                            false
                        }
                    )
                } else {
                    android.util.Log.e("AddExpense", "❌ Local car not found!")
                    false
                }
            } else {
                android.util.Log.d("AddExpense", "✅ Car already exists on server")
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("AddExpense", "Exception ensuring car synced", e)
            false
        }
    }

    private suspend fun saveExpenseWithLocation(
        state: AddExpenseUiState,
        location: Location?,
        onSuccess: () -> Unit
    ) {
        try {
            val expense = Expense(
                id = state.expenseId,
                carId = carId,
                category = state.category,
                amount = state.amount.toDouble(),
                currency = "RUB",
                date = state.date,
                odometer = state.odometer.toInt(),
                description = state.description.ifBlank { null },
                location = state.location.ifBlank { null },
                latitude = location?.latitude,
                longitude = location?.longitude,
                fuelLiters = if (state.category == ExpenseCategory.FUEL) {
                    state.fuelLiters.toDoubleOrNull()
                } else null,
                isFullTank = state.category == ExpenseCategory.FUEL && state.isFullTank,
                serviceType = if (state.category == ExpenseCategory.MAINTENANCE) {
                    state.serviceType
                } else null,
                workshopName = if (state.category == ExpenseCategory.MAINTENANCE ||
                    state.category == ExpenseCategory.REPAIR) {
                    state.workshopName.ifBlank { null }
                } else null,
                maintenanceParts = if (state.category == ExpenseCategory.MAINTENANCE) {
                    state.maintenanceParts.ifBlank { null }
                } else null,
                receiptPhotoUri = state.receiptPhotoUri
            )

            // 1. Сохраняем локально
            val expenseId = expenseRepository.insertExpense(expense)
            android.util.Log.d("AddExpense", "Expense saved locally with ID: $expenseId")

            // 2. Проверяем достижения (non-blocking, best-effort)
            viewModelScope.launch {
                try {
                    val userId = supabaseAuth.getUserId()
                    if (userId != null) {
                        AchievementChecker(database.achievementDao(), database.expenseDao())
                            .checkAfterExpenseAdded(userId, carId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddExpense", "Achievement check failed", e)
                }
            }

            // 3. Сохраняем связи с тегами
            state.selectedTags.forEach { tag ->
                tagDao.insertExpenseTagCrossRef(
                    ExpenseTagCrossRef(expenseId = expenseId, tagId = tag.id)
                )
            }

            // 3. Если это обслуживание - создаем/обновляем напоминание
            if (state.category == ExpenseCategory.MAINTENANCE && state.serviceType != null) {
                val reminderRepository = MaintenanceReminderRepository(
                    AppDatabase.getDatabase(getApplication()).maintenanceReminderDao()
                )

                val maintenanceType = convertServiceTypeToMaintenanceType(state.serviceType)
                if (maintenanceType != null) {
                    reminderRepository.updateAfterMaintenance(
                        carId = carId,
                        type = maintenanceType,
                        currentOdometer = state.odometer.toInt()
                    )
                }
            }

            // 4. Обновляем одометр машины
            val car = carRepository.getCarById(carId)
            car?.let {
                if (state.odometer.toInt() > it.currentOdometer) {
                    carRepository.updateOdometer(carId, state.odometer.toInt())
                }
            }

            // 5. ✅ СИНХРОНИЗИРУЕМ С SUPABASE
            try {
                val userId = supabaseAuth.getUserId()
                if (userId == null) {
                    android.util.Log.e("AddExpense", "User not authenticated!")
                } else {
                    // Try direct insert first — works for both owners and members (RLS allows any car member).
                    // If it fails (e.g. car was never synced to Supabase by the owner), fall back to
                    // syncing the car first, then retry.
                    val result = supabaseExpenseRepo.insertExpense(expense)
                    result.fold(
                        onSuccess = {
                            android.util.Log.d("AddExpense", "✅ Expense synced to Supabase: ${expense.id}")
                        },
                        onFailure = { error ->
                            android.util.Log.w("AddExpense", "Direct expense sync failed (${error.message}), trying to ensure car exists first")
                            val carSynced = ensureCarSyncedToSupabase(carId)
                            if (carSynced) {
                                supabaseExpenseRepo.insertExpense(expense).fold(
                                    onSuccess = { android.util.Log.d("AddExpense", "✅ Expense synced after car sync") },
                                    onFailure = { e -> android.util.Log.e("AddExpense", "❌ Expense sync failed after car sync: ${e.message}") }
                                )
                            } else {
                                android.util.Log.e("AddExpense", "❌ Car not synced, expense not uploaded to Supabase")
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AddExpense", "Exception syncing expense", e)
                // Не критично - продолжаем
            }

            // ✅ 6. СИНХРОНИЗИРУЕМ ТЕГИ С SUPABASE
            try {
                if (state.selectedTags.isNotEmpty()) {
                    val supabaseTagRepo = com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository(supabaseAuth)

                    // ВАЖНО: Сначала убеждаемся что САМ ТЕГ существует в Supabase
                    for (tag in state.selectedTags) {
                        try {
                            // Пытаемся вставить тег (если уже есть - ничего не произойдет)
                            val tagResult = supabaseTagRepo.insertTag(tag)
                            tagResult.fold(
                                onSuccess = {
                                    android.util.Log.d("AddExpense", "✅ Tag ensured in Supabase: ${tag.id}")
                                },
                                onFailure = { error ->
                                    // Возможно тег уже существует - это нормально
                                    android.util.Log.d("AddExpense", "Tag already exists or error: ${error.message}")
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("AddExpense", "Tag insert exception (probably exists): ${e.message}")
                        }
                    }

                    // Теперь безопасно создаем связи
                    val tagIds = state.selectedTags.map { it.id }
                    val result = supabaseTagRepo.setTagsForExpense(state.expenseId, tagIds)

                    result.fold(
                        onSuccess = {
                            android.util.Log.d("AddExpense", "✅ Tag links synced to Supabase: ${tagIds.size} tags")
                        },
                        onFailure = { error ->
                            android.util.Log.e("AddExpense", "❌ Failed to sync tag links", error)
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AddExpense", "Exception syncing tags", e)
            }

            // ✅ 7. СИНХРОНИЗИРУЕМ НАПОМИНАНИЕ С SUPABASE
            if (state.category == ExpenseCategory.MAINTENANCE && state.serviceType != null) {
                try {
                    val maintenanceType = convertServiceTypeToMaintenanceType(state.serviceType)
                    if (maintenanceType != null) {
                        val supabaseReminderRepo = com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository(supabaseAuth)
                        val reminderRepository = MaintenanceReminderRepository(
                            AppDatabase.getDatabase(getApplication()).maintenanceReminderDao()
                        )

                        // Получаем напоминание которое только что создали/обновили локально
                        val localReminder = reminderRepository.getReminderByType(carId, maintenanceType)

                        if (localReminder != null) {
                            // Синхронизируем с Supabase
                            val result = supabaseReminderRepo.insertReminder(localReminder)
                            result.fold(
                                onSuccess = {
                                    android.util.Log.d("AddExpense", "✅ Reminder synced to Supabase")
                                },
                                onFailure = { error ->
                                    android.util.Log.e("AddExpense", "❌ Failed to sync reminder", error)
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddExpense", "Exception syncing reminder", e)
                }
            }


            // ✅ Проверка бюджета — немедленное уведомление при превышении
            viewModelScope.launch {
                try {
                    val cal = java.util.Calendar.getInstance()
                    val month = cal.get(java.util.Calendar.MONTH) + 1
                    val year = cal.get(java.util.Calendar.YEAR)
                    val budget = database.categoryBudgetDao().getBudget(carId, state.category, month, year)
                    if (budget != null) {
                        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        val startOfMonth = cal.timeInMillis
                        val monthlyTotal = expenseRepository
                            .getExpensesInDateRange(carId, startOfMonth, System.currentTimeMillis())
                            .firstOrNull()
                            ?.filter { it.category == state.category }
                            ?.sumOf { it.amount } ?: 0.0
                        if (monthlyTotal > budget.monthlyLimit) {
                            val over = monthlyTotal - budget.monthlyLimit
                            val catName = NotificationHelper.categoryDisplayName(state.category.name)
                            NotificationHelper.sendGenericNotification(
                                context = getApplication(),
                                notificationId = state.category.ordinal + 3000,
                                title = "Превышен бюджет: $catName",
                                body = "Перерасход: +${"%.0f".format(over)} ₽ (лимит ${"%.0f".format(budget.monthlyLimit)} ₽)"
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddExpense", "Budget check failed", e)
                }
            }

            // ✅ НОВОЕ: Если расход создан из плана - обновить статус на COMPLETED
            if (state.plannedExpenseId != null) {
                try {
                    val plannedExpense = plannedExpenseRepository.getPlannedExpenseById(state.plannedExpenseId)
                    if (plannedExpense != null) {
                        val updatedPlanned = plannedExpense.copy(
                            status = PlannedExpenseStatus.COMPLETED,
                            completedDate = System.currentTimeMillis(),
                            linkedExpenseId = expenseId,
                            actualAmount = expense.amount,
                            updatedAt = System.currentTimeMillis(),
                            isSynced = false  // ✅ Помечаем что нужно синхронизировать
                        )
                        plannedExpenseRepository.updatePlannedExpense(updatedPlanned)
                        android.util.Log.d("AddExpense", "✅ Planned expense marked as COMPLETED locally")

                        // ✅ СИНХРОНИЗИРУЕМ С SUPABASE
                        try {
                            val userId = supabaseAuth.getUserId()
                            if (userId != null) {
                                val supabasePlannedRepo = com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository(supabaseAuth)
                                val result = supabasePlannedRepo.updatePlannedExpense(updatedPlanned)

                                result.fold(
                                    onSuccess = {
                                        android.util.Log.d("AddExpense", "✅ Planned expense synced to Supabase")
                                        // Помечаем как синхронизированный
                                        plannedExpenseRepository.updatePlannedExpense(
                                            updatedPlanned.copy(isSynced = true)
                                        )
                                    },
                                    onFailure = { error ->
                                        android.util.Log.e("AddExpense", "❌ Failed to sync planned expense", error)
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AddExpense", "Exception syncing planned expense", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddExpense", "Error updating planned expense status", e)
                }
            }
            // 8. Сбрасываем состояние
            _uiState.value = state.copy(isSaving = false)

            // 9. Вызываем onSuccess
            onSuccess()

        } catch (e: Exception) {
            android.util.Log.e("AddExpense", "Error saving expense", e)
            _uiState.value = state.copy(
                isSaving = false,
                showError = true,
                errorMessage = "Ошибка сохранения: ${e.message}"
            )
        }
    }

    private fun convertServiceTypeToMaintenanceType(serviceType: ServiceType): MaintenanceType? {
        return when (serviceType) {
            ServiceType.OIL_CHANGE -> MaintenanceType.OIL_CHANGE
            ServiceType.OIL_FILTER -> MaintenanceType.OIL_FILTER
            ServiceType.AIR_FILTER -> MaintenanceType.AIR_FILTER
            ServiceType.CABIN_FILTER -> MaintenanceType.CABIN_FILTER
            ServiceType.FUEL_FILTER -> MaintenanceType.FUEL_FILTER
            ServiceType.SPARK_PLUGS -> MaintenanceType.SPARK_PLUGS
            ServiceType.BRAKE_PADS -> MaintenanceType.BRAKE_PADS
            ServiceType.TIMING_BELT -> MaintenanceType.TIMING_BELT
            ServiceType.TRANSMISSION_FLUID -> MaintenanceType.TRANSMISSION_FLUID
            ServiceType.COOLANT -> MaintenanceType.COOLANT
            ServiceType.BRAKE_FLUID -> MaintenanceType.BRAKE_FLUID
            else -> null
        }
    }
}