package com.aggin.carcost.presentation.screens.analytics

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlin.math.abs

// --- ФАБРИКА ДЛЯ СОЗДАНИЯ VIEWMODEL С ПАРАМЕТРАМИ ---
class AnalyticsViewModelFactory(
    private val application: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EnhancedAnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EnhancedAnalyticsViewModel(
                application,
                androidx.lifecycle.SavedStateHandle(mapOf("carId" to carId))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAnalyticsScreen(
    navController: NavController,
    carId: String
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: EnhancedAnalyticsViewModel = viewModel(
        factory = AnalyticsViewModelFactory(application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аналитика") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Pre-fill with avg consumption if available
                        val avgL100 = uiState.fuelStatistics?.averageConsumption ?: 0.0
                        navController.navigate(
                            com.aggin.carcost.presentation.navigation.Screen.FuelCalculator
                                .createRoute(avgL100 = avgL100)
                        )
                    }) {
                        Icon(Icons.Default.Calculate, "Калькулятор топлива")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.padding(paddingValues)
        ) {
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                com.aggin.carcost.presentation.components.SkeletonCardList(count = 4, cardHeight = 160.dp)
            }
        } else if (uiState.expenses.isEmpty() && uiState.gpsTripStats == null) {
            EmptyAnalyticsState(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CarSummaryCard(uiState) }

                if (uiState.gpsTripStats != null) {
                    item { GpsTripStatsCard(uiState.gpsTripStats!!) }
                }

                if (uiState.expenses.isNotEmpty()) {
                    item { MonthComparisonCard(uiState) }
                    item { MainStatisticsCard(uiState) }

                    uiState.yearComparison?.let { yc ->
                        item { YearComparisonCard(yc) }
                    }

                    if (uiState.topMonths.isNotEmpty()) {
                        item { TopExpenseMonthsCard(uiState.topMonths) }
                    }

                    if (uiState.anomalies.isNotEmpty()) {
                        item { AnomalyCard(uiState.anomalies) }
                    }

                    if (uiState.categoryExpenses.isNotEmpty()) {
                        item { PieChartCard(uiState.categoryExpenses) }
                    }

                    if (uiState.categoryTrends.isNotEmpty()) {
                        item { CategoryTrendsCard(uiState.categoryTrends) }
                    }

                    if (uiState.monthlyExpenses.isNotEmpty()) {
                        item { MonthlyChartCard(uiState.monthlyExpenses) }
                    }

                    if (uiState.odometerHistory.size >= 2) {
                        item { OdometerChartCard(uiState.odometerHistory) }
                    }

                    uiState.fuelStatistics?.let { fs ->
                        item { FuelStatisticsCard(fs) }
                    }

                    uiState.forecast?.let { forecast ->
                        item { ForecastCard(forecast) }
                    }
                }
            }
        } // end else
        } // end PullToRefreshBox
    } // end Scaffold
} // end EnhancedAnalyticsScreen

// ---------------------------------------------------------------------------
// GPS Trip Stats Card
// ---------------------------------------------------------------------------

@Composable
fun GpsTripStatsCard(stats: GpsTripStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "GPS-поездки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                GpsStat(
                    icon = Icons.Default.Route,
                    label = "Всего поездок",
                    value = "${stats.totalTrips}"
                )
                GpsStat(
                    icon = Icons.Default.Straighten,
                    label = "Общий пробег",
                    value = "%.1f км".format(stats.totalDistanceKm)
                )
                GpsStat(
                    icon = Icons.Default.Timeline,
                    label = "Средняя",
                    value = "%.1f км".format(stats.avgTripDistanceKm)
                )
                if (stats.avgSpeedKmh != null) {
                    GpsStat(
                        icon = Icons.Default.Speed,
                        label = "Ср. скорость",
                        value = "%.0f км/ч".format(stats.avgSpeedKmh)
                    )
                }
            }
            if (stats.longestTripKm > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🏆 Самая длинная поездка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "%.1f км".format(stats.longestTripKm),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
    }
}

// ---------------------------------------------------------------------------
// Year Comparison Card
// ---------------------------------------------------------------------------

@Composable
fun YearComparisonCard(yc: YearComparison) {
    val isUp = yc.changePercent > 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Год к году", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                YearBlock(year = "${yc.currentYear}", amount = yc.currentYearTotal, primary = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        null,
                        tint = if (isUp) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "${if (isUp) "+" else ""}${"%.1f".format(yc.changePercent)}%",
                        fontWeight = FontWeight.Bold,
                        color = if (isUp) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                        fontSize = 13.sp
                    )
                }
                YearBlock(year = "${yc.previousYear}", amount = yc.previousYearTotal, primary = false)
            }
        }
    }
}

