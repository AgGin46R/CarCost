package com.aggin.carcost.presentation.screens.tco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.domain.DepreciationCalculator
import com.aggin.carcost.presentation.components.SkeletonCardList
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcoScreen(
    carId: String,
    navController: NavController,
    viewModel: TcoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(carId) { viewModel.load(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Стоимость владения (TCO)")
                        uiState.car?.let {
                            Text(
                                "${it.brand} ${it.model} ${it.year}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
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
    ) { padding ->
        if (uiState.isLoading) {
            SkeletonCardList(count = 4, cardHeight = 120.dp)
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Главная карточка TCO
            item { TcoSummaryCard(uiState) }

            // Показатели эффективности
            item { TcoMetricsCard(uiState) }

            // Калькулятор амортизации
            if (uiState.purchasePrice > 0) {
                item {
                    DepreciationCard(
                        uiState = uiState,
                        onMarketValueChange = { viewModel.updateMarketValue(it) }
                    )
                }
            }

            // Разбивка по категориям
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    Text(
                        "РАСХОДЫ ПО КАТЕГОРИЯМ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                items(uiState.categoryBreakdown, key = { it.category.name }) { item ->
                    CategoryBreakdownRow(item, uiState.totalExpenses)
                }
            }
        }
    }
}

@Composable
private fun TcoSummaryCard(uiState: TcoUiState) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Итоговая стоимость владения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            // Большая цифра
            Text(
                "${fmt.format(uiState.totalCostOfOwnership)} ₽",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            // Слагаемые
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Цена покупки", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (uiState.purchasePrice > 0) "${fmt.format(uiState.purchasePrice)} ₽"
                        else "Не указана",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Расходы за всё время", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${fmt.format(uiState.totalExpenses)} ₽",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Пробег с покупки", style = MaterialTheme.typography.labelSmall)
                    Text("${fmt.format(uiState.kmDriven)} км", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Период владения", style = MaterialTheme.typography.labelSmall)
                    Text(formatMonths(uiState.monthsOwned), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TcoMetricsCard(uiState: TcoUiState) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 2 }
    val fmtInt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Показатели эффективности",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "За 1 км",
                    value = if (uiState.costPerKm > 0) "${fmt.format(uiState.costPerKm)} ₽" else "—",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "В месяц",
                    value = "${fmtInt.format(uiState.costPerMonth)} ₽",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "В год",
                    value = "${fmtInt.format(uiState.costPerYear)} ₽",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CategoryBreakdownRow(item: TcoCategoryBreakdown, totalExpenses: Double) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(categoryEmoji(item.category), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        categoryName(item.category),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${item.count} записей",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    "${fmt.format(item.total)} ₽",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(item.share * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { item.share },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun DepreciationCard(
    uiState: TcoUiState,
    onMarketValueChange: (String) -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Амортизация автомобиля",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Market value input
            OutlinedTextField(
                value = uiState.marketValueInput,
                onValueChange = onMarketValueChange,
                label = { Text("Текущая рыночная стоимость") },
                placeholder = { Text("Оставьте пустым для авторасчёта") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("₽") }
            )

            // Summary row
            if (uiState.estimatedCurrentValue > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Текущая стоимость", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            "${fmt.format(uiState.estimatedCurrentValue)} ₽",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Обесценивание", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            "−${fmt.format(uiState.totalDepreciation)} ₽",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Vico LineChart
            if (uiState.depreciationPoints.size >= 2) {
                Text(
                    "Прогноз стоимости на 10 лет",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                val entries = remember(uiState.depreciationPoints) {
                    uiState.depreciationPoints.mapIndexed { idx, pt ->
                        FloatEntry(idx.toFloat(), pt.value.toFloat())
                    }
                }
                val chartModel = remember(entries) { entryModelOf(entries) }

                Chart(
                    chart = lineChart(),
                    model = chartModel,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            uiState.depreciationPoints.getOrNull(value.toInt())
                                ?.year?.toString()?.takeLast(2) ?: ""
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}

private fun formatMonths(months: Int): String {
    val y = months / 12
    val m = months % 12
    return buildString {
        if (y > 0) append("${y} г. ")
        if (m > 0) append("${m} мес.")
        if (y == 0 && m == 0) append("< 1 мес.")
    }.trim()
}

private fun categoryEmoji(c: ExpenseCategory) = when (c) {
    ExpenseCategory.FUEL -> "⛽"
    ExpenseCategory.MAINTENANCE -> "🔧"
    ExpenseCategory.REPAIR -> "🛠️"
    ExpenseCategory.INSURANCE -> "🛡️"
    ExpenseCategory.TAX -> "📋"
    ExpenseCategory.PARKING -> "🅿️"
    ExpenseCategory.TOLL -> "🛣️"
    ExpenseCategory.WASH -> "🚿"
    ExpenseCategory.FINE -> "⚠️"
    ExpenseCategory.ACCESSORIES -> "🔩"
    ExpenseCategory.OTHER -> "📦"
}

private fun categoryName(c: ExpenseCategory) = when (c) {
    ExpenseCategory.FUEL -> "Топливо"
    ExpenseCategory.MAINTENANCE -> "Обслуживание"
    ExpenseCategory.REPAIR -> "Ремонт"
    ExpenseCategory.INSURANCE -> "Страховка"
    ExpenseCategory.TAX -> "Налог"
    ExpenseCategory.PARKING -> "Парковка"
    ExpenseCategory.TOLL -> "Платная дорога"
    ExpenseCategory.WASH -> "Мойка"
    ExpenseCategory.FINE -> "Штраф"
    ExpenseCategory.ACCESSORIES -> "Аксессуары"
    ExpenseCategory.OTHER -> "Прочее"
}
