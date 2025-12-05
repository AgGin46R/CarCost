package com.aggin.carcost.presentation.screens.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// Данные для круговой диаграммы
data class CategoryExpense(
    val category: ExpenseCategory,
    val amount: Double,
    val percentage: Float,
    val count: Int
)

// Данные для графика по месяцам
data class MonthlyExpense(
    val month: String,
    val year: Int,
    val amount: Double,
    val timestamp: Long
)

// Статистика по топливу
data class FuelStatistics(
    val averageConsumption: Double, // л/100км
    val totalLiters: Double,
    val totalCost: Double,
    val averagePricePerLiter: Double,
    val kmDriven: Int
)

// Прогноз расходов
data class ExpenseForecast(
    val nextMonthEstimate: Double,
    val nextYearEstimate: Double,
    val averageMonthly: Double,
    val trend: String // "increasing", "decreasing", "stable"
)

data class AnalyticsUiState(
    val car: Car? = null,
    val expenses: List<Expense> = emptyList(),
    val totalExpenses: Double = 0.0,
    val averageExpensePerMonth: Double = 0.0,
    val averageExpensePerDay: Double = 0.0,
    val averageExpensePerKm: Double = 0.0,
    val categoryExpenses: List<CategoryExpense> = emptyList(),
    val monthlyExpenses: List<MonthlyExpense> = emptyList(),
    val fuelStatistics: FuelStatistics? = null,
    val forecast: ExpenseForecast? = null,
    val currentMonthExpenses: Double = 0.0,
    val previousMonthExpenses: Double = 0.0,
    val monthComparison: Float = 0f,
    val isLoading: Boolean = true
)

class EnhancedAnalyticsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: "" // ✅ String UUID

    private val database = AppDatabase.getDatabase(application)
    private val carDao = database.carDao()
    private val expenseDao = database.expenseDao()

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val car = carDao.getCarById(carId)
            if (car == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            expenseDao.getExpensesByCarId(carId).collect { expenses ->
                _uiState.value = calculateAnalytics(car, expenses)
            }
        }
    }

    private fun calculateAnalytics(car: Car, expenses: List<Expense>): AnalyticsUiState {
        if (expenses.isEmpty()) {
            return AnalyticsUiState(car = car, expenses = expenses, isLoading = false)
        }

        val totalExpenses = expenses.sumOf { it.amount }
        val firstExpenseDate = expenses.minOfOrNull { it.date } ?: car.purchaseDate
        val daysSinceFirstExpense = ((System.currentTimeMillis() - firstExpenseDate) / (1000 * 60 * 60 * 24)).coerceAtLeast(1).toInt()
        val monthsSinceFirstExpense = daysSinceFirstExpense / 30.0

        val averagePerDay = totalExpenses / daysSinceFirstExpense
        val averagePerMonth = if (monthsSinceFirstExpense > 0) totalExpenses / monthsSinceFirstExpense else 0.0
        val kmDriven = car.currentOdometer - (car.purchaseOdometer ?: car.currentOdometer)
        val averagePerKm = if (kmDriven > 0) totalExpenses / kmDriven else 0.0

        val categoryExpenses = calculateCategoryExpenses(expenses, totalExpenses)
        val monthlyExpenses = calculateMonthlyExpenses(expenses)
        val fuelStatistics = calculateFuelStatistics(expenses, kmDriven)
        val forecast = calculateForecast(expenses, averagePerMonth)
        val (currentMonth, previousMonth, comparison) = compareMonths(expenses)

        return AnalyticsUiState(
            car = car,
            expenses = expenses,
            totalExpenses = totalExpenses,
            averageExpensePerMonth = averagePerMonth,
            averageExpensePerDay = averagePerDay,
            averageExpensePerKm = averagePerKm,
            categoryExpenses = categoryExpenses,
            monthlyExpenses = monthlyExpenses,
            fuelStatistics = fuelStatistics,
            forecast = forecast,
            currentMonthExpenses = currentMonth,
            previousMonthExpenses = previousMonth,
            monthComparison = comparison,
            isLoading = false
        )
    }

    private fun calculateCategoryExpenses(expenses: List<Expense>, total: Double): List<CategoryExpense> {
        if (total == 0.0) return emptyList()
        return expenses
            .groupBy { it.category }
            .map { (category, list) ->
                val amount = list.sumOf { it.amount }
                CategoryExpense(
                    category = category,
                    amount = amount,
                    percentage = ((amount / total) * 100).toFloat(),
                    count = list.size
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun calculateMonthlyExpenses(expenses: List<Expense>): List<MonthlyExpense> {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

        return expenses
            .groupBy { expense ->
                calendar.timeInMillis = expense.date
                Pair(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
            }
            .map { (yearMonthPair, list) ->
                val (year, monthNum) = yearMonthPair
                calendar.set(year, monthNum, 1)
                val monthName = monthFormat.format(calendar.time)

                MonthlyExpense(
                    month = monthName,
                    year = year,
                    amount = list.sumOf { it.amount },
                    timestamp = calendar.timeInMillis
                )
            }
            .sortedBy { it.timestamp }
    }

    private fun calculateFuelStatistics(expenses: List<Expense>, kmDriven: Int): FuelStatistics? {
        val fuelExpenses = expenses.filter { it.category == ExpenseCategory.FUEL }
        if (fuelExpenses.isEmpty() || kmDriven <= 0) return null

        val totalLiters = fuelExpenses.sumOf { it.fuelLiters ?: 0.0 }
        val totalCost = fuelExpenses.sumOf { it.amount }
        if (totalLiters <= 0) return null

        val averageConsumption = (totalLiters / kmDriven) * 100
        val averagePricePerLiter = totalCost / totalLiters

        return FuelStatistics(
            averageConsumption = averageConsumption,
            totalLiters = totalLiters,
            totalCost = totalCost,
            averagePricePerLiter = averagePricePerLiter,
            kmDriven = kmDriven
        )
    }

    private fun calculateForecast(expenses: List<Expense>, averagePerMonth: Double): ExpenseForecast {
        if (expenses.size < 3) {
            return ExpenseForecast(
                nextMonthEstimate = averagePerMonth,
                nextYearEstimate = averagePerMonth * 12,
                averageMonthly = averagePerMonth,
                trend = "stable"
            )
        }

        val calendar = Calendar.getInstance()
        val monthlyTotals = expenses
            .groupBy { expense ->
                calendar.timeInMillis = expense.date
                "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
            }
            .mapValues { it.value.sumOf { exp -> exp.amount } }
            .toList()
            .sortedBy {
                val parts = it.first.split("-")
                parts[0].toInt() * 12 + parts[1].toInt()
            }
            .map { it.second }

        val trend = if (monthlyTotals.size >= 2) {
            val recentAverage = monthlyTotals.takeLast(3).average()
            val olderAverage = monthlyTotals.dropLast(3).average().takeIf { !it.isNaN() } ?: recentAverage

            when {
                recentAverage > olderAverage * 1.1 -> "increasing"
                recentAverage < olderAverage * 0.9 -> "decreasing"
                else -> "stable"
            }
        } else "stable"

        val trendMultiplier = when (trend) {
            "increasing" -> 1.1
            "decreasing" -> 0.9
            else -> 1.0
        }

        val nextMonthEstimate = averagePerMonth * trendMultiplier

        return ExpenseForecast(
            nextMonthEstimate = nextMonthEstimate,
            nextYearEstimate = nextMonthEstimate * 12,
            averageMonthly = averagePerMonth,
            trend = trend
        )
    }

    private fun compareMonths(expenses: List<Expense>): Triple<Double, Double, Float> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val currentMonthExpenses = expenses.filter {
            calendar.timeInMillis = it.date
            calendar.get(Calendar.YEAR) == currentYear && calendar.get(Calendar.MONTH) == currentMonth
        }.sumOf { it.amount }

        calendar.add(Calendar.MONTH, -1)
        val previousYear = calendar.get(Calendar.YEAR)
        val previousMonth = calendar.get(Calendar.MONTH)

        val previousMonthExpenses = expenses.filter {
            calendar.timeInMillis = it.date
            calendar.get(Calendar.YEAR) == previousYear && calendar.get(Calendar.MONTH) == previousMonth
        }.sumOf { it.amount }

        val comparison = if (previousMonthExpenses > 0) {
            ((currentMonthExpenses - previousMonthExpenses) / previousMonthExpenses * 100).toFloat()
        } else if (currentMonthExpenses > 0) 100f else 0f

        return Triple(currentMonthExpenses, previousMonthExpenses, comparison)
    }
}