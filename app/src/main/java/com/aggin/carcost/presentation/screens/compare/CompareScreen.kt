package com.aggin.carcost.presentation.screens.compare

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.presentation.components.SkeletonCard
import com.aggin.carcost.presentation.screens.analytics.getCategoryName
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    navController: NavController,
    viewModel: CompareViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedStats = uiState.selectedCarIds
        .mapNotNull { uiState.carStats[it] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_sravnenie_avto)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Car selection ─────────────────────────────────────────────────
            item {
                Text(
                    stringResource(R.string.compare_vyberite_avtomobili_dlya_sravneniya),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.availableCars) { car ->
                        val selected = car.id in uiState.selectedCarIds
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleCarSelection(car.id) },
                            label = { Text("${car.brand} ${car.model}") },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // ── Period filter ─────────────────────────────────────────────────
            if (uiState.selectedCarIds.size >= 2) {
                item {
                    Text(
                        stringResource(R.string.components_period),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ComparePeriod.entries) { period ->
                            val selected = uiState.selectedPeriod == period
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setPeriod(period) },
                                label = { Text(period.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            if (uiState.selectedCarIds.size < 2) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CompareArrows,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.compare_vyberite_minimum_2_avtomobilya),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (uiState.isLoading) {
                items(3) {
                    SkeletonCard(height = 120.dp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            } else {
                // ── Monthly expenses line chart ────────────────────────────────
                if (selectedStats.isNotEmpty() && uiState.periodMonthLabels.isNotEmpty()) {
                    item {
                        MonthlyCompareChartCard(
                            stats = selectedStats,
                            monthLabels = uiState.periodMonthLabels
                        )
                    }
                }

                // ── Total expenses ───────────────────────────────────────────
                item {
                    CompareCard(title = stringResource(R.string.compare_obschie_rashody_za_period), icon = Icons.Default.AccountBalanceWallet) {
                        CompareRow(selectedStats) { stats ->
                            String.format("%.0f ₽", stats.totalExpenses)
                        }
                    }
                }

                // ── Records count ────────────────────────────────────────────
                item {
                    CompareCard(title = stringResource(R.string.analytics_zapisey_rashodov), icon = Icons.Default.Receipt) {
                        CompareRow(selectedStats) { stats ->
                            stringResource(R.string.compare_sht, stats.expenseCount)
                        }
                    }
                }

                // ── Cost per km ──────────────────────────────────────────────
                item {
                    CompareCard(title = stringResource(R.string.compare_stoimost_1_km), icon = Icons.Default.Speed) {
                        CompareRow(selectedStats) { stats ->
                            if (stats.costPerKm > 0) String.format(stringResource(R.string.compare_2f_km), stats.costPerKm)
                            else "—"
                        }
                    }
                }

                // ── Maintenance per 10k km ────────────────────────────────────
                item {
                    CompareCard(title = stringResource(R.string.compare_to_i_remont_na_10_000_km), icon = Icons.Default.Build) {
                        CompareRow(selectedStats) { stats ->
                            if (stats.maintenancePer10k > 0)
                                String.format("%.0f ₽", stats.maintenancePer10k)
                            else "—"
                        }
                    }
                }

                // ── Avg fuel consumption ─────────────────────────────────────
                item {
                    CompareCard(title = stringResource(R.string.compare_sredniy_rashod_topliva), icon = Icons.Default.LocalGasStation) {
                        CompareRow(selectedStats) { stats ->
                            stats.avgFuelConsumption?.let { String.format(stringResource(R.string.analytics_2f_l_100km), it) } ?: stringResource(R.string.compare_net_dannyh)
                        }
                    }
                }

                // ── Top categories ────────────────────────────────────────────
                if (selectedStats.all { it.topCategories.isNotEmpty() }) {
                    item {
                        CompareCard(title = stringResource(R.string.compare_top_kategoriy_rashodov), icon = Icons.Default.PieChart) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedStats.forEach { stats ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${stats.car.brand} ${stats.car.model}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        stats.topCategories.forEach { (category, amount) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    getCategoryName(category),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    String.format("%.0f ₽", amount),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Monthly comparison chart ──────────────────────────────────────────────────

@Composable
fun MonthlyCompareChartCard(
    stats: List<CarCompareStats>,
    monthLabels: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ShowChart,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.analytics_rashody_po_mesyatsam),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stats.forEach { carStats ->
                    Text(
                        "● ${carStats.car.brand} ${carStats.car.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val allZero = stats.all { cs -> cs.monthlyExpenses.all { it.second == 0.0 } }
            if (!allZero && monthLabels.isNotEmpty()) {
                val entrySeries = stats.map { carStats ->
                    carStats.monthlyExpenses.mapIndexed { idx, (_, amount) ->
                        FloatEntry(idx.toFloat(), amount.toFloat())
                    }
                }
                val model = entryModelOf(*entrySeries.toTypedArray())
                Chart(
                    chart = lineChart(),
                    model = model,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            monthLabels.getOrElse(value.toInt()) { "" }
                        },
                        labelRotationDegrees = -45f
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.compare_net_dannyh_za_vybrannyy_period),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Reusable card / row composables ──────────────────────────────────────────

@Composable
fun CompareCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun CompareRow(
    stats: List<CarCompareStats>,
    // Значение собирается из ресурсов, а значит внутри композиции: без пометки
    // подписи в вызовах пришлось бы поднимать наружу по одной
    valueFor: @Composable (CarCompareStats) -> String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { carStats ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${carStats.car.brand} ${carStats.car.model}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    valueFor(carStats),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
