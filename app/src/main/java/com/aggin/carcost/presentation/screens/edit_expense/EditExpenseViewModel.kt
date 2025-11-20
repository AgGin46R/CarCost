package com.aggin.carcost.presentation.screens.edit_expense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.data.local.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditExpenseUiState(
    val expenseId: Long = 0L,
    val carId: Long = 0L,
    val category: ExpenseCategory = ExpenseCategory.FUEL,
    val amount: String = "",
    val odometer: String = "",
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val location: String = "",

    // Для топлива
    val fuelLiters: String = "",
    val isFullTank: Boolean = false,

    // Для обслуживания и ремонта
    val serviceType: ServiceType? = null,
    val workshopName: String = "",

    // Геолокация (сохраняем старые значения)
    val latitude: Double? = null,
    val longitude: Double? = null,

    val amountError: String? = null,
    val odometerError: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

class EditExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val expenseRepository = ExpenseRepository(database.expenseDao())

    private val _uiState = MutableStateFlow(EditExpenseUiState())
    val uiState: StateFlow<EditExpenseUiState> = _uiState.asStateFlow()

    fun loadExpense(carId: Long, expenseId: Long) {
        viewModelScope.launch {
            try {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense != null) {
                    _uiState.value = EditExpenseUiState(
                        expenseId = expense.id,
                        carId = expense.carId,
                        category = expense.category,
                        amount = expense.amount.toString(),
                        odometer = expense.odometer.toString(),
                        date = expense.date,
                        description = expense.description ?: "",
                        location = expense.location ?: "",
                        fuelLiters = expense.fuelLiters?.toString() ?: "",
                        isFullTank = expense.isFullTank,
                        serviceType = expense.serviceType,
                        workshopName = expense.workshopName ?: "",
                        latitude = expense.latitude,
                        longitude = expense.longitude,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Расход не найден",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка загрузки: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.value = _uiState.value.copy(category = category, errorMessage = null)
    }

    fun updateAmount(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(
                amount = amount,
                amountError = null,
                errorMessage = null
            )
        }
    }

    fun updateOdometer(odometer: String) {
        if (odometer.isEmpty() || odometer.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(
                odometer = odometer,
                odometerError = null,
                errorMessage = null
            )
        }
    }

    fun updateDate(date: Long) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(location = location)
    }

    fun updateFuelLiters(fuelLiters: String) {
        if (fuelLiters.isEmpty() || fuelLiters.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(fuelLiters = fuelLiters)
        }
    }

    fun updateIsFullTank(isFullTank: Boolean) {
        _uiState.value = _uiState.value.copy(isFullTank = isFullTank)
    }

    fun updateServiceType(serviceType: ServiceType?) {
        _uiState.value = _uiState.value.copy(serviceType = serviceType)
    }

    fun updateWorkshopName(workshopName: String) {
        _uiState.value = _uiState.value.copy(workshopName = workshopName)
    }

    fun saveExpense(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Валидация
        val amount = state.amount.toDoubleOrNull()
        val odometer = state.odometer.toIntOrNull()

        var hasError = false

        if (amount == null || amount <= 0) {
            _uiState.value = state.copy(
                amountError = "Введите корректную сумму",
                errorMessage = "Проверьте введенные данные"
            )
            hasError = true
        }

        if (odometer == null || odometer < 0) {
            _uiState.value = _uiState.value.copy(
                odometerError = "Введите корректный пробег",
                errorMessage = "Проверьте введенные данные"
            )
            hasError = true
        }

        if (hasError) return

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val updatedExpense = Expense(
                    id = state.expenseId,
                    carId = state.carId,
                    category = state.category,
                    amount = amount!!,
                    currency = "RUB",
                    date = state.date,
                    odometer = odometer!!,
                    description = state.description.ifBlank { null },
                    location = state.location.ifBlank { null },
                    latitude = state.latitude,
                    longitude = state.longitude,
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

                expenseRepository.updateExpense(updatedExpense)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка сохранения: ${e.message}",
                    isSaving = false
                )
            }
        }
    }
}