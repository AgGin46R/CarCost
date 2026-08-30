package com.aggin.carcost.presentation.screens.service_timeline

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.presentation.components.SkeletonCardList
import com.aggin.carcost.presentation.navigation.Screen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.navigation.navigateOnce

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTimelineScreen(
    carId: String,
    navController: NavController,
    viewModel: ServiceTimelineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(carId) { viewModel.load(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.servicetimeline_taymlayn_obsluzhivaniya))
                        uiState.car?.let {
                            Text("${it.brand} ${it.model}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
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
        if (uiState.isLoading) {
            SkeletonCardList(count = 5, cardHeight = 90.dp)
            return@Scaffold
        }

        if (uiState.events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Build, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.servicetimeline_net_zapisey_ob_obsluzhivanii), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.servicetimeline_dobavte_rashod_kategorii_to_ili_remont_on),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Сводка
            item {
                TimelineSummaryCard(uiState)
                Spacer(Modifier.height(16.dp))
            }

            itemsIndexed(uiState.events, key = { _, e -> e.expense.id }) { index, event ->
                TimelineEventRow(
                    event = event,
                    isFirst = index == 0,
                    isLast = index == uiState.events.lastIndex,
                    onClick = {
                        navController.navigateOnce(
                            Screen.EditExpense.createRoute(event.expense.carId, event.expense.id)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineSummaryCard(uiState: ServiceTimelineUiState) {
    val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${uiState.serviceCount}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.cardetail_zapisey), style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${fmt.format(uiState.totalServiceExpenses)} ₽",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.servicetimeline_na_obsluzhivanie), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: TimelineEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expense = event.expense

    val dotColor = if (expense.category == ExpenseCategory.MAINTENANCE)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.tertiary

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline line + dot
        TimelineDot(
            color = dotColor,
            showTopLine = !isFirst,
            showBottomLine = !isLast
        )

        Spacer(Modifier.width(12.dp))

        // Card
        Card(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            expense.title ?: serviceLabel(expense),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            dateFormat.format(Date(expense.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        "${fmt.format(expense.amount)} ₽",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!expense.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        expense.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = dotColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            if (expense.category == ExpenseCategory.MAINTENANCE) stringResource(R.string.servicetimeline_obsluzhivanie) else stringResource(R.string.incidents_remont),
                            style = MaterialTheme.typography.labelSmall,
                            color = dotColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (expense.odometer > 0) {
                        Text(
                            stringResource(R.string.home_km, fmt.format(expense.odometer)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    expense.workshopName?.takeIf { it.isNotBlank() }?.let { ws ->
                        Text(
                            ws,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDot(
    color: Color,
    showTopLine: Boolean,
    showBottomLine: Boolean
) {
    val lineColor = color.copy(alpha = 0.3f)
    Box(
        modifier = Modifier.width(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.width(24.dp).height(72.dp)) {
            val centerX = size.width / 2
            val dotY = 20f
            val dotRadius = 8f

            // Линия сверху
            if (showTopLine) {
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, dotY - dotRadius),
                    strokeWidth = 2f
                )
            }

            // Точка
            drawCircle(color = color, radius = dotRadius, center = Offset(centerX, dotY))
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = dotRadius / 2,
                center = Offset(centerX, dotY)
            )

            // Линия снизу
            if (showBottomLine) {
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, dotY + dotRadius),
                    end = Offset(centerX, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
private fun serviceLabel(expense: com.aggin.carcost.data.local.database.entities.Expense): String {
    return expense.serviceType?.let { serviceTypeName(it) } ?: stringResource(R.string.servicetimeline_obsluzhivanie)
}

@Composable
private fun serviceTypeName(type: ServiceType) = type.displayName()

