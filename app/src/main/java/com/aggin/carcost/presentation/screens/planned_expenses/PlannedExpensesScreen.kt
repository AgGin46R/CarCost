package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.*
import com.aggin.carcost.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedExpensesScreen(
    carId: String,
    navController: NavController,
    viewModel: PlannedExpensesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<PlannedExpenseStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Планы покупок") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, "Фильтр")
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Все") },
                            onClick = {
                                selectedFilter = null
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Запланировано") },
                            onClick = {
                                selectedFilter = PlannedExpenseStatus.PLANNED
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("В процессе") },
                            onClick = {
                                selectedFilter = PlannedExpenseStatus.IN_PROGRESS
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Выполнено") },
                            onClick = {
                                selectedFilter = PlannedExpenseStatus.COMPLETED
                                showFilterMenu = false
                            }
                        )
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
                onClick = { navController.navigate(Screen.AddPlannedExpense.createRoute(carId)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "Добавить план")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Статистика
            StatisticsCard(
                plannedCount = uiState.plannedCount,
                totalEstimated = uiState.totalEstimatedAmount
            )

            // Список
            val filteredItems = if (selectedFilter != null) {
                uiState.plannedExpenses.filter { it.status == selectedFilter }
            } else {
                uiState.plannedExpenses
            }

            if (filteredItems.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it.id }) { plannedExpense ->
                        PlannedExpenseCard(
                            plannedExpense = plannedExpense,
                            onClick = {
                                navController.navigate(
                                    Screen.EditPlannedExpense.createRoute(carId, plannedExpense.id)
                                )
                            },
                            onComplete = {
                                // Переход к созданию расхода с данными из плана
                                viewModel.markAsInProgress(plannedExpense.id)
                                navController.navigate(
                                    "${Screen.AddExpense.createRoute(carId)}?plannedId=${plannedExpense.id}"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsCard(
    plannedCount: Int,
    totalEstimated: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(
                icon = Icons.Default.Assignment,
                label = "Запланировано",
                value = plannedCount.toString()
            )
            StatItem(
                icon = Icons.Default.AttachMoney,
                label = "Ориентировочно",
                value = formatCurrency(totalEstimated)
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedExpenseCard(
    plannedExpense: PlannedExpense,
    onClick: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            getCategoryIcon(plannedExpense.category),
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = plannedExpense.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (plannedExpense.status == PlannedExpenseStatus.COMPLETED)
                                TextDecoration.LineThrough else null
                        )
                    }

                    if (plannedExpense.description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = plannedExpense.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PriorityBadge(plannedExpense.priority)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Информация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    if (plannedExpense.estimatedAmount != null) {
                        Text(
                            text = "~${formatCurrency(plannedExpense.estimatedAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (plannedExpense.targetDate != null) {
                        Text(
                            text = "До ${formatDate(plannedExpense.targetDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (plannedExpense.targetOdometer != null) {
                        Text(
                            text = "При ${plannedExpense.targetOdometer} км",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(plannedExpense.status)
            }

            // Кнопка выполнения
            if (plannedExpense.status == PlannedExpenseStatus.PLANNED ||
                plannedExpense.status == PlannedExpenseStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Выполнить")
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Assignment,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Нет запланированных покупок",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте план покупки",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}