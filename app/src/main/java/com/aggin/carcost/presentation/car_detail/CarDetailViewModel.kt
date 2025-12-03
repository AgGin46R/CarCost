package com.aggin.carcost.presentation.screens.car_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data class для фильтра
data class ExpenseFilter(
    val categories: Set<com.aggin.carcost.data.local.database.entities.ExpenseCategory> = emptySet(),
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
    val expenses: List<Expense> = emptyList(), // Отфильтрованные расходы
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
    private val supabaseAuth = SupabaseAuthRepository()
    private val userId: String? = supabaseAuth.getUserId()

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagRepository = ExpenseTagRepository(database.expenseTagDao())

    private val _filter = MutableStateFlow(ExpenseFilter())

    // --- ЛОГИКА СВЕРНАННЫХ ПЕРЕПИСАНА ---
    val uiState: StateFlow<CarDetailUiState> = flow {
        // Сначала загружаем данные, которые не меняются (или меняются редко)
        val car = carRepository.getCarById(carId)
        val availableTags = if (userId != null) tagRepository.getTagsByUser(userId).first() else emptyList()

        // Теперь комбинируем потоки, которые меняются: все расходы и текущий фильтр
        combine(
            expenseRepository.getExpensesByCarId(carId),
            _filter
        ) { allExpenses, filter ->

            // Применяем фильтрацию
            val filteredExpenses = applyFilterLogic(allExpenses, filter)

            // Считаем статистику на основе отфильтрованных данных
            val total = filteredExpenses.sumOf { it.amount }
            val monthly = expenseRepository.calculateMonthlyExpenses(filteredExpenses) // Нужен новый метод
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
            // TODO: Добавить логику для фильтрации по тегам
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
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }
}