package com.aggin.carcost.presentation.screens.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.GpsTrip
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
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
    val kmDriven: Int,
    val consumptionHistory: List<Pair<String, Double>> = emptyList() // дата → л/100км
)

// Прогноз расходов
data class ExpenseForecast(
    val nextMonthEstimate: Double,
    val nextYearEstimate: Double,
    val averageMonthly: Double,
    val trend: String // "increasing", "decreasing", "stable"
)

// Топ месяцев по расходам
data class TopMonth(
    val label: String,   // "Янв 2025"
    val amount: Double,
    val rank: Int
)

// Сравнение текущего и прошлого года
data class YearComparison(
    val currentYear: Int,
    val currentYearTotal: Double,
    val previousYear: Int,
    val previousYearTotal: Double,
    val changePercent: Float
)

// Тренд по категории: изменение за последние 3 месяца
data class CategoryTrend(
    val category: ExpenseCategory,
    val recentAmount: Double,    // последние 3 месяца
    val previousAmount: Double,  // 3 месяца до этого
    val changePercent: Float     // положительный = рост, отрицательный = снижение
)

// GPS-статистика поездок
data class GpsTripStats(
    val totalTrips: Int,
    val totalDistanceKm: Double,
    val avgTripDistanceKm: Double,
    val avgSpeedKmh: Double?,
    val longestTripKm: Double
)

data class OdometerPoint(
    val label: String,   // "Янв 25"
    val odometer: Int
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
    val topMonths: List<TopMonth> = emptyList(),
    val yearComparison: YearComparison? = null,
    val categoryTrends: List<CategoryTrend> = emptyList(),
    val gpsTripStats: GpsTripStats? = null,
    val odometerHistory: List<OdometerPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val anomalies: List<ExpenseAnomaly> = emptyList()
)

/**
 * An anomalous expense spike or category increase detected by the engine.
 * [changePercent] is > 0 for increase, < 0 for decrease vs baseline.
 */
data class ExpenseAnomaly(
    val category: ExpenseCategory,
    val changePercent: Float,       // e.g. +43 means +43%
    val currentMonthAmount: Double,
    val baselineAmount: Double,     // average of previous N months
    val message: String             // human-readable explanation
)

class EnhancedAnalyticsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: ""

    private val database = AppDatabase.getDatabase(application)
    private val carDao = database.carDao()
    private val expenseDao = database.expenseDao()
    private val gpsTripDao = database.gpsTripDao()

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Re-trigger by reloading (Flow will emit fresh data)
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val car = carDao.getCarById(carId)
            if (car == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            combine(
                expenseDao.getExpensesByCarId(carId),
                gpsTripDao.getTripsByCarId(carId)
            ) { expenses, trips -> expenses to trips }
                .debounce(300.milliseconds)
                .distinctUntilChanged()
                .map { (expenses, trips) -> calculateAnalytics(car, expenses, trips) }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun calculateAnalytics(
        car: Car,
        expenses: List<Expense>,
        trips: List<GpsTrip>
    ): AnalyticsUiState {
        // GPS stats (always available even without expenses)
        val gpsTripStats = if (trips.isNotEmpty()) calculateGpsTripStats(trips) else null

        if (expenses.isEmpty()) {
            return AnalyticsUiState(car = car, expenses = expenses, gpsTripStats = gpsTripStats, isLoading = false)
        }

        val totalExpenses = expenses.sumOf { it.amount }
        val firstExpenseDate = expenses.minOfOrNull { it.date } ?: car.purchaseDate
        val daysSinceFirst = ((System.currentTimeMillis() - firstExpenseDate) / (1000 * 60 * 60 * 24)).coerceAtLeast(1).toInt()
        val monthsSinceFirst = daysSinceFirst / 30.0

        val averagePerDay = totalExpenses / daysSinceFirst
        val averagePerMonth = if (monthsSinceFirst > 0) totalExpenses / monthsSinceFirst else 0.0

        // км: приоритет одометру; если нет — GPS-суммарный пробег
        val odometerKm = car.currentOdometer - (car.purchaseOdometer ?: car.currentOdometer)
        val gpsKm = gpsTripStats?.totalDistanceKm?.toInt() ?: 0
        val kmDriven = when {
            odometerKm > 0 -> odometerKm
            gpsKm > 0 -> gpsKm
            else -> 0
        }
        val averagePerKm = if (kmDriven > 0) totalExpenses / kmDriven else 0.0

        val categoryExpenses = calculateCategoryExpenses(expenses, totalExpenses)
        val monthlyExpenses = calculateMonthlyExpenses(expenses)
        val fuelStatistics = calculateFuelStatistics(expenses, kmDriven)
        val forecast = calculateForecast(expenses, averagePerMonth)
        val (currentMonth, previousMonth, comparison) = compareMonths(expenses)
        val topMonths = calculateTopMonths(monthlyExpenses)
        val yearComparison = calculateYearComparison(expenses)
        val categoryTrends = calculateCategoryTrends(expenses)
        val odometerHistory = calculateOdometerHistory(expenses)
        val anomalies = detectAnomalies(expenses)

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
            topMonths = topMonths,
            yearComparison = yearComparison,
            categoryTrends = categoryTrends,
            gpsTripStats = gpsTripStats,
            odometerHistory = odometerHistory,
            isLoading = false,
            anomalies = anomalies
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
                MonthlyExpense(
                    month = monthFormat.format(calendar.time),
                    year = year,
                    amount = list.sumOf { it.amount },
                    timestamp = calendar.timeInMillis
                )
            }
            .sortedBy { it.timestamp }
    }

    private fun calculateFuelStatistics(expenses: List<Expense>, kmDriven: Int): FuelStatistics? {
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL && (it.fuelLiters ?: 0.0) > 0 }
            .sortedBy { it.odometer }
        if (fuelExpenses.isEmpty()) return null

        val effectiveKmDriven = if (kmDriven > 0) kmDriven else {
            val minOdom = fuelExpenses.minOfOrNull { it.odometer } ?: 0
            val maxOdom = fuelExpenses.maxOfOrNull { it.odometer } ?: 0
            maxOdom - minOdom
        }
        if (effectiveKmDriven <= 0) return null

        val totalLiters = fuelExpenses.sumOf { it.fuelLiters ?: 0.0 }
        val totalCost = fuelExpenses.sumOf { it.amount }
        if (totalLiters <= 0) return null

        val averageConsumption = (totalLiters / effectiveKmDriven) * 100
        val averagePricePerLiter = totalCost / totalLiters

        val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
        val consumptionHistory = mutableListOf<Pair<String, Double>>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val km = curr.odometer - prev.odometer
            val liters = curr.fuelLiters ?: 0.0
            if (km > 0 && liters > 0) {
                val consumption = (liters / km) * 100
                if (consumption in 1.0..35.0) {
                    consumptionHistory.add(dateFormat.format(Date(curr.date)) to consumption)
                }
            }
        }

        return FuelStatistics(
            averageConsumption = averageConsumption,
            totalLiters = totalLiters,
            totalCost = totalCost,
            averagePricePerLiter = averagePricePerLiter,
            kmDriven = effectiveKmDriven,
            consumptionHistory = consumptionHistory
        )
    }

    /**
     * Detects per-category anomalies: current month's spending vs 3-month average.
     * Returns categories where the change exceeds ±30% and the absolute value is notable.
     */
    private fun detectAnomalies(expenses: List<Expense>): List<ExpenseAnomaly> {
        if (expenses.size < 5) return emptyList()
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()

        // Bucket by "YYYY-MM" per category
        data class MonthKey(val year: Int, val month: Int)
        val byCategory = expenses.groupBy { it.category }

        val anomalies = mutableListOf<ExpenseAnomaly>()
        val curYear = now.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH)

        for ((category, catExpenses) in byCategory) {
            val byMonth = catExpenses.groupBy { exp ->
                calendar.timeInMillis = exp.date
                MonthKey(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
            }.mapValues { it.value.sumOf { e -> e.amount } }

            val currentAmount = byMonth[MonthKey(curYear, curMonth)] ?: continue
            // Baseline: average of previous 3 months
            val prevMonths = (1..3).mapNotNull { offset ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, curYear)
                    set(Calendar.MONTH, curMonth - offset)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val key = MonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                byMonth[key]
            }
            if (prevMonths.isEmpty()) continue
            val baseline = prevMonths.average()
            if (baseline < 100.0) continue // ignore tiny amounts

            val changePct = ((currentAmount - baseline) / baseline * 100).toFloat()
            if (kotlin.math.abs(changePct) < 30f) continue

            val direction = if (changePct > 0) "вырос" else "снизился"
            val pctFormatted = "%.0f".format(kotlin.math.abs(changePct))
            val categoryName = when (category) {
                ExpenseCategory.FUEL -> "Топливо"
                ExpenseCategory.MAINTENANCE -> "Обслуживание"
                ExpenseCategory.REPAIR -> "Ремонт"
                ExpenseCategory.INSURANCE -> "Страховка"
                ExpenseCategory.TAX -> "Налоги"
                ExpenseCategory.PARKING -> "Парковка"
                ExpenseCategory.TOLL -> "Платная дорога"
                ExpenseCategory.WASH -> "Мойка"
                ExpenseCategory.FINE -> "Штраф"
                ExpenseCategory.ACCESSORIES -> "Аксессуары"
                else -> "Прочее"
            }
            anomalies.add(
                ExpenseAnomaly(
                    category = category,
                    changePercent = changePct,
                    currentMonthAmount = currentAmount,
                    baselineAmount = baseline,
                    message = "$categoryName $direction на $pctFormatted% по сравнению со средним за 3 мес."
                )
            )
        }

        // Sort by absolute change magnitude descending
        return anomalies.sortedByDescending { kotlin.math.abs(it.changePercent) }
    }

    private fun calculateForecast(expenses: List<Expense>, averagePerMonth: Double): ExpenseForecast {
        val calendar = Calendar.getInstance()
        val monthlyTotals = expenses
            .groupBy { expense ->
                calendar.timeInMillis = expense.date
                "${calendar.get(Calendar.YEAR)}-${"%02d".format(calendar.get(Calendar.MONTH))}"
            }
            .mapValues { it.value.sumOf { exp -> exp.amount } }
            .toSortedMap()
            .values.toList()

        if (monthlyTotals.size < 2) {
            return ExpenseForecast(
                nextMonthEstimate = averagePerMonth,
                nextYearEstimate = averagePerMonth * 12,
                averageMonthly = averagePerMonth,
                trend = "stable"
            )
        }

        val recentMonths = monthlyTotals.takeLast(3)
        val olderMonths = monthlyTotals.dropLast(3)

        val recentAvg = recentMonths.average()
        val olderAvg = if (olderMonths.isNotEmpty()) olderMonths.average() else recentAvg

        val trend = when {
            recentAvg > olderAvg * 1.1 -> "increasing"
            recentAvg < olderAvg * 0.9 -> "decreasing"
            else -> "stable"
        }

        // Взвешенный прогноз: 70% от последних 3 месяцев + 30% общий средний
        val weightedEstimate = recentAvg * 0.7 + averagePerMonth * 0.3

        return ExpenseForecast(
            nextMonthEstimate = weightedEstimate,
            nextYearEstimate = weightedEstimate * 12,
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

        calendar.set(currentYear, currentMonth, 1)
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

    private fun calculateTopMonths(monthlyExpenses: List<MonthlyExpense>): List<TopMonth> {
        val monthYearFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return monthlyExpenses
            .sortedByDescending { it.amount }
            .take(3)
            .mapIndexed { index, me ->
                val cal = Calendar.getInstance().apply { timeInMillis = me.timestamp }
                TopMonth(
                    label = monthYearFmt.format(cal.time),
                    amount = me.amount,
                    rank = index + 1
                )
            }
    }

    private fun calculateYearComparison(expenses: List<Expense>): YearComparison? {
        val calendar = Calendar.getInstance()
        val thisYear = calendar.get(Calendar.YEAR)
        val lastYear = thisYear - 1

        val thisYearTotal = expenses.filter {
            calendar.timeInMillis = it.date
            calendar.get(Calendar.YEAR) == thisYear
        }.sumOf { it.amount }

        val lastYearTotal = expenses.filter {
            calendar.timeInMillis = it.date
            calendar.get(Calendar.YEAR) == lastYear
        }.sumOf { it.amount }

        if (thisYearTotal == 0.0 && lastYearTotal == 0.0) return null

        val change = if (lastYearTotal > 0) {
            ((thisYearTotal - lastYearTotal) / lastYearTotal * 100).toFloat()
        } else if (thisYearTotal > 0) 100f else 0f

        return YearComparison(
            currentYear = thisYear,
            currentYearTotal = thisYearTotal,
            previousYear = lastYear,
            previousYearTotal = lastYearTotal,
            changePercent = change
        )
    }

    private fun calculateCategoryTrends(expenses: List<Expense>): List<CategoryTrend> {
        val now = System.currentTimeMillis()
        val threeMonthsAgo = now - 90L * 24 * 60 * 60 * 1000
        val sixMonthsAgo = now - 180L * 24 * 60 * 60 * 1000

        val recent = expenses.filter { it.date in threeMonthsAgo..now }
        val previous = expenses.filter { it.date in sixMonthsAgo until threeMonthsAgo }

        if (recent.isEmpty()) return emptyList()

        val recentByCategory = recent.groupBy { it.category }
            .mapValues { it.value.sumOf { e -> e.amount } }
        val prevByCategory = previous.groupBy { it.category }
            .mapValues { it.value.sumOf { e -> e.amount } }

        return recentByCategory.entries
            .map { (cat, recentAmt) ->
                val prevAmt = prevByCategory[cat] ?: 0.0
                val change = if (prevAmt > 0) {
                    ((recentAmt - prevAmt) / prevAmt * 100).toFloat()
                } else if (recentAmt > 0) 100f else 0f
                CategoryTrend(
                    category = cat,
                    recentAmount = recentAmt,
                    previousAmount = prevAmt,
                    changePercent = change
                )
            }
            .filter { abs(it.changePercent) >= 5f || it.previousAmount == 0.0 }
            .sortedByDescending { abs(it.changePercent) }
            .take(5)
    }

    private fun calculateOdometerHistory(expenses: List<Expense>): List<OdometerPoint> {
        val fmt = SimpleDateFormat("MMM yy", Locale("ru"))
        return expenses
            .filter { it.odometer > 0 }
            .sortedBy { it.date }
            .map { OdometerPoint(label = fmt.format(Date(it.date)), odometer = it.odometer) }
            .distinctBy { it.label } // one point per month-year label
            .takeLast(12)
    }

    private fun calculateGpsTripStats(trips: List<GpsTrip>): GpsTripStats {
        val completed = trips.filter { it.endTime != null }
        val totalDist = trips.sumOf { it.distanceKm }
        val avgDist = if (trips.isNotEmpty()) totalDist / trips.size else 0.0
        val speeds = completed.mapNotNull { it.avgSpeedKmh }
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else null
        val longest = trips.maxOfOrNull { it.distanceKm } ?: 0.0

        return GpsTripStats(
            totalTrips = trips.size,
            totalDistanceKm = totalDist,
            avgTripDistanceKm = avgDist,
            avgSpeedKmh = avgSpeed,
            longestTripKm = longest
        )
    }
}
