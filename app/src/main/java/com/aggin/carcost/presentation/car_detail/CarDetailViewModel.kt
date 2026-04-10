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
    val estimatedFuelLiters: Double? = null,
    val fuelLevelPct: Float? = null,
    val fuelConsumptionPerFill: Map<String, Double> = emptyMap(), // expenseId → L/100km
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

        val (estimatedFuel, fuelPct) = estimateFuelLevel(car, expenses)
        CarDetailUiState(
            car = car,
            expenses = filteredExpenses,
            expensesWithTags = expensesWithTagsMap,
            totalExpenses = filteredExpenses.sumOf { it.amount },
            monthlyExpenses = calculateMonthlyExpenses(filteredExpenses),
            expenseCount = filteredExpenses.size,
            currentFilter = filter,
            availableTags = availableTags,
            estimatedFuelLiters = estimatedFuel,
            fuelLevelPct = fuelPct,
            fuelConsumptionPerFill = calculateFuelConsumptionPerFill(expenses),
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

                // Для каждого расхода получаем теги синхронно
                expenses.forEach { expense ->
                    try {
                        // Получаем текущее значение из Flow
                        tagRepository.getTagsForExpense(expense.id).firstOrNull()?.let { tags ->
                            tagsMap[expense.id] = tags
                        }
                    } catch (e: Exception) {
                        Log.e("CarDetail", "Error loading tags for expense ${expense.id}", e)
                    }
                }

                // Обновляем состояние один раз для всех расходов
                _expensesWithTags.value = tagsMap
            }
        }
    }

    private fun syncData() {
        viewModelScope.launch {
            try {
                // 1. Синхронизация расходов
                val result = supabaseExpenseRepo.getExpensesByCarId(carId)
                result.fold(
                    onSuccess = { expenses ->
                        expenses.forEach { expense ->
                            // Only overwrite if the remote record is newer than the local one.
                            // This prevents syncData() from reverting local edits when the
                            // Supabase update failed (logged silently) and the remote still has
                            // the old version.
                            val local = expenseRepository.getExpenseById(expense.id)
                            if (local == null || expense.updatedAt >= local.updatedAt) {
                                expenseRepository.insertExpense(expense)
                            }
                        }
                        Log.d("CarDetail", "Synced ${expenses.size} expenses")

                        // ✅ 2. Синхронизируем теги для каждого расхода
                        val supabaseTagRepo = com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository(supabaseAuth)
                        expenses.forEach { expense ->
                            viewModelScope.launch {
                                try {
                                    val tagsResult = supabaseTagRepo.getTagsForExpense(expense.id)
                                    tagsResult.fold(
                                        onSuccess = { remoteTags ->
                                            if (remoteTags.isNotEmpty()) {
                                                // Сохраняем теги локально
                                                remoteTags.forEach { tag ->
                                                    database.expenseTagDao().insertTag(tag)
                                                }

                                                // Удаляем старые связи
                                                database.expenseTagDao().deleteExpenseTagsByExpenseId(expense.id)

                                                // Создаём новые связи
                                                remoteTags.forEach { tag ->
                                                    database.expenseTagDao().insertExpenseTagCrossRef(
                                                        com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef(
                                                            expenseId = expense.id,
                                                            tagId = tag.id
                                                        )
                                                    )
                                                }
                                                Log.d("CarDetail", "✅ Synced ${remoteTags.size} tags for expense ${expense.id}")
                                            }
                                        },
                                        onFailure = { error ->
                                            Log.e("CarDetail", "Failed to sync tags for expense ${expense.id}", error)
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.e("CarDetail", "Exception syncing tags for expense ${expense.id}", e)
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.e("CarDetail", "Sync failed", error)
                    }
                )

                // 3. Синхронизация напоминаний
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

    private fun estimateFuelLevel(car: Car?, expenses: List<Expense>): Pair<Double?, Float?> {
        val tankCapacity = car?.tankCapacity ?: return null to null
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL }
            .sortedByDescending { it.date }
        if (fuelExpenses.isEmpty()) return null to null

        val lastFull = fuelExpenses.firstOrNull { it.isFullTank } ?: return null to null
        val litersAtLastFull = lastFull.fuelLiters ?: return null to null

        val partialAfter = fuelExpenses
            .filter { it.date > lastFull.date && !it.isFullTank }
            .sumOf { it.fuelLiters ?: 0.0 }

        val kmSinceFull = (car.currentOdometer - lastFull.odometer).coerceAtLeast(0)
        val avgConsumption = calcAvgConsumption(fuelExpenses) ?: 10.0

        val consumed = kmSinceFull * avgConsumption / 100.0
        val remaining = (tankCapacity + partialAfter - consumed).coerceIn(0.0, tankCapacity)
        val pct = (remaining / tankCapacity).toFloat()

        return remaining to pct
    }

    private fun calcAvgConsumption(fuelExpenses: List<Expense>): Double? {
        val fullTanks = fuelExpenses.filter { it.isFullTank && it.fuelLiters != null }.sortedBy { it.date }
        if (fullTanks.size < 2) return null
        var totalL = 0.0; var totalKm = 0
        for (i in 1 until fullTanks.size) {
            val km = fullTanks[i].odometer - fullTanks[i - 1].odometer
            val l = fullTanks[i].fuelLiters!!
            if (km > 0 && l / km * 100 in 2.0..30.0) { totalL += l; totalKm += km }
        }
        return if (totalKm > 0) totalL * 100.0 / totalKm else null
    }

    private fun calculateFuelConsumptionPerFill(expenses: List<Expense>): Map<String, Double> {
        val fullTanks = expenses
            .filter { it.category == ExpenseCategory.FUEL && it.isFullTank && it.fuelLiters != null && it.odometer > 0 }
            .sortedBy { it.date }
        if (fullTanks.size < 2) return emptyMap()
        val result = mutableMapOf<String, Double>()
        for (i in 1 until fullTanks.size) {
            val km = fullTanks[i].odometer - fullTanks[i - 1].odometer
            val liters = fullTanks[i].fuelLiters!!
            if (km > 0) {
                val consumption = liters / km * 100.0
                if (consumption in 3.0..40.0) {
                    result[fullTanks[i].id] = consumption
                }
            }
        }
        return result
    }
}