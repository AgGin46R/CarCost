package com.aggin.carcost.presentation.screens.maintenance_dashboard

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.presentation.components.SkeletonCardList
import com.aggin.carcost.presentation.navigation.Screen
import kotlin.math.abs
import com.aggin.carcost.presentation.navigation.navigateOnce

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceDashboardScreen(
    navController: NavController,
    viewModel: MaintenanceDashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val overdueCount = uiState.items.count { it.urgency == ReminderUrgency.OVERDUE }
    val soonCount = uiState.items.count { it.urgency == ReminderUrgency.SOON }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_dashbord_to)) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigateOnce(Screen.EditMaintenanceReminder.createRoute()) }
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.maintenancedashboard_dobavit_napominanie_to))
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            SkeletonCardList(count = 4, cardHeight = 100.dp)
            return@Scaffold
        }

        if (uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Build, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.maintenancedashboard_net_aktivnyh_napominaniy), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.maintenancedashboard_dobavte_to_v_kartochke_avtomobilya),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Сводка
            if (overdueCount > 0 || soonCount > 0) {
                item {
                    StatusSummaryCard(overdueCount, soonCount)
                }
            }

            // Просрочено
            val overdue = uiState.items.filter { it.urgency == ReminderUrgency.OVERDUE }
            if (overdue.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.maintenancedashboard_prosrocheno), MaterialTheme.colorScheme.error)
                }
                items(overdue, key = { it.reminder.id }) { item ->
                    ReminderCard(item, navController)
                }
            }

            // Скоро
            val soon = uiState.items.filter { it.urgency == ReminderUrgency.SOON }
            if (soon.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.maintenancedashboard_skoro), MaterialTheme.colorScheme.tertiary)
                }
                items(soon, key = { it.reminder.id }) { item ->
                    ReminderCard(item, navController)
                }
            }

            // Остальные
            val ok = uiState.items.filter { it.urgency == ReminderUrgency.OK }
            if (ok.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.maintenancedashboard_v_norme), MaterialTheme.colorScheme.primary)
                }
                items(ok, key = { it.reminder.id }) { item ->
                    ReminderCard(item, navController)
                }
            }
        }
    }
}

@Composable
private fun StatusSummaryCard(overdueCount: Int, soonCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overdueCount > 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (overdueCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$overdueCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(stringResource(R.string.maintenancedashboard_prosrocheno), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (soonCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$soonCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(stringResource(R.string.maintenancedashboard_skoro), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun ReminderCard(item: ReminderWithCar, navController: NavController) {
    val urgencyColor = when (item.urgency) {
        ReminderUrgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        ReminderUrgency.SOON -> MaterialTheme.colorScheme.tertiaryContainer
        ReminderUrgency.OK -> MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = {
            navController.navigateOnce(
                Screen.EditMaintenanceReminder.createRoute(reminderId = item.reminder.id)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = urgencyColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Название авто
                item.car?.let {
                    Text(
                        "${it.brand} ${it.model} · ${it.licensePlate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                // Тип ТО
                Text(
                    stringResource(item.reminder.type.displayNameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Пробег
                Text(
                    buildString {
                        append(stringResource(R.string.maintenancedashboard_sleduyuschee_to_km, item.reminder.nextChangeOdometer))
                        item.car?.let { append(stringResource(R.string.maintenancedashboard_seychas_km, it.currentOdometer)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Индикатор км
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (item.urgency) {
                        ReminderUrgency.OVERDUE -> MaterialTheme.colorScheme.error
                        ReminderUrgency.SOON -> MaterialTheme.colorScheme.tertiary
                        ReminderUrgency.OK -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        when {
                            item.kmRemaining <= 0 -> stringResource(R.string.maintenancedashboard_km, abs(item.kmRemaining))
                            else -> stringResource(R.string.home_km, item.kmRemaining)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (item.urgency) {
                            ReminderUrgency.OVERDUE -> MaterialTheme.colorScheme.onError
                            ReminderUrgency.SOON -> MaterialTheme.colorScheme.onTertiary
                            ReminderUrgency.OK -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                item.predictedDate?.let { date ->
                    Text(
                        "~${date.format(DateTimeFormatter.ofPattern("dd MMM"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
