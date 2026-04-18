package com.aggin.carcost.presentation.screens.compare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ComparePeriod(val label: String, val months: Int) {
    THREE_MONTHS("3 мес", 3),
    SIX_MONTHS("6 мес", 6),
    YEAR("Год", 12),
    ALL("Всё время", 24)  // Show up to 2 years for "all time"
}

data class CarCompareStats(
    val car: Car,
    val totalExpenses: Double,
    val expenseCount: Int,
    val topCategories: List<Pair<ExpenseCategory, Double>>,
    val costPerKm: Double,
    val avgFuelConsumption: Double?,
    val totalLiters: Double,
    val maintenancePer10k: Double,
    val monthlyExpenses: List<Pair<String, Double>>  // month label → total amount
)

data class CompareUiState(
    val availableCars: List<Car> = emptyList(),
    val selectedCarIds: Set<String> = emptySet(),
    val carStats: Map<String, CarCompareStats> = emptyMap(),
    val selectedPeriod: ComparePeriod = ComparePeriod.SIX_MONTHS,
    val periodMonthLabels: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()
    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            carDao.getAllActiveCars().collect { cars ->
                _uiState.update { it.copy(availableCars = cars, isLoading = false) }
            }
        }
    }

    fun toggleCarSelection(carId: String) {
        val current = _uiState.value.selectedCarIds
        val updated = if (carId in current) current - carId else current + carId
        _uiState.update { it.copy(selectedCarIds = updated) }

        if (updated.size >= 2) {
            loadStatsForSelected(updated, _uiState.value.selectedPeriod)
        }
    }

    fun setPeriod(period: ComparePeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        val selected = _uiState.value.selectedCarIds
        if (selected.size >= 2) {
            loadStatsForSelected(selected, period)
        }
    }

    private fun buildPeriodMonths(period: ComparePeriod): List<Triple<Int, Int, String>> {
        // Returns list of (year, monthOf1-12, label) for the period, oldest → newest
        val result = mutableListOf<Triple<Int, Int, String>>()
        for (i in period.months - 1 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1  // 1-12
            val label = "${month.toString().padStart(2, '0')}.${year.toString().takeLast(2)}"
            result.add(Triple(year, month, label))
        }
        return result
    }

    private fun loadStatsForSelected(carIds: Set<String>, period: ComparePeriod) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Sync expenses from Supabase first
            for (carId in carIds) {
                supabaseExpenseRepo.getExpensesByCarId(carId).onSuccess { expenses ->
                    expenses.forEach { expense ->
                        try { expenseDao.insertExpense(expense) } catch (_: Exception) { }
                    }
                }
            }

            val periodMonths = buildPeriodMonths(period)
            val periodLabels = periodMonths.map { it.third }

            // Compute start timestamp for filtering
            val startCal = Calendar.getInstance()
            startCal.add(Calendar.MONTH, -period.months)
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            startCal.set(Calendar.HOUR_OF_DAY, 0)
            startCal.set(Calendar.MINUTE, 0)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)
            val startTimestamp = if (period == ComparePeriod.ALL) 0L else startCal.timeInMillis

            val allStats = mutableMapOf<String, CarCompareStats>()
            for (carId in carIds) {
                val car = carDao.getCarById(carId) ?: continue
                val allExpenses = expenseDao.getExpensesByCarId(carId).first()
                val filteredExpenses = if (startTimestamp == 0L) allExpenses
                else allExpenses.filter { it.date >= startTimestamp }
                allStats[carId] = buildStats(car, filteredExpenses, periodMonths)
            }
            _uiState.update { it.copy(carStats = allStats, periodMonthLabels = periodLabels, isLoading = false) }
        }
    }

    private fun buildStats(
        car: Car,
        expenses: List<Expense>,
        periodMonths: List<Triple<Int, Int, String>>
    ): CarCompareStats {
        val totalExpenses = expenses.sumOf { it.amount }
        val kmDriven = car.currentOdometer - (car.purchaseOdometer ?: car.currentOdometer)
        val costPerKm = if (kmDriven > 0) totalExpenses / kmDriven else 0.0

        val topCategories = expenses
            .groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(4)

        val fuelExpenses = expenses.filter {
            it.category == ExpenseCategory.FUEL && (it.fuelLiters ?: 0.0) > 0
        }.sortedBy { it.odometer }

        val totalLiters = fuelExpenses.sumOf { it.fuelLiters ?: 0.0 }
        val fuelKm = if (kmDriven > 0) kmDriven else {
            val minOdom = fuelExpenses.minOfOrNull { it.odometer } ?: 0
            val maxOdom = fuelExpenses.maxOfOrNull { it.odometer } ?: 0
            maxOdom - minOdom
        }
        val avgConsumption = if (fuelKm > 0 && totalLiters > 0) (totalLiters / fuelKm) * 100 else null

        // Maintenance per 10 000 km (MAINTENANCE + REPAIR categories)
        val maintTotal = expenses
            .filter { it.category == ExpenseCategory.MAINTENANCE || it.category == ExpenseCategory.REPAIR }
            .sumOf { it.amount }
        val maintenancePer10k = if (kmDriven >= 500) maintTotal / kmDriven * 10_000.0 else 0.0

        // Monthly breakdown aligned to period months
        val expensesByYearMonth = expenses.groupBy { expense ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = expense.date
            cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH) + 1
        }
        val monthlyExpenses = periodMonths.map { (year, month, label) ->
            val amount = expensesByYearMonth[year to month]?.sumOf { it.amount } ?: 0.0
            label to amount
        }

        return CarCompareStats(
            car = car,
            totalExpenses = totalExpenses,
            expenseCount = expenses.size,
            topCategories = topCategories,
            costPerKm = costPerKm,
            avgFuelConsumption = avgConsumption,
            totalLiters = totalLiters,
            maintenancePer10k = maintenancePer10k,
            monthlyExpenses = monthlyExpenses
        )
    }
}
