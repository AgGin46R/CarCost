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
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseFilter(
    val categories: Set<ExpenseCategory> = emptySet(),
    val tags: Set<String> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    fun isActive(): Boolean {
        return categories.isNotEmpty() || tags.isNotEmpty() || startDate != null || endDate != null || minAmount != null || maxAmount != null
    }
}

data class CarDetailUiState(
    val car: Car? = null,
    val expenses: List<Expense> = emptyList(),
    val expensesWithTags: Map<String, List<ExpenseTag>> = emptyMap(),
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

    private val carId: String = savedStateHandle.get<String>("carId") ?: ""

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagRepository = ExpenseTagRepository(database.expenseTagDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)

    private val _currentFilter = MutableStateFlow(ExpenseFilter())

    private val _expensesWithTags = MutableStateFlow<Map<String, List<ExpenseTag>>>(emptyMap())
    private val _availableTags = MutableStateFlow<List<ExpenseTag>>(emptyList())

    val uiState: StateFlow<CarDetailUiState> = combine(
        carRepository.getCarByIdFlow(carId),
        expenseRepository.getExpensesByCarId(carId),
        _currentFilter,
        _expensesWithTags,
        _availableTags
    ) { car: Car?, expenses: List<Expense>, filter: ExpenseFilter, expensesWithTagsMap: Map<String, List<ExpenseTag>>, availableTags: List<ExpenseTag> ->

        val filteredExpenses = applyFilters(expenses, filter, expensesWithTagsMap)

        CarDetailUiState(
            car = car,
            expenses = filteredExpenses,
            expensesWithTags = expensesWithTagsMap,
            totalExpenses = filteredExpenses.sumOf { it.amount },
            monthlyExpenses = calculateMonthlyExpenses(filteredExpenses),
            expenseCount = filteredExpenses.size,
            currentFilter = filter,
            availableTags = availableTags,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CarDetailUiState(isLoading = true)
    )

    init {
        syncData()
        loadTags()
        loadAvailableTags()
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            val userId = supabaseAuth.getUserId()
            if (userId != null) {
                tagRepository.getTagsByUser(userId).collect { tags ->
                    _availableTags.value = tags
                }
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            // Загружаем теги для всех расходов
            expenseRepository.getExpensesByCarId(carId).collect { expenses ->
                val tagsMap = mutableMapOf<String, List<ExpenseTag>>()
                expenses.forEach { expense ->
                    try {
                        tagRepository.getTagsForExpense(expense.id).firstOrNull()?.let { tags ->
                            tagsMap[expense.id] = tags
                        }
                    } catch (e: Exception) {
                        Log.e("CarDetail", "Error loading tags for expense ${expense.id}", e)
                    }
                }
                _expensesWithTags.value = tagsMap
            }
        }
    }

    private fun syncData() {
        viewModelScope.launch {
            try {
                // Синхронизация расходов
                val result = supabaseExpenseRepo.getExpensesByCarId(carId)
                result.fold(
                    onSuccess = { expenses ->
                        expenses.forEach { expense ->
                            expenseRepository.insertExpense(expense)
                        }
                        Log.d("CarDetail", "Synced ${expenses.size} expenses")
                    },
                    onFailure = { error ->
                        Log.e("CarDetail", "Sync failed", error)
                    }
                )

                // Синхронизация напоминаний
                val reminderResult = supabaseReminderRepo.getRemindersByCarId(carId)
                reminderResult.fold(
                    onSuccess = { reminders ->
                        reminders.forEach { reminder ->
                            reminderRepository.insertReminder(reminder)
                        }
                    },
                    onFailure = { }
                )
            } catch (e: Exception) {
                Log.e("CarDetail", "Sync exception", e)
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expense)

                // Удаляем из Supabase
                supabaseExpenseRepo.deleteExpense(expense.id)

                Log.d("CarDetail", "Expense deleted: ${expense.id}")
            } catch (e: Exception) {
                Log.e("CarDetail", "Delete failed", e)
            }
        }
    }

    fun applyFilter(filter: ExpenseFilter) {
        _currentFilter.value = filter
    }

    fun clearFilter() {
        _currentFilter.value = ExpenseFilter()
    }

    private fun applyFilters(
        expenses: List<Expense>,
        filter: ExpenseFilter,
        tagsMap: Map<String, List<ExpenseTag>>
    ): List<Expense> {
        var filtered = expenses

        if (filter.categories.isNotEmpty()) {
            filtered = filtered.filter { it.category in filter.categories }
        }

        if (filter.tags.isNotEmpty()) {
            filtered = filtered.filter { expense ->
                // Проверяем есть ли хоть один из выбранных тегов у расхода
                val expenseTags = tagsMap[expense.id] ?: emptyList()
                expenseTags.any { it.id in filter.tags }
            }
        }

        filter.startDate?.let { start ->
            filtered = filtered.filter { it.date >= start }
        }

        filter.endDate?.let { end ->
            filtered = filtered.filter { it.date <= end }
        }

        filter.minAmount?.let { min ->
            filtered = filtered.filter { it.amount >= min }
        }

        filter.maxAmount?.let { max ->
            filtered = filtered.filter { it.amount <= max }
        }

        return filtered.sortedByDescending { it.date }
    }

    private fun calculateMonthlyExpenses(expenses: List<Expense>): Double {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        return expenses
            .filter { it.date >= thirtyDaysAgo }
            .sumOf { it.amount }
    }
}