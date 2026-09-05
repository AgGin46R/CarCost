package com.aggin.carcost.presentation.screens.yearreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.R
import com.aggin.carcost.domain.year.YearSummaryCalculator
import com.aggin.carcost.presentation.common.LocalCarCurrency
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.currencyFormat
import androidx.compose.runtime.CompositionLocalProvider
import java.text.DateFormatSymbols
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Итоги года.
 *
 * Свёрстано страницей, а не таблицей: это то, что показывают другим, и там,
 * где обычный экран перечисляет, здесь один крупный факт на блок. Пустые места
 * не заполняются нулями — блок, для которого нет данных, просто не рисуется.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewScreen(
    navController: NavController,
    carId: String,
    year: Int? = null
) {
    val context = LocalContext.current
    val viewModel: YearReviewViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                YearReviewViewModel(
                    context.applicationContext as android.app.Application,
                    carId,
                    year
                ) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.yearreview_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Кнопка появляется только когда есть что отправлять
            if (uiState.summary?.hasEnoughData == true) {
                FloatingActionButton(onClick = {
                    scope.launch {
                        val image = graphicsLayer.toImageBitmap()
                        viewModel.share(image) { error ->
                            android.widget.Toast.makeText(
                                context, error, android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Icon(Icons.Default.Share, stringResource(R.string.yearreview_share))
                }
            }
        }
    ) { padding ->
        val summary = uiState.summary
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            summary == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.yearreview_no_car)) }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.availableYears.size > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.availableYears.forEach { y ->
                            FilterChip(
                                selected = y == summary.year,
                                onClick = { viewModel.load(y) },
                                label = { Text(y.toString()) }
                            )
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalCarCurrency provides (uiState.car?.currency ?: "RUB")
                ) {
                    // Снимок делается с этого блока, а не со всего экрана:
                    // в картинку не должны попасть шапка, выбор года и кнопка
                    Box(
                        Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        }
                    ) {
                        YearReviewPage(
                            summary = summary,
                            carName = uiState.car?.let { "${it.brand} ${it.model}" } ?: ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearReviewPage(
    summary: YearSummaryCalculator.YearSummary,
    carName: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = summary.year.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = carName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!summary.hasEnoughData) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.yearreview_not_enough, summary.recordCount),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Spacer(Modifier.height(28.dp))

        BigFact(
            value = currencyFormat(summary.totalSpent),
            label = stringResource(R.string.yearreview_spent)
        )

        summary.changeVsPrevious?.let { change ->
            val percent = (change * 100).toInt()
            Text(
                text = stringResource(
                    if (percent >= 0) R.string.yearreview_more_than_last
                    else R.string.yearreview_less_than_last,
                    kotlin.math.abs(percent)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (percent >= 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }

        summary.kmDriven?.let {
            Spacer(Modifier.height(24.dp))
            BigFact(
                value = stringResource(R.string.home_km, it),
                label = stringResource(R.string.yearreview_driven)
            )
        }

        summary.costPerKm?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.yearreview_cost_per_km,
                    currencyFormat(it, decimals = 2)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (summary.fillUps > 0) {
            Spacer(Modifier.height(24.dp))
            Section(stringResource(R.string.yearreview_section_fuel))
            Line(stringResource(R.string.yearreview_fillups, summary.fillUps))
            if (summary.liters > 0) {
                Line(stringResource(R.string.yearreview_liters, "%.0f".format(summary.liters)))
            }
            summary.averagePricePerLiter?.let {
                Line(
                    stringResource(
                        R.string.yearreview_avg_price,
                        currencyFormat(it, decimals = 2)
                    )
                )
            }
            summary.favouriteStation?.let {
                Line(stringResource(R.string.yearreview_favourite_station, it))
            }
        }

        summary.busiestMonth?.let { month ->
            Spacer(Modifier.height(24.dp))
            Section(stringResource(R.string.yearreview_section_months))
            Line(
                stringResource(
                    R.string.yearreview_busiest_month,
                    monthName(month.month),
                    currencyFormat(month.amount)
                )
            )
            summary.quietestMonth?.let { quiet ->
                Line(
                    stringResource(
                        R.string.yearreview_quietest_month,
                        monthName(quiet.month),
                        currencyFormat(quiet.amount)
                    )
                )
            }
        }

        summary.longestTripKm?.let { km ->
            Spacer(Modifier.height(24.dp))
            Section(stringResource(R.string.yearreview_section_trips))
            Line(stringResource(R.string.yearreview_longest_trip, "%.0f".format(km)))
            Line(stringResource(R.string.yearreview_trips_recorded, summary.tripsRecorded))
        }

        if (summary.byCategory.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Section(stringResource(R.string.yearreview_section_categories))
            summary.byCategory.take(5).forEach { (category, amount) ->
                Line("${category.displayName()} — ${currencyFormat(amount)}")
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.yearreview_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BigFact(value: String, label: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun Line(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** Название месяца в языке интерфейса */
private fun monthName(month: Int): String =
    DateFormatSymbols(Locale.getDefault()).months.getOrNull(month).orEmpty()
