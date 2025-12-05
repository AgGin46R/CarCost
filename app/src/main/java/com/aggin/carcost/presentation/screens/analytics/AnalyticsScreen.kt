package com.aggin.carcost.presentation.screens.analytics

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import androidx.compose.ui.graphics.drawscope.Stroke

// --- ФАБРИКА ДЛЯ СОЗДАНИЯ VIEWMODEL С ПАРАМЕТРАМИ ---
class AnalyticsViewModelFactory(
    private val application: Application,
    private val carId: String // ✅ String UUID
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EnhancedAnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EnhancedAnalyticsViewModel(
                application,
                androidx.lifecycle.SavedStateHandle(mapOf("carId" to carId.toString()))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAnalyticsScreen(
    navController: NavController,
    carId: String // ✅ String UUID
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: EnhancedAnalyticsViewModel = viewModel(
        factory = AnalyticsViewModelFactory(application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аналитика") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.expenses.isEmpty()) {
            EmptyAnalyticsState(Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CarSummaryCard(uiState) }
                item { MonthComparisonCard(uiState) }
                item { MainStatisticsCard(uiState) }
                if (uiState.categoryExpenses.isNotEmpty()) {
                    item { PieChartCard(uiState.categoryExpenses) }
                }
                if (uiState.monthlyExpenses.isNotEmpty()) {
                    item { MonthlyChartCard(uiState.monthlyExpenses) }
                }
                uiState.fuelStatistics?.let { fuelStats ->
                    item { FuelStatisticsCard(fuelStats) }
                }
                uiState.forecast?.let { forecast ->
                    item { ForecastCard(forecast) }
                }
            }
        }
    }
}

@Composable
fun CarSummaryCard(uiState: AnalyticsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${uiState.car?.brand} ${uiState.car?.model}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.car?.year} • ${uiState.car?.licensePlate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun MonthComparisonCard(uiState: AnalyticsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.monthComparison > 0)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Сравнение с прошлым месяцем",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Текущий месяц", style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.2f ₽", uiState.currentMonthExpenses),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (uiState.monthComparison > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            null,
                            tint = if (uiState.monthComparison > 0)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            String.format("%.1f%%", kotlin.math.abs(uiState.monthComparison)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Прошлый: ${String.format("%.2f ₽", uiState.previousMonthExpenses)}",
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
            Spacer(modifier = Modifier.height(16.dp))
            StatRow("Всего потрачено", String.format("%.2f ₽", uiState.totalExpenses), Icons.Default.AccountBalanceWallet)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("В среднем в месяц", String.format("%.2f ₽", uiState.averageExpensePerMonth), Icons.Default.CalendarMonth)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("В среднем в день", String.format("%.2f ₽", uiState.averageExpensePerDay), Icons.Default.Today)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("На 1 км", String.format("%.2f ₽", uiState.averageExpensePerKm), Icons.Default.Speed)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Записей расходов", uiState.expenses.size.toString(), Icons.Default.Receipt)
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
            Icon(
                icon,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PieChartCard(categoryExpenses: List<CategoryExpense>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Расходы по категориям",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            PieChart(categoryExpenses)
            Spacer(modifier = Modifier.height(16.dp))
            categoryExpenses.forEach { category ->
                CategoryLegendItem(category)
                Spacer(modifier = Modifier.height(8.dp))
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(category.category))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                getCategoryName(category.category),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                String.format("%.2f ₽", category.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${category.count} шт. • ${String.format("%.1f%%", category.percentage)}",
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
            Text(
                "Расходы по месяцам",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (monthlyExpenses.isNotEmpty()) {
                val chartEntryModel = entryModelOf(
                    *monthlyExpenses.mapIndexed { index, expense ->
                        index to expense.amount
                    }.toTypedArray()
                )
                Chart(
                    chart = columnChart(),
                    model = chartEntryModel,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ -> monthlyExpenses[value.toInt()].month }
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Статистика по топливу",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FuelStatRow("Средний расход", String.format("%.2f л/100км", fuelStats.averageConsumption))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Всего заправлено", String.format("%.2f л", fuelStats.totalLiters))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Потрачено на топливо", String.format("%.2f ₽", fuelStats.totalCost))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Средняя цена за литр", String.format("%.2f ₽", fuelStats.averagePricePerLiter))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Пройдено км", "${fuelStats.kmDriven} км")
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
                Spacer(modifier = Modifier.width(8.dp))
                Text("Прогноз расходов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            FuelStatRow("Следующий месяц", String.format("~ %.0f ₽", forecast.nextMonthEstimate))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow("Прогноз на год", String.format("~ %.0f ₽", forecast.nextYearEstimate))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
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
                    Icon(trendIcon, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when (forecast.trend) {
                            "increasing" -> "Растут"
                            "decreasing" -> "Снижаются"
                            else -> "Стабильно"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyAnalyticsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Analytics,
                null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
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