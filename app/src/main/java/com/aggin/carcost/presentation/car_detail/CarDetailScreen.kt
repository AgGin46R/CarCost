package com.aggin.carcost.presentation.screens.car_detail

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.aggin.carcost.presentation.common.icon
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.formatDateShort
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    carId: String,
    navController: NavController,
    viewModel: CarDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Строка берётся здесь: внутри onClick контекста Composable уже нет
    val passportBuilding = stringResource(R.string.passport_creating)

    /** Расход, по которому нажали: показываем карточку с подробностями и чеком */
    var selectedExpense by remember {
        mutableStateOf<com.aggin.carcost.data.local.database.entities.Expense?>(null)
    }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.car?.let { "${it.brand} ${it.model}" } ?: stringResource(R.string.cardetail_zagruzka),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Chat moved here for quick access — Planned Purchases moved to overflow menu
                    IconButton(onClick = { navController.navigateOnce(Screen.Chat.createRoute(carId)) }) {
                        Icon(Icons.Default.Chat, stringResource(R.string.cardetail_chat))
                    }
                    IconButton(onClick = { navController.navigateOnce(Screen.Map.createRoute(carId)) }) {
                        Icon(Icons.Default.Map, stringResource(R.string.cardetail_karta))
                    }
                    IconButton(onClick = { navController.navigateOnce(Screen.Analytics.createRoute(carId)) }) {
                        Icon(Icons.Default.Assessment, stringResource(R.string.cardetail_analitika))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.cardetail_menyu))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Only owners can edit car data
                            if (uiState.isOwner) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cardetail_redaktirovat)) },
                                    onClick = {
                                        showMenu = false
                                        navController.navigateOnce(Screen.EditCar.createRoute(carId))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_plany_pokupok)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.PlannedExpenses.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Assignment, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_dokumenty_i_strahovki)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Documents.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_byudzhet)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Budget.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_stoimost_vladeniya)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Tco.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.MonetizationOn, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_taymlayn_to)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.ServiceTimeline.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Timeline, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_poezdki_po_gps)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.GpsTrip.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Map, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_uchastniki)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.CarMembers.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Group, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_tseli_nakopleniya)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Goals.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Savings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_istoriya_intsidentov)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.IncidentHistory.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.Warning, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_urovni_zhidkostey)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.FluidLevels.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_shiny)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Tyres.createRoute(carId))
                                },
                                leadingIcon = { Icon(Icons.Default.DonutLarge, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.passport_export)) },
                                onClick = {
                                    showMenu = false
                                    android.widget.Toast.makeText(
                                        context,
                                        passportBuilding,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.buildVehiclePassport { error ->
                                        if (error != null) {
                                            android.widget.Toast.makeText(
                                                context, error, android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Description, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cardetail_eksport_dannyh)) },
                                onClick = {
                                    showMenu = false
                                    navController.navigateOnce(Screen.Export.createRoute(carId))
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
                        navController.navigateOnce(
                            Screen.AddExpense.createRoute(
                                carId,
                                category = ExpenseCategory.MAINTENANCE.name,
                                lockedCategory = true
                            )
                        )
                    } else {
                        navController.navigateOnce(Screen.AddExpense.createRoute(carId))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (isMechanic) Icons.Default.Build else Icons.Default.Add,
                    contentDescription = if (isMechanic) stringResource(R.string.cardetail_dobavit_to) else stringResource(R.string.cardetail_dobavit_rashod)
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
                            navController.navigateOnce(Screen.EditExpense.createRoute(expense.carId, expense.id))
                        },
                        // Нажатие открывает карточку, а не форму правки: чаще
                        // всего человек хочет посмотреть подробности и чек, а не
                        // менять запись
                        onClick = { selectedExpense = expense }
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
                                    stringResource(R.string.cardetail_esche, uiState.totalExpenseCount - uiState.expenses.size),
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

    // Карточка расхода по нажатию: подробности и прикреплённый чек
    selectedExpense?.let { expense ->
        ExpenseDetailSheet(
            expense = expense,
            tags = uiState.expensesWithTags[expense.id] ?: emptyList(),
            currency = uiState.car?.currency ?: "RUB",
            fuelConsumptionL100km = uiState.fuelConsumptionPerFill[expense.id],
            onEdit = {
                selectedExpense = null
                navController.navigateOnce(
                    Screen.EditExpense.createRoute(expense.carId, expense.id)
                )
            },
            onDismiss = { selectedExpense = null }
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
            contentDescription = stringResource(R.string.home_foto_avtomobilya),
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
                text = stringResource(R.string.cardetail_statistika),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.cardetail_vsego_rashodov),
                    value = formatCurrency(uiState.totalExpenses, uiState.car?.currency ?: "RUB"),
                    icon = Icons.Default.AttachMoney
                )
                StatItem(
                    label = stringResource(R.string.home_za_30_dney),
                    value = formatCurrency(uiState.monthlyExpenses, uiState.car?.currency ?: "RUB"),
                    icon = Icons.Default.CalendarMonth
                )
                StatItem(
                    label = stringResource(R.string.cardetail_zapisey),
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
                // Год ПОКУПКИ, а не год выпуска. Раньше сюда передавался car.year,
                // и амортизация применялась дважды: цена подержанной машины уже
                // включает просадку с завода, а модель просаживала её ещё раз от нуля.
                // Машина 2015 года, купленная в 2024 за 800 000, оценивалась в ~199 000.
                val purchaseYear = java.util.Calendar.getInstance()
                    .apply { timeInMillis = car.purchaseDate }
                    .get(java.util.Calendar.YEAR)
                val currentValue = CarValueEstimator.estimateCurrentValue(price, purchaseYear)
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
                        stringResource(R.string.cardetail_otsenochnaya_stoimost),
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
                            stringResource(R.string.cardetail_ot_pokupki, deprPct),
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
                    stringResource(R.string.cardetail_raschetnyy_ostatok_topliva),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                buildString {
                    append(stringResource(R.string.cardetail_l, liters.toInt()))
                    if (tankCapacity != null) append(stringResource(R.string.cardetail_l_2, tankCapacity.toInt()))
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
    onEdit: () -> Unit,
    /** Нажатие по карточке — открыть подробности расхода вместе с чеком */
    onClick: () -> Unit = {}
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
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
                .pointerInput(Unit) {
                    // Нажатие отдельным обработчиком, а не поверх перетаскивания:
                    // смахивание и касание не должны мешать друг другу
                    detectTapGestures(
                        onTap = {
                            if (offsetX.value == 0f) onClick()
                            else scope.launch { offsetX.animateTo(0f, animationSpec = tween(300)) }
                        }
                    )
                }
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
            title = { Text(stringResource(R.string.cardetail_udalit_rashod)) },
            text = { Text(stringResource(R.string.cardetail_eto_deystvie_nelzya_otmenit)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticLong()
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { hapticClick(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                        contentDescription = stringResource(R.string.cardetail_redaktirovat),
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
                        contentDescription = stringResource(R.string.action_delete),
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
                            text = stringResource(R.string.cardetail_1f_l_100km).format(fuelConsumptionL100km),
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
                            text = stringResource(R.string.cardetail_detali, expense.maintenanceParts),
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
                    text = stringResource(R.string.home_km, expense.odometer),
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
            text = stringResource(R.string.cardetail_rashody, expenseCount),
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
                    Text(stringResource(R.string.action_reset))
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
                Text(stringResource(R.string.action_filter), color = filterColor)
            }
        }
    }
}

@Composable
fun EmptyExpensesState(isFiltered: Boolean) {
    EmptyState(
        icon = Icons.Default.Receipt,
        title = if (isFiltered) stringResource(R.string.cardetail_net_rashodov_po_filtru) else stringResource(R.string.cardetail_rashodov_poka_net),
        subtitle = if (isFiltered)
            stringResource(R.string.cardetail_poprobuyte_izmenit_filtry_ili_vybrat)
        else
            stringResource(R.string.cardetail_dobavte_pervyy_rashod_nazhav_knopku_vnizu)
    )
}

fun getCategoryIcon(category: ExpenseCategory) = category.icon()

@Composable
fun getCategoryName(category: ExpenseCategory) = category.displayName()

fun formatDate(timestamp: Long): String = formatDateShort(timestamp)

fun formatCurrency(amount: Double, currency: String = "RUB"): String {
    val symbol = com.aggin.carcost.util.CurrencyUtils.symbol(currency)
    return "%.0f %s".format(amount, symbol)
}

@Composable
private fun QuickAddRow(carId: String, navController: NavController, isMechanic: Boolean = false) {
    val allChips = listOf(
        Triple(stringResource(R.string.cardetail_toplivo), ExpenseCategory.FUEL, false),
        Triple(stringResource(R.string.cardetail_to), ExpenseCategory.MAINTENANCE, true),
        Triple(stringResource(R.string.cardetail_moyka), ExpenseCategory.WASH, false),
        Triple(stringResource(R.string.cardetail_parkovka), ExpenseCategory.PARKING, false)
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
                    navController.navigateOnce(
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
        score.total >= 85 -> stringResource(R.string.cardetail_otlichnoe)        to colorScheme.tertiary
        score.total >= 65 -> stringResource(R.string.cardetail_horoshee)         to colorScheme.primary
        score.total >= 40 -> stringResource(R.string.cardetail_srednee)         to colorScheme.error.copy(alpha = 0.75f)
        else              -> stringResource(R.string.cardetail_trebuet_vnimaniya) to colorScheme.error
    }
    var expanded by rememberSaveable { mutableStateOf(false) }

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
                        stringResource(R.string.cardetail_sostoyanie_avto),
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
                            HealthChip(stringResource(R.string.cardetail_to_2, score.overdueReminders), danger = true)
                        }
                        if (!score.activeInsurance) {
                            HealthChip(stringResource(R.string.cardetail_net_osago), danger = true)
                        }
                        if (score.recentIncidents > 0) {
                            HealthChip(stringResource(R.string.cardetail_dtp, score.recentIncidents), danger = true)
                        }
                        if (score.overdueReminders == 0 && score.activeInsurance && score.recentIncidents == 0) {
                            HealthChip(stringResource(R.string.cardetail_vse_v_poryadke), danger = false)
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.cardetail_svernut) else stringResource(R.string.cardetail_podrobnee)
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