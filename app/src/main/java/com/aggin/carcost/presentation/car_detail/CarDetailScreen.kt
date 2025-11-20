package com.aggin.carcost.presentation.screens.car_detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.presentation.navigation.Screen
import com.aggin.carcost.presentation.components.ExpenseFilterDialog
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterListOff
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    carId: Long,
    navController: NavController,
    viewModel: CarDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.car?.let { "${it.brand} ${it.model}" } ?: "Загрузка...")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Map.createRoute(carId)) }) {
                        Icon(Icons.Default.Map, "Карта")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Analytics.createRoute(carId)) }) {
                        Icon(Icons.Default.Assessment, "Аналитика")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Меню")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Редактировать") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.EditCar.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт данных") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Export.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                        }
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
                onClick = { navController.navigate(Screen.AddExpense.createRoute(carId)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "Добавить расход")
            }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CarInfoCard(uiState) }
                item { StatisticsCard(uiState) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "История расходов",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.currentFilter.isActive()) {
                                Text(
                                    text = "Активных фильтров: ${uiState.currentFilter.getActiveFilterCount()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.currentFilter.isActive()) {
                                IconButton(onClick = { viewModel.clearFilter() }) {
                                    Icon(
                                        Icons.Default.FilterListOff,
                                        "Сбросить фильтр",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            TextButton(onClick = { showFilterDialog = true }) {
                                val filterColor = if (uiState.currentFilter.isActive()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                Icon(
                                    Icons.Default.FilterList,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = filterColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Фильтр", color = filterColor)
                            }
                        }
                    }
                }

                if (uiState.expenses.isEmpty()) {
                    item {
                        EmptyExpensesState(isFiltered = uiState.currentFilter.isActive())
                    }
                } else {
                    items(uiState.expenses, key = { it.id }) { expense ->
                        ExpenseCard(
                            expense = expense,
                            onDelete = { viewModel.deleteExpense(expense) },
                            onEdit = {
                                navController.navigate(Screen.EditExpense.createRoute(expense.carId, expense.id))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        ExpenseFilterDialog(
            currentFilter = uiState.currentFilter,
            availableTags = uiState.availableTags,
            onFilterApplied = { filter ->
                viewModel.applyFilter(filter)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun CarInfoCard(uiState: CarDetailUiState) {
    val car = uiState.car ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${car.year} • ${car.licensePlate}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${car.currentOdometer} км",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Icon(
                Icons.Default.DirectionsCar,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StatisticsCard(uiState: CarDetailUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Статистика",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Всего расходов",
                    value = formatCurrency(uiState.totalExpenses),
                    icon = Icons.Default.AttachMoney
                )
                StatItem(
                    label = "За месяц",
                    value = formatCurrency(uiState.monthlyExpenses),
                    icon = Icons.Default.CalendarMonth
                )
                StatItem(
                    label = "Записей",
                    value = uiState.expenseCount.toString(),
                    icon = Icons.Default.Receipt
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCard(
    expense: Expense,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    getCategoryIcon(expense.category),
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = getCategoryName(expense.category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDate(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expense.description?.isNotBlank() == true) {
                        Text(
                            text = expense.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${expense.odometer} км",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Меню")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить расход?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun EmptyExpensesState(isFiltered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = if (isFiltered) Icons.Default.FilterAltOff else Icons.Default.Receipt
        val title = if (isFiltered) "Ничего не найдено" else "Нет расходов"
        val subtitle = if (isFiltered) "Попробуйте изменить или сбросить фильтры" else "Добавьте первую запись"

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getCategoryIcon(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
    ExpenseCategory.REPAIR -> Icons.Default.CarRepair
    ExpenseCategory.INSURANCE -> Icons.Default.Shield
    ExpenseCategory.TAX -> Icons.Default.Receipt
    ExpenseCategory.PARKING -> Icons.Default.LocalParking
    ExpenseCategory.TOLL -> Icons.Default.Toll
    ExpenseCategory.WASH -> Icons.Default.LocalCarWash
    ExpenseCategory.FINE -> Icons.Default.Warning
    ExpenseCategory.ACCESSORIES -> Icons.Default.ShoppingCart
    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
}

fun getCategoryName(category: ExpenseCategory) = when (category) {
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
    ExpenseCategory.OTHER -> "Прочее"
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
    return sdf.format(Date(timestamp))
}

fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(amount)
}