@Composable
private fun YearBlock(year: String, amount: Double, primary: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            year,
            style = MaterialTheme.typography.labelSmall,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "%.0f ₽".format(amount),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// Top 3 Expensive Months
// ---------------------------------------------------------------------------

@Composable
fun TopExpenseMonthsCard(topMonths: List<TopMonth>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFC107))
                Spacer(Modifier.width(8.dp))
                Text("Топ месяцев по расходам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            topMonths.forEachIndexed { index, tm ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val medalColor = when (index) {
                        0 -> Color(0xFFFFC107) // gold
                        1 -> Color(0xFFB0BEC5) // silver
                        else -> Color(0xFFCD7F32) // bronze
                    }
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(medalColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${tm.rank}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                    Text(tm.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("%.0f ₽".format(tm.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                if (index < topMonths.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category Trends Card
// ---------------------------------------------------------------------------

@Composable
fun CategoryTrendsCard(trends: List<CategoryTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Тренды по категориям", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "Изменения за последние 3 месяца",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            trends.forEach { trend ->
                CategoryTrendRow(trend)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryTrendRow(trend: CategoryTrend) {
    val isNew = trend.previousAmount == 0.0
    val isUp = trend.changePercent > 0
    val color = if (isNew) MaterialTheme.colorScheme.primary
    else if (isUp) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(getCategoryColor(trend.category))
        )
        Text(
            getCategoryName(trend.category),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "%.0f ₽".format(trend.recentAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(70.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                if (isNew) Icons.Default.FiberNew
                else if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                if (isNew) "новое"
                else "${if (isUp) "+" else ""}${"%.0f".format(trend.changePercent)}%",
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Existing cards (preserved + minor improvements)
// ---------------------------------------------------------------------------

@Composable
fun CarSummaryCard(uiState: AnalyticsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${uiState.car?.brand} ${uiState.car?.model}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${uiState.car?.year} • ${uiState.car?.licensePlate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (uiState.expenses.isNotEmpty()) {
                    Text(
                        "${uiState.expenses.size} записей • итого %.0f ₽".format(uiState.totalExpenses),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MonthComparisonCard(uiState: AnalyticsUiState) {
    val isUp = uiState.monthComparison > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUp)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Этот месяц vs прошлый",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Текущий месяц", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "%.0f ₽".format(uiState.currentMonthExpenses),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            null,
                            tint = if (isUp) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        )
                        Text(
                            "${if (isUp) "+" else ""}${"%.1f".format(uiState.monthComparison)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUp) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        )
                    }
                    Text(
                        "Прошлый: %.0f ₽".format(uiState.previousMonthExpenses),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun MainStatisticsCard(uiState: AnalyticsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Статистика расходов",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            StatRow("Всего потрачено", "%.2f ₽".format(uiState.totalExpenses), Icons.Default.AccountBalanceWallet)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("В среднем в месяц", "%.0f ₽".format(uiState.averageExpensePerMonth), Icons.Default.CalendarMonth)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("В среднем в день", "%.0f ₽".format(uiState.averageExpensePerDay), Icons.Default.Today)
            if (uiState.averageExpensePerKm > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                StatRow("На 1 км", "%.2f ₽".format(uiState.averageExpensePerKm), Icons.Default.Speed)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Записей расходов", "${uiState.expenses.size}", Icons.Default.Receipt)
        }
    }
}

@Composable
fun StatRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PieChartCard(categoryExpenses: List<CategoryExpense>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Расходы по категориям", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            PieChart(categoryExpenses)
            Spacer(Modifier.height(16.dp))
            categoryExpenses.forEach { category ->
                CategoryLegendItem(category)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PieChart(data: List<CategoryExpense>) {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatable.animateTo(1f, animationSpec = tween(1000))
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            var startAngle = -90f
            data.forEach { category ->
                val sweepAngle = (category.percentage / 100f * 360f) * animatable.value
                drawArc(
                    color = getCategoryColor(category.category),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 35f)
                )
                startAngle += sweepAngle
            }
        }
    }
}

@Composable
fun CategoryLegendItem(category: CategoryExpense) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(getCategoryColor(category.category)))
            Spacer(Modifier.width(8.dp))
            Text(getCategoryName(category.category), style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.0f ₽".format(category.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                "${category.count} шт. • ${"%.1f".format(category.percentage)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MonthlyChartCard(monthlyExpenses: List<MonthlyExpense>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Расходы по месяцам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (monthlyExpenses.isNotEmpty()) {
                val chartEntryModel = entryModelOf(
                    *monthlyExpenses.mapIndexed { index, expense -> index to expense.amount }.toTypedArray()
                )
                Chart(
                    chart = lineChart(),
                    model = chartEntryModel,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            monthlyExpenses.getOrNull(value.toInt())?.month ?: ""
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                // Пиковый месяц под графиком
                val peak = monthlyExpenses.maxByOrNull { it.amount }
                if (peak != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Пик: ${peak.month} ${peak.year} — ${"%.0f ₽".format(peak.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FuelStatisticsCard(fuelStats: FuelStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalGasStation, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Статистика по топливу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            FuelStatRow("Средний расход", "%.2f л/100км".format(fuelStats.averageConsumption))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Всего заправлено", "%.1f л".format(fuelStats.totalLiters))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Потрачено на топливо", "%.0f ₽".format(fuelStats.totalCost))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Средняя цена за литр", "%.2f ₽".format(fuelStats.averagePricePerLiter))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Пройдено км", "${fuelStats.kmDriven} км")

            if (fuelStats.consumptionHistory.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Динамика расхода (л/100км)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                val chartModel = entryModelOf(
                    *fuelStats.consumptionHistory.mapIndexed { index, (_, consumption) ->
                        index to consumption
                    }.toTypedArray()
                )
                Chart(
                    chart = lineChart(),
                    model = chartModel,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            fuelStats.consumptionHistory.getOrNull(value.toInt())?.first ?: ""
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            }
        }
    }
}

@Composable
fun FuelStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ForecastCard(forecast: ExpenseForecast) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueryStats, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Прогноз расходов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "На основе последних 3 месяцев",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            FuelStatRow("Следующий месяц", "~ %.0f ₽".format(forecast.nextMonthEstimate))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Прогноз на год", "~ %.0f ₽".format(forecast.nextYearEstimate))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Средний месячный", "%.0f ₽".format(forecast.averageMonthly))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Тренд", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trendIcon = when (forecast.trend) {
                        "increasing" -> Icons.Default.TrendingUp
                        "decreasing" -> Icons.Default.TrendingDown
                        else -> Icons.Default.TrendingFlat
                    }
                    val trendColor = when (forecast.trend) {
                        "increasing" -> MaterialTheme.colorScheme.error
                        "decreasing" -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Icon(trendIcon, null, tint = trendColor)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (forecast.trend) {
                            "increasing" -> "Растут"
                            "decreasing" -> "Снижаются"
                            else -> "Стабильно"
                        },
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Anomaly Detection Card
// ---------------------------------------------------------------------------

@Composable
fun AnomalyCard(anomalies: List<com.aggin.carcost.presentation.screens.analytics.ExpenseAnomaly>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber,
                    null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Аномалии в расходах",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Значительные изменения по сравнению со средним за 3 месяца",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            anomalies.forEach { anomaly ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (anomaly.changePercent > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            null,
                            tint = if (anomaly.changePercent > 0) MaterialTheme.colorScheme.error
                                   else Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            anomaly.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        "%.0f ₽".format(anomaly.currentMonthAmount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (anomaly != anomalies.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyAnalyticsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Analytics, null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text("Нет данных для аналитики", style = MaterialTheme.typography.titleLarge)
            Text("Добавьте расходы, чтобы увидеть статистику", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun getCategoryColor(category: ExpenseCategory): Color {
    return when (category) {
        ExpenseCategory.FUEL -> Color(0xFFE57373)
        ExpenseCategory.MAINTENANCE -> Color(0xFF81C784)
        ExpenseCategory.REPAIR -> Color(0xFF64B5F6)
        ExpenseCategory.INSURANCE -> Color(0xFFFFD54F)
        ExpenseCategory.TAX -> Color(0xFFBA68C8)
        ExpenseCategory.PARKING -> Color(0xFF4DB6AC)
        ExpenseCategory.TOLL -> Color(0xFFFF8A65)
        ExpenseCategory.WASH -> Color(0xFFA1887F)
        ExpenseCategory.FINE -> Color(0xFFEF5350)
        ExpenseCategory.ACCESSORIES -> Color(0xFF9575CD)
        else -> Color(0xFF90A4AE)
    }
}

fun getCategoryName(category: ExpenseCategory): String {
    return when (category) {
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
}

@Composable
fun OdometerChartCard(history: List<OdometerPoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "История пробега",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            val chartEntryModel = entryModelOf(
                *history.mapIndexed { index, point -> index to point.odometer }.toTypedArray()
            )
            Chart(
                chart = columnChart(),
                model = chartEntryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        history.getOrNull(value.toInt())?.label ?: ""
                    }
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Spacer(Modifier.height(8.dp))
            val first = history.first().odometer
            val last = history.last().odometer
            val growth = last - first
            if (growth > 0) {
                Text(
                    "Прирост за период: +$growth км",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
