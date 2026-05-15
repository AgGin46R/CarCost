package com.aggin.carcost.presentation.screens.car_detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.presentation.navigation.Screen
import com.aggin.carcost.presentation.components.EmptyState
import com.aggin.carcost.presentation.components.ExpenseFilterDialog
import com.aggin.carcost.presentation.components.OfflineBanner
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aggin.carcost.domain.tco.CarValueEstimator
import com.aggin.carcost.util.rememberHapticClick
import com.aggin.carcost.util.rememberHapticLongPress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    carId: String,
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
                    Text(
                        text = uiState.car?.let { "${it.brand} ${it.model}" } ?: "Загрузка...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Chat moved here for quick access — Planned Purchases moved to overflow menu
                    IconButton(onClick = { navController.navigate(Screen.Chat.createRoute(carId)) }) {
                        Icon(Icons.Default.Chat, "Чат")
                    }
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
                            // Only owners can edit car data
                            if (uiState.isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    onClick = {
                                        showMenu = false
                                        navController.navigate(Screen.EditCar.createRoute(carId))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Планы покупок") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.PlannedExpenses.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Assignment, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Документы и страховки") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Documents.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Бюджет") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Budget.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Стоимость владения") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Tco.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.MonetizationOn, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Таймлайн ТО") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.ServiceTimeline.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Timeline, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("GPS Поездка") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.GpsTrip.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Map, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Участники") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.CarMembers.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Group, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Цели накопления") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Goals.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Savings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("История инцидентов") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.IncidentHistory.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Warning, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Уровни жидкостей") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.FluidLevels.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт данных") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Export.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Download, null) }
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
            val isMechanic = uiState.userRole == MemberRole.MECHANIC
            FloatingActionButton(
                onClick = {
                    if (isMechanic) {
                        // Механик может добавлять только расходы на ТО, категория зафиксирована
                        navController.navigate(
                            Screen.AddExpense.createRoute(
                                carId,
                                category = ExpenseCategory.MAINTENANCE.name,
                                lockedCategory = true
                            )
                        )
                    } else {
                        navController.navigate(Screen.AddExpense.createRoute(carId))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (isMechanic) Icons.Default.Build else Icons.Default.Add,
                    contentDescription = if (isMechanic) "Добавить ТО" else "Добавить расход"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OfflineBanner()
            if (uiState.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val listState = rememberLazyListState()

            // Автоподгрузка: когда пользователь дошёл до последних 5 элементов
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = listState.layoutInfo.totalItemsCount
                    uiState.hasMoreExpenses && total > 0 && lastVisible >= total - 5
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMoreExpenses()
            }

            LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CarInfoCard(uiState = uiState)
            }

            uiState.healthScore?.let { score ->
                item { CarHealthCard(score = score) }
            }

            item {
                ExpensesHeader(
                    expenseCount = uiState.totalExpenseCount,
                    isFilterActive = uiState.currentFilter.isActive(),
                    onFilterClick = { showFilterDialog = true },
                    onClearFilter = { viewModel.clearFilter() }
                )
            }

            item {
                QuickAddRow(
                    carId = carId,
                    navController = navController,
                    isMechanic = uiState.userRole == MemberRole.MECHANIC
                )
            }

            if (uiState.expenses.isEmpty()) {
                item {
                    EmptyExpensesState(isFiltered = uiState.currentFilter.isActive())
                }
            } else {
                val isMechanic = uiState.userRole == MemberRole.MECHANIC
                items(uiState.expenses, key = { it.id }) { expense ->
                    // Механик может редактировать/удалять только расходы ТО
                    val canEditDelete = !isMechanic || expense.category == ExpenseCategory.MAINTENANCE
                    SwipeableExpenseCard(
                        expense = expense,
                        tags = uiState.expensesWithTags[expense.id] ?: emptyList(),
                        fuelConsumptionL100km = uiState.fuelConsumptionPerFill[expense.id],
                        currency = uiState.car?.currency ?: "RUB",
                        canEditDelete = canEditDelete,
                        onDelete = { viewModel.deleteExpense(expense) },
                        onEdit = {
                            navController.navigate(Screen.EditExpense.createRoute(expense.carId, expense.id))
                        }
                    )
                }
                // Индикатор подгрузки + счётчик скрытых записей
                if (uiState.hasMoreExpenses) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    "Ещё ${uiState.totalExpenseCount - uiState.expenses.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end Column
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

    // Car photo banner (shown above the card if photo exists)
    car.photoUri?.let { photoUrl ->
        AsyncImage(
            model = photoUrl,
            contentDescription = "Фото автомобиля",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
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
                    value = formatCurrency(uiState.totalExpenses, uiState.car?.currency ?: "RUB"),
                    icon = Icons.Default.AttachMoney
                )
                StatItem(
                    label = "За месяц",
                    value = formatCurrency(uiState.monthlyExpenses, uiState.car?.currency ?: "RUB"),
                    icon = Icons.Default.CalendarMonth
                )
                StatItem(
                    label = "Записей",
                    value = uiState.expenseCount.toString(),
                    icon = Icons.Default.Receipt
                )
            }

            // Индикатор топлива (только если есть данные)
            if (uiState.fuelLevelPct != null && uiState.estimatedFuelLiters != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                FuelLevelIndicator(
                    pct = uiState.fuelLevelPct,
                    liters = uiState.estimatedFuelLiters,
                    tankCapacity = car.tankCapacity
                )
            }

            // Оценочная стоимость (если есть цена покупки)
            car.purchasePrice?.let { price ->
                val currentValue = CarValueEstimator.estimateCurrentValue(price, car.year)
                val deprPct = CarValueEstimator.depreciationPercent(price, currentValue)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Оценочная стоимость",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "~${formatCurrency(currentValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "-$deprPct% от покупки",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelLevelIndicator(pct: Float, liters: Double, tankCapacity: Double?) {
    val color = when {
        pct < 0.15f -> MaterialTheme.colorScheme.error
        pct < 0.30f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⛽", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Расчётный остаток топлива",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                buildString {
                    append("~${liters.toInt()} л")
                    if (tankCapacity != null) append(" / ${tankCapacity.toInt()} л")
                    append("  (${(pct * 100).toInt()}%)")
                },
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
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

@Composable
fun SwipeableExpenseCard(
    expense: Expense,
    tags: List<ExpenseTag> = emptyList(),
    fuelConsumptionL100km: Double? = null,
    currency: String = "RUB",
    canEditDelete: Boolean = true,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hapticLong = rememberHapticLongPress()
    val hapticClick = rememberHapticClick()

    // Максимальный свайп (ширина кнопок)
    val maxSwipeDistance = with(density) { 120.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Фон с кнопками — только если разрешено редактирование/удаление
        if (canEditDelete) {
            SwipeBackground(
                onEdit = {
                    scope.launch {
                        offsetX.animateTo(0f, animationSpec = tween(300))
                    }
                    onEdit()
                },
                onDelete = {
                    showDeleteDialog = true
                }
            )
        }

        // Передний слой (карточка расхода)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(canEditDelete) {
                    if (!canEditDelete) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // Если свайп больше половины максимального, открываем
                                if (offsetX.value < -maxSwipeDistance / 2) {
                                    hapticLong()
                                    offsetX.animateTo(-maxSwipeDistance, animationSpec = tween(300))
                                } else {
                                    offsetX.animateTo(0f, animationSpec = tween(300))
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxSwipeDistance, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            ExpenseCardContent(expense = expense, tags = tags, fuelConsumptionL100km = fuelConsumptionL100km, currency = currency)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить расход?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticLong()
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { hapticClick(); showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SwipeBackground(
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка редактирования
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Кнопка удаления
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseCardContent(
    expense: Expense,
    tags: List<ExpenseTag> = emptyList(),
    fuelConsumptionL100km: Double? = null,
    currency: String = "RUB"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDate(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fuelConsumptionL100km != null) {
                        Text(
                            text = "%.1f л/100км".format(fuelConsumptionL100km),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    if (expense.description?.isNotBlank() == true) {
                        Text(
                            text = expense.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (expense.maintenanceParts?.isNotBlank() == true) {
                        Text(
                            text = "Детали: ${expense.maintenanceParts}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Отображение тегов
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tags.take(3).forEach { tag ->
                                TagChip(tag = tag)
                            }
                            if (tags.size > 3) {
                                Text(
                                    text = "+${tags.size - 3}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(expense.amount, currency),
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
        }
    }
}

@Composable
fun TagChip(tag: ExpenseTag) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = try {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(tag.color))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            text = tag.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}

@Composable
fun ExpensesHeader(
    expenseCount: Int,
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    onClearFilter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Расходы ($expenseCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isFilterActive) {
                TextButton(onClick = onClearFilter) {
                    Icon(
                        Icons.Default.FilterListOff,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сбросить")
                }
            }
            TextButton(onClick = onFilterClick) {
                val filterColor = if (isFilterActive) {
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

@Composable
fun EmptyExpensesState(isFiltered: Boolean) {
    EmptyState(
        icon = Icons.Default.Receipt,
        title = if (isFiltered) "Нет расходов по фильтру" else "Расходов пока нет",
        subtitle = if (isFiltered)
            "Попробуйте изменить фильтры или выбрать другой период"
        else
            "Добавьте первый расход, нажав кнопку + внизу экрана"
    )
}

fun getCategoryIcon(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
    ExpenseCategory.REPAIR -> Icons.Default.CarRepair
    ExpenseCategory.INSURANCE -> Icons.Default.Security
    ExpenseCategory.TAX -> Icons.Default.AttachMoney
    ExpenseCategory.PARKING -> Icons.Default.LocalParking
    ExpenseCategory.WASH -> Icons.Default.LocalCarWash
    ExpenseCategory.FINE -> Icons.Default.Warning
    ExpenseCategory.TOLL -> Icons.Default.Toll
    ExpenseCategory.ACCESSORIES -> Icons.Default.ShoppingCart
    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
}

fun getCategoryName(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FUEL -> "Топливо"
    ExpenseCategory.MAINTENANCE -> "Обслуживание"
    ExpenseCategory.REPAIR -> "Ремонт"
    ExpenseCategory.INSURANCE -> "Страховка"
    ExpenseCategory.TAX -> "Налог"
    ExpenseCategory.PARKING -> "Парковка"
    ExpenseCategory.WASH -> "Мойка"
    ExpenseCategory.FINE -> "Штраф"
    ExpenseCategory.TOLL -> "Платная дорога"
    ExpenseCategory.ACCESSORIES -> "Аксессуары"
    ExpenseCategory.OTHER -> "Другое"
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
    return sdf.format(java.util.Date(timestamp))
}

fun formatCurrency(amount: Double, currency: String = "RUB"): String {
    val symbol = com.aggin.carcost.util.CurrencyUtils.symbol(currency)
    return "%.0f %s".format(amount, symbol)
}

@Composable
private fun QuickAddRow(carId: String, navController: NavController, isMechanic: Boolean = false) {
    val allChips = listOf(
        Triple("⛽ Топливо", ExpenseCategory.FUEL, false),
        Triple("🔧 ТО", ExpenseCategory.MAINTENANCE, true),
        Triple("🚿 Мойка", ExpenseCategory.WASH, false),
        Triple("🅿️ Парковка", ExpenseCategory.PARKING, false)
    )
    // Механик видит только чип ТО
    val chips = if (isMechanic) allChips.filter { it.third } else allChips
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(chips.size) { i ->
            val (label, category, _) = chips[i]
            FilterChip(
                selected = false,
                onClick = {
                    navController.navigate(
                        Screen.AddExpense.createRoute(
                            carId,
                            category = category.name,
                            lockedCategory = isMechanic
                        )
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

// ── Car Health Score card ────────────────────────────────────────────────────

@Composable
fun CarHealthCard(score: com.aggin.carcost.domain.health.CarHealthScore) {
    val colorScheme = MaterialTheme.colorScheme
    val (label, color) = when {
        score.total >= 85 -> "Отличное"        to colorScheme.tertiary
        score.total >= 65 -> "Хорошее"         to colorScheme.primary
        score.total >= 40 -> "Среднее"         to colorScheme.error.copy(alpha = 0.75f)
        else              -> "Требует внимания" to colorScheme.error
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular indicator
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        strokeWidth = 7.dp,
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                    CircularProgressIndicator(
                        progress = { score.total / 100f },
                        modifier = Modifier.size(72.dp),
                        color = color,
                        strokeWidth = 7.dp,
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                    Text(
                        "${score.total}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Состояние авто",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(Modifier.height(4.dp))
                    // Inline chips: overdue / insurance / incidents
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (score.overdueReminders > 0) {
                            HealthChip("⚠ ТО: ${score.overdueReminders}", danger = true)
                        }
                        if (!score.activeInsurance) {
                            HealthChip("Нет ОСАГО", danger = true)
                        }
                        if (score.recentIncidents > 0) {
                            HealthChip("ДТП: ${score.recentIncidents}", danger = true)
                        }
                        if (score.overdueReminders == 0 && score.activeInsurance && score.recentIncidents == 0) {
                            HealthChip("Всё в порядке", danger = false)
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Подробнее"
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                score.breakdown.forEach { factor ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            factor.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        val sign = if (factor.delta > 0) "+" else ""
                        Text(
                            "$sign${factor.delta}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (factor.positive)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthChip(text: String, danger: Boolean) {
    val bg = if (danger) MaterialTheme.colorScheme.errorContainer
             else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (danger) MaterialTheme.colorScheme.onErrorContainer
             else MaterialTheme.colorScheme.onTertiaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}