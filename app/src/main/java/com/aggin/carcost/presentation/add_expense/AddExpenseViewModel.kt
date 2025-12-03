package com.aggin.carcost.presentation.screens.add_expense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.*
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class AddExpenseUiState(
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

    // Теги
    val availableTags: List<ExpenseTag> = emptyList(),
    val selectedTags: List<ExpenseTag> = emptyList(),

    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

class AddExpenseViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private val carId: Long = savedStateHandle.get<String>("carId")?.toLongOrNull() ?: 0L
    private val supabaseAuth = SupabaseAuthRepository()

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagDao = database.expenseTagDao()

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Загружаем текущий пробег автомобиля
            val car = carRepository.getCarById(carId)
            car?.let {
                _uiState.value = _uiState.value.copy(odometer = it.currentOdometer.toString())
            }

            // Загружаем доступные теги
            val userId = supabaseAuth.getUserId()
            if (userId != null) {
                tagDao.getAllTags(userId).collect { tags ->
                    _uiState.value = _uiState.value.copy(availableTags = tags)
                }
            }
        }
    }

    fun updateCategory(value: ExpenseCategory) {
        _uiState.value = _uiState.value.copy(category = value, showError = false)
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

    fun updateDate(value: Long) {
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
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

    // Новые функции для работы с тегами
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

    private suspend fun getLocationWithTimeout(): Location? {
        return try {
            // Пытаемся получить локацию с таймаутом 3 секунды
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

                    // Ждем результата с небольшими интервалами
                    var attempts = 0
                    while (result == null && attempts < 30) { // 30 * 100ms = 3 секунды
                        delay(100)
                        attempts++
                    }

                    cancellationToken.cancel()
                } catch (e: SecurityException) {
                    // Нет разрешения на геолокацию
                    null
                }

                result
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveExpenseWithLocation(
        state: AddExpenseUiState,
        location: Location?,
        onSuccess: () -> Unit
    ) {
        try {
            val expense = Expense(
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
                } else null
            )

            val expenseId = expenseRepository.insertExpense(expense)

            // Сохраняем связи с тегами
            state.selectedTags.forEach { tag ->
                tagDao.insertExpenseTagCrossRef(
                    ExpenseTagCrossRef(expenseId = expenseId, tagId = tag.id)
                )
            }

            // Если это обслуживание - создаем/обновляем напоминание
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

            val car = carRepository.getCarById(carId)
            car?.let {
                if (state.odometer.toInt() > it.currentOdometer) {
                    carRepository.updateOdometer(carId, state.odometer.toInt())
                }
            }

            onSuccess()
        } catch (e: Exception) {
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