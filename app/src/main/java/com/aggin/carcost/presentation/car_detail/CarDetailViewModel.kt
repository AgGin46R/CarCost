package com.aggin.carcost.presentation.screens.car_detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data class для фильтра
data class ExpenseFilter(
    val categories: Set<ExpenseCategory> = emptySet(),
    val tags: Set<Long> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    fun isActive(): Boolean {
        return categories.isNotEmpty() || tags.isNotEmpty() || startDate != null || endDate != null || minAmount != null || maxAmount != null
    }

    fun getActiveFilterCount(): Int {
        var count = 0
        if (categories.isNotEmpty()) count++
        if (tags.isNotEmpty()) count++
        if (startDate != null) count++
        if (endDate != null) count++
        if (minAmount != null) count++
        if (maxAmount != null) count++
        return count
    }
}

data class CarDetailUiState(
    val car: Car? = null,
    val expenses: List<Expense> = emptyList(),
    val totalExpenses: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val expenseCount: Int = 0,
    val currentFilter: ExpenseFilter = ExpenseFilter(),
    val availableTags: List<ExpenseTag> = emptyList(),
    val isLoading: Boolean = true
)

class CarDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: Long = savedStateHandle.get<String>("carId")?.toLongOrNull() ?: 0L

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagRepository = ExpenseTagRepository(database.expenseTagDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)

    private val _filter = MutableStateFlow(ExpenseFilter())

    val uiState: StateFlow<CarDetailUiState> = flow {
        val car = carRepository.getCarById(carId)

        val userId = supabaseAuth.getUserId()
        val availableTags = if (userId != null) {
            tagRepository.getTagsByUser(userId).first()
        } else {
            emptyList()
        }

        combine(
            expenseRepository.getExpensesByCarId(carId),
            _filter
        ) { allExpenses, filter ->

            val filteredExpenses = applyFilterLogic(allExpenses, filter)

            val total = filteredExpenses.sumOf { it.amount }
            val monthly = expenseRepository.calculateMonthlyExpenses(filteredExpenses)
            val count = filteredExpenses.size

            CarDetailUiState(
                car = car,
                expenses = filteredExpenses,
                totalExpenses = total,
                monthlyExpenses = monthly,
                expenseCount = count,
                currentFilter = filter,
                availableTags = availableTags,
                isLoading = false
            )
        }.collect { state ->
            emit(state)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CarDetailUiState()
    )

    private fun applyFilterLogic(expenses: List<Expense>, filter: ExpenseFilter): List<Expense> {
        return expenses.filter { expense ->
            val categoryMatch = filter.categories.isEmpty() || expense.category in filter.categories
            val startDateMatch = filter.startDate == null || expense.date >= filter.startDate
            val endDateMatch = filter.endDate == null || expense.date <= filter.endDate
            val minAmountMatch = filter.minAmount == null || expense.amount >= filter.minAmount
            val maxAmountMatch = filter.maxAmount == null || expense.amount <= filter.maxAmount

            categoryMatch && startDateMatch && endDateMatch && minAmountMatch && maxAmountMatch
        }
    }

    fun applyFilter(newFilter: ExpenseFilter) {
        _filter.value = newFilter
    }

    fun clearFilter() {
        _filter.value = ExpenseFilter()
    }

    fun deleteExpense(expense: Expense) {
        Log.d("CarDetailVM", "🔴 deleteExpense called for expense ID: ${expense.id}")
        Log.d("CarDetailVM", "Category: ${expense.category}, ServiceType: ${expense.serviceType}")

        viewModelScope.launch {
            // 1. ✅ Если это расход ТО - удаляем связанное напоминание
            if (expense.category == ExpenseCategory.MAINTENANCE) {
                Log.d("CarDetailVM", "✅ Expense is MAINTENANCE category")

                if (expense.serviceType != null) {
                    Log.d("CarDetailVM", "✅ ServiceType is NOT null: ${expense.serviceType}")

                    try {
                        val maintenanceType = reminderRepository.serviceTypeToMaintenanceType(expense.serviceType)
                        Log.d("CarDetailVM", "Converted to MaintenanceType: $maintenanceType")

                        if (maintenanceType != null) {
                            Log.d("CarDetailVM", "Attempting to delete reminder: carId=${expense.carId}, type=$maintenanceType")

                            reminderRepository.deleteReminderByType(expense.carId, maintenanceType)

                            Log.d("CarDetailVM", "✅ Successfully deleted maintenance reminder for type: $maintenanceType")
                        } else {
                            Log.w("CarDetailVM", "⚠️ MaintenanceType is NULL - cannot delete reminder")
                        }
                    } catch (e: Exception) {
                        Log.e("CarDetailVM", "❌ Error deleting maintenance reminder", e)
                        e.printStackTrace()
                    }
                } else {
                    Log.w("CarDetailVM", "⚠️ ServiceType is NULL - skipping reminder deletion")
                }
            } else {
                Log.d("CarDetailVM", "ℹ️ Expense is NOT maintenance (${expense.category}) - skipping reminder deletion")
            }

            // 2. Удаляем расход локально
            try {
                expenseRepository.deleteExpense(expense)
                Log.d("CarDetailVM", "✅ Expense deleted locally: ${expense.id}")
            } catch (e: Exception) {
                Log.e("CarDetailVM", "❌ Error deleting expense locally", e)
            }

            // 3. Синхронизируем удаление с Supabase
            try {
                val result = supabaseExpenseRepo.deleteExpense(expense.id)
                result.fold(
                    onSuccess = {
                        Log.d("CarDetailVM", "✅ Expense deleted from Supabase: ${expense.id}")
                    },
                    onFailure = { error ->
                        Log.e("CarDetailVM", "❌ Failed to delete expense from Supabase: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("CarDetailVM", "❌ Exception deleting expense from Supabase", e)
            }
        }
    }
}