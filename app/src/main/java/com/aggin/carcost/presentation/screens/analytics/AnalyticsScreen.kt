package com.aggin.carcost.presentation.screens.analytics

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
import com.aggin.carcost.presentation.components.EmptyState
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlin.math.abs
import com.aggin.carcost.presentation.common.LocalCarCurrency
import com.aggin.carcost.presentation.common.color
import com.aggin.carcost.presentation.common.currencyFormat
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.hasMixedCurrencies
import com.aggin.carcost.presentation.navigation.navigateOnce

// Валюта и её форматирование переехали в presentation/common/CarCurrency.kt:
// тот же приём понадобился другим экранам, и держать его копию в каждом — верный
// способ однажды поправить один и забыть остальные.


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

    // Ключ carId, а не Unit: переход между машинами — это новое открытие раздела
    LaunchedEffect(carId) { com.aggin.carcost.data.analytics.Analytics.analyticsOpened() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cardetail_analitika)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigateOnce(
                            com.aggin.carcost.presentation.navigation.Screen.YearReview
                                .createRoute(carId)
                        )
                    }) {
                        Icon(Icons.Default.EmojiEvents, stringResource(R.string.yearreview_open))
                    }
                    IconButton(onClick = {
                        // Pre-fill with avg consumption if available
                        val avgL100 = uiState.fuelStatistics?.averageConsumption ?: 0.0
                        navController.navigateOnce(
                            com.aggin.carcost.presentation.navigation.Screen.FuelCalculator
                                .createRoute(avgL100 = avgL100)
                        )
                    }) {
                        Icon(Icons.Default.Calculate, stringResource(R.string.analytics_kalkulyator_topliva))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
      CompositionLocalProvider(LocalCarCurrency provides (uiState.car?.currency ?: "RUB")) {
        // Предупреждение о смешанных валютах.
        //
        // Такое бывает только у старых данных: до того как валюта стала браться
        // у автомобиля, каждой записи проставлялся рубль независимо от машины.
        // Пересчитать по курсу нельзя — неизвестно ни курс какого дня брать, ни
        // какая валюта в записи настоящая. Поэтому не досочиняем, а говорим.
        if (hasMixedCurrencies(uiState.expenses)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.analytics_v_rashodah_smeshany_raznye_valyuty),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        stringResource(R.string.analytics_itogi_nizhe_skladyvayut_ih_kak_odinakovye) +
                            stringResource(R.string.analytics_im_doveryat_nelzya_valyutu_zapisi_mozhno) +
                            stringResource(R.string.analytics_ee_izmenenii),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Analytics,
                    title = stringResource(R.string.analytics_net_dannyh_dlya_analitiki),
                    subtitle = stringResource(R.string.analytics_dobavte_rashody_chtoby_uvidet_statistiku)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
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

                    if (uiState.contributions.isNotEmpty()) {
                        item {
                            ContributionsCard(
                                contributions = uiState.contributions,
                                names = uiState.contributorNames
                            )
                        }
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

                    if (uiState.stationPrices.isNotEmpty()) {
                        item { StationPricesCard(uiState.stationPrices) }
                    }

                    // Прогноза может не быть — пока мало истории. Тогда вместо
                    // исчезнувшего блока показываем, чего именно не хватает:
                    // пропавшая карточка выглядит как сбой, а не как «рано».
                    val forecast = uiState.forecast
                    if (forecast != null) {
                        item { ForecastCard(forecast) }
                    } else {
                        item { ForecastPendingCard() }
                    }
                }
            }
        } // end else
        } // end PullToRefreshBox
      } // end CompositionLocalProvider
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
                    stringResource(R.string.analytics_gps_poezdki),
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
                    label = stringResource(R.string.analytics_vsego_poezdok),
                    value = "${stats.totalTrips}"
                )
                GpsStat(
                    icon = Icons.Default.Straighten,
                    label = stringResource(R.string.analytics_obschiy_probeg),
                    value = stringResource(R.string.analytics_1f_km).format(stats.totalDistanceKm)
                )
                GpsStat(
                    icon = Icons.Default.Timeline,
                    // «Средняя» — средняя что? Значение в километрах, речь о длине поездки
                    label = stringResource(R.string.analytics_sr_poezdka),
                    value = stringResource(R.string.analytics_1f_km).format(stats.avgTripDistanceKm)
                )
                if (stats.avgSpeedKmh != null) {
                    GpsStat(
                        icon = Icons.Default.Speed,
                        label = stringResource(R.string.analytics_sr_skorost),
                        value = stringResource(R.string.analytics_0f_km_ch).format(stats.avgSpeedKmh)
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
                        stringResource(R.string.analytics_samaya_dlinnaya_poezdka),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        stringResource(R.string.analytics_1f_km).format(stats.longestTripKm),
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
                Text(stringResource(R.string.analytics_god_k_godu), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        tint = if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "${if (isUp) "+" else ""}${"%.1f".format(yc.changePercent)}%",
                        fontWeight = FontWeight.Bold,
                        color = if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
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
            currencyFormat(amount),
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
                Text(stringResource(R.string.analytics_top_mesyatsev_po_rashodam), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Text(currencyFormat(tm.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
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
                Text(stringResource(R.string.analytics_trendy_po_kategoriyam), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.analytics_izmeneniya_za_poslednie_3_mesyatsa),
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
    else if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

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
            currencyFormat(trend.recentAmount),
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
                if (isNew) stringResource(R.string.analytics_novoe)
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${uiState.car?.year} • ${uiState.car?.licensePlate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.expenses.isNotEmpty()) {
                    Text(
                        stringResource(R.string.analytics_zapisey_itogo, uiState.expenses.size, currencyFormat(uiState.totalExpenses)),
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
                stringResource(R.string.analytics_etot_mesyats_vs_proshlyy),
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
                    Text(stringResource(R.string.analytics_tekuschiy_mesyats), style = MaterialTheme.typography.bodySmall)
                    Text(
                        currencyFormat(uiState.currentMonthExpenses),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            null,
                            tint = if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "${if (isUp) "+" else ""}${"%.1f".format(uiState.monthComparison)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        stringResource(R.string.analytics_proshlyy, currencyFormat(uiState.previousMonthExpenses)),
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
                stringResource(R.string.analytics_statistika_rashodov),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            StatRow(stringResource(R.string.analytics_vsego_potracheno), currencyFormat(uiState.totalExpenses, 2), Icons.Default.AccountBalanceWallet)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow(stringResource(R.string.analytics_v_srednem_v_mesyats), currencyFormat(uiState.averageExpensePerMonth), Icons.Default.CalendarMonth)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow(stringResource(R.string.analytics_v_srednem_v_den), currencyFormat(uiState.averageExpensePerDay), Icons.Default.Today)
            if (uiState.averageExpensePerKm > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                StatRow(stringResource(R.string.analytics_na_1_km), currencyFormat(uiState.averageExpensePerKm, 2), Icons.Default.Speed)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow(stringResource(R.string.analytics_zapisey_rashodov), "${uiState.expenses.size}", Icons.Default.Receipt)
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
            Text(stringResource(R.string.analytics_rashody_po_kategoriyam), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text(currencyFormat(category.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.analytics_sht, category.count, "%.1f".format(category.percentage)),
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
            Text(stringResource(R.string.analytics_rashody_po_mesyatsam), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        stringResource(R.string.analytics_pik, peak.month, peak.year, currencyFormat(peak.amount)),
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
                Text(stringResource(R.string.analytics_statistika_po_toplivu), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            // Стоимость километра идёт первой у машин с двумя источниками.
            //
            // У подключаемого гибрида раздельные «л/100 км» и «кВт·ч/100 км»
            // почти бессмысленны: обе величины зависят от того, какую долю пути
            // проехали на розетке, а не от прожорливости машины. Месяц езды на
            // электричестве покажет расход бензина 1,5 л/100 км — и это не
            // достижение двигателя. Сравнивать можно только стоимость километра.
            fuelStats.costPerKm?.let { perKm ->
                FuelStatRow(stringResource(R.string.analytics_stoimost_kilometra), currencyFormat(perKm, decimals = 2))
                fuelStats.electricCostShare?.let { share ->
                    FuelStatRow(
                        stringResource(R.string.analytics_iz_nih_na_elektrichestvo),
                        "%.0f %%".format(share * 100)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.analytics_rashody_nizhe_pokazany_po_otdelnosti_u) +
                        stringResource(R.string.analytics_istochnikami_oni_zavisyat_ot_doli_poezdok) +
                        stringResource(R.string.analytics_poetomu_sravnivat_ih_s_obychnoy_mashinoy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
            }

            // Расход считается только по заправкам «до полного»: пока их меньше двух,
            // честного числа нет, и показывать выдуманное хуже, чем объяснить почему
            if (fuelStats.totalLiters > 0) {
                FuelStatRow(
                    stringResource(R.string.analytics_sredniy_rashod),
                    fuelStats.averageConsumption
                        ?.let { stringResource(R.string.analytics_2f_l_100km).format(it) }
                        ?: stringResource(R.string.analytics_nuzhny_2_zapravki_do_polnogo)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FuelStatRow(stringResource(R.string.analytics_vsego_zapravleno), stringResource(R.string.analytics_1f_l).format(fuelStats.totalLiters))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FuelStatRow(stringResource(R.string.analytics_potracheno_na_toplivo), currencyFormat(fuelStats.totalCost))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FuelStatRow(stringResource(R.string.analytics_srednyaya_tsena_za_litr), currencyFormat(fuelStats.averagePricePerLiter))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (fuelStats.totalKwh > 0) {
                FuelStatRow(
                    stringResource(R.string.analytics_rashod_elektrichestva),
                    fuelStats.averageEnergyConsumption
                        ?.let { stringResource(R.string.analytics_1f_kvt_ch_100km).format(it) }
                        ?: stringResource(R.string.analytics_nuzhny_2_zaryadki_do_100)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FuelStatRow(stringResource(R.string.analytics_vsego_zaryazheno), stringResource(R.string.cardetail_1f_kvt_ch).format(fuelStats.totalKwh))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FuelStatRow(stringResource(R.string.analytics_potracheno_na_zaryadku), currencyFormat(fuelStats.energyCost))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            if (fuelStats.averageConsumption != null) {
                FuelStatRow(stringResource(R.string.analytics_probeg_v_raschete), stringResource(R.string.home_km, fuelStats.kmDriven))
            }

            if (fuelStats.consumptionHistory.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.analytics_dinamika_rashoda_l_100km),
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

/**
 * Цены литра по заправкам.
 *
 * Показывается, только когда есть что сравнивать. Заправка, где были один раз,
 * в список не попадает: одна цена в один день — это не цена заправки.
 */
@Composable
fun StationPricesCard(
    stations: List<com.aggin.carcost.domain.fuel.StationPriceAnalyzer.Station>
) {
    if (stations.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.analytics_stations_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.analytics_stations_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            stations.forEachIndexed { index, station ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            station.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            stringResource(R.string.analytics_stations_fillups, station.fillUps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            currencyFormat(station.averagePerLiter, decimals = 2),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // У самой дешёвой разницы нет — и подписывать её нечем
                        if (station.overpayPerLiter > 0.009) {
                            Text(
                                stringResource(
                                    R.string.analytics_stations_overpay,
                                    currencyFormat(station.overpayPerLiter, decimals = 2)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Итог имеет смысл только при разных ценах: иначе это ноль,
            // напечатанный отдельной строкой
            val overpay = com.aggin.carcost.domain.fuel.StationPriceAnalyzer.totalOverpay(stations)
            if (overpay > 1.0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.analytics_stations_total_overpay, currencyFormat(overpay)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(stringResource(R.string.analytics_prognoz_rashodov), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                // Раньше здесь стояло «На основе последних 3 месяцев» независимо
                // от того, сколько месяцев на самом деле было в расчёте
                stringResource(R.string.analytics_po_zavershennym_mesyatsam_tekuschiy_esche, forecast.basedOnMonths),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            FuelStatRow(stringResource(R.string.analytics_sleduyuschiy_mesyats), "~ ${currencyFormat(forecast.nextMonthEstimate)}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow(stringResource(R.string.analytics_prognoz_na_god), "~ ${currencyFormat(forecast.nextYearEstimate)}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FuelStatRow(stringResource(R.string.analytics_sredniy_mesyachnyy), currencyFormat(forecast.averageMonthly))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.analytics_trend), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trendIcon = when (forecast.trend) {
                        "increasing" -> Icons.Default.TrendingUp
                        "decreasing" -> Icons.Default.TrendingDown
                        else -> Icons.Default.TrendingFlat
                    }
                    val trendColor = when (forecast.trend) {
                        "increasing" -> MaterialTheme.colorScheme.error
                        "decreasing" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Icon(trendIcon, null, tint = trendColor)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (forecast.trend) {
                            "increasing" -> stringResource(R.string.analytics_rastut)
                            "decreasing" -> stringResource(R.string.analytics_snizhayutsya)
                            else -> stringResource(R.string.analytics_stabilno)
                        },
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

/**
 * Кто сколько внёс в общую машину.
 *
 * Показывается только когда плательщиков больше одного — у одиночной машины это
 * всегда «вы, 100 %».
 *
 * Кроме доли показывается отклонение от равного деления: сама по себе доля
 * «62 %» ничего не решает, а «на 12 400 ₽ больше поровну» — это уже разговор о
 * том, кто кому сколько должен, ради которого секция и нужна.
 */
@Composable
fun ContributionsCard(
    contributions: List<com.aggin.carcost.domain.contribution.ContributionCalculator.Contribution>,
    names: Map<String, String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.analytics_kto_skolko_zaplatil),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            contributions.forEach { c ->
                val label = when {
                    c.userId == null -> stringResource(R.string.analytics_avtor_neizvesten)
                    else -> names[c.userId] ?: stringResource(R.string.analytics_uchastnik)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${currencyFormat(c.amount)} · ${(c.share * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { c.share.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = if (c.userId == null) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.primary
                )

                c.deviationFromEqual?.let { deviation ->
                    // Копейки в такой фразе только мешают
                    if (kotlin.math.abs(deviation) >= 1.0) {
                        Text(
                            text = if (deviation > 0)
                                stringResource(R.string.analytics_na_bolshe_porovnu, currencyFormat(deviation))
                            else
                                stringResource(R.string.analytics_na_menshe_porovnu, currencyFormat(-deviation)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                if (c != contributions.last()) Spacer(Modifier.height(14.dp))
            }

            if (contributions.any { it.userId == null }) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.analytics_zapisi_bez_avtora_sdelany_do_togo_kak) +
                        stringResource(R.string.analytics_zapominat_kto_platil),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Заглушка прогноза, пока истории мало.
 *
 * Прогноз по одной записи — это не прогноз, а та же запись, умноженная на
 * двенадцать. Вместо неё говорим прямо, чего ждём.
 */
@Composable
fun ForecastPendingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.QueryStats,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.analytics_prognoz_rashodov),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.analytics_poyavitsya_kogda_naberutsya_dva_polnyh) +
                    stringResource(R.string.analytics_schitat_po_nepolnomu_mesyatsu_nechestno),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
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
                    stringResource(R.string.analytics_anomalii_v_rashodah),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.analytics_znachitelnye_izmeneniya_po_sravneniyu_so),
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
                                   else MaterialTheme.colorScheme.tertiary,
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
                        currencyFormat(anomaly.currentMonthAmount),
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


fun getCategoryColor(category: ExpenseCategory): Color = category.color()

@Composable
fun getCategoryName(category: ExpenseCategory): String = category.displayName()

@Composable
fun OdometerChartCard(history: List<OdometerPoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.analytics_istoriya_probega),
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
                    stringResource(R.string.analytics_prirost_za_period_km, growth),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
