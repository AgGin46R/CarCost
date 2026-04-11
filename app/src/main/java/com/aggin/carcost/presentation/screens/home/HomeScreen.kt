package com.aggin.carcost.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.remote.repository.CarInvitationDto
import com.aggin.carcost.presentation.components.OfflineBanner
import com.aggin.carcost.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullRefreshState = rememberPullToRefreshState()

    // Показываем Snackbar при ошибке синхронизации
    LaunchedEffect(uiState.syncError) {
        uiState.syncError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Повторить",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.forceRefresh()
            }
            viewModel.clearSyncError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CarCost") },
                actions = {
                    // Таймер парковки
                    IconButton(onClick = { navController.navigate(Screen.ParkingTimer.route) }) {
                        Icon(Icons.Default.LocalParking, contentDescription = "Таймер парковки")
                    }
                    // Цены топлива
                    IconButton(onClick = { navController.navigate(Screen.FuelPrices.route) }) {
                        Icon(Icons.Default.LocalGasStation, contentDescription = "Цены топлива")
                    }
                    // Дашборд ТО
                    IconButton(onClick = { navController.navigate(Screen.MaintenanceDashboard.route) }) {
                        Icon(Icons.Default.Build, contentDescription = "Дашборд ТО")
                    }
                    // Кнопка сравнения (только если авто >= 2)
                    if (uiState.cars.size >= 2) {
                        IconButton(onClick = { navController.navigate(Screen.Compare.route) }) {
                            Icon(Icons.Default.CompareArrows, contentDescription = "Сравнить авто")
                        }
                    }
                    // Кнопка профиля
                    IconButton(
                        onClick = { navController.navigate(Screen.Profile.route) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Профиль"
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
                onClick = { navController.navigate(Screen.AddCar.route) },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить автомобиль")
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = { viewModel.forceRefresh() },
            state = pullRefreshState,
            modifier = Modifier.padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.cars.isEmpty() && uiState.pendingInvitations.isEmpty()) {
                EmptyState()
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Индикатор фоновой синхронизации
                    if (uiState.isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    // Баннер отсутствия сети
                    OfflineBanner()
                    // Incoming invitations banner
                    uiState.pendingInvitations.forEach { inv ->
                        InvitationBanner(
                            invitation = inv,
                            onAccept = {
                                navController.navigate(Screen.AcceptInvite.createRoute(inv.token))
                                viewModel.dismissInvitation(inv.token)
                            },
                            onDismiss = { viewModel.dismissInvitation(inv.token) }
                        )
                    }
                    if (uiState.cars.isNotEmpty()) {
                        CarsList(
                            cars = uiState.cars,
                            reminders = uiState.remindersByCarId,
                            modifier = Modifier.weight(1f),
                            onCarClick = { car ->
                                navController.navigate(Screen.CarDetail.createRoute(car.id))
                            },
                            monthlyExpensePerCar = uiState.monthlyExpensePerCar
                        )
                    } else {
                        EmptyState(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationBanner(
    invitation: CarInvitationDto,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.PersonAdd, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Вас пригласили в авто",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Роль: ${
                        when (invitation.role) {
                            "DRIVER" -> "Водитель"
                            "MECHANIC" -> "Механик"
                            else -> invitation.role
                        }
                    }",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onDismiss) { Text("Позже") }
            Button(onClick = onAccept) { Text("Принять") }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Нет автомобилей",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте свой первый автомобиль",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun CarsList(
    cars: List<Car>,
    reminders: Map<String, List<MaintenanceReminder>>,
    modifier: Modifier = Modifier,
    onCarClick: (Car) -> Unit,
    monthlyExpensePerCar: Map<String, Double> = emptyMap()
) {
    var expandedCarId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cars) { car ->
            CarCard(
                car = car,
                reminders = reminders[car.id] ?: emptyList(),
                isExpanded = expandedCarId == car.id,
                onClick = { onCarClick(car) },
                onExpandToggle = {
                    expandedCarId = if (expandedCarId == car.id) null else car.id
                },
                monthlyExpense = monthlyExpensePerCar[car.id]
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarCard(
    car: Car,
    reminders: List<MaintenanceReminder>,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onExpandToggle: () -> Unit,
    monthlyExpense: Double? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${car.brand} ${car.model}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${car.year} • ${car.licensePlate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (car.photoUri != null) {
                    AsyncImage(
                        model = car.photoUri,
                        contentDescription = "Фото автомобиля",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    label = "Пробег",
                    value = "${car.currentOdometer} км"
                )
                InfoChip(
                    label = "Топливо",
                    value = when(car.fuelType) {
                        FuelType.GASOLINE -> "Бензин"
                        FuelType.DIESEL -> "Дизель"
                        FuelType.ELECTRIC -> "Электро"
                        FuelType.HYBRID -> "Гибрид"
                        FuelType.GAS -> "Газ"
                        else -> "Другое"
                    }
                )
                InfoChip(
                    label = "За месяц",
                    value = if (monthlyExpense != null && monthlyExpense > 0)
                        "${"%.0f".format(monthlyExpense)} ₽"
                    else "—"
                )
            }

            // Кнопка раскрытия напоминаний
            if (reminders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isExpanded) "Скрыть напоминания" else "Напоминания о ТО (${reminders.size})"
                    )
                }

                // Раскрывающиеся напоминания
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reminders.forEach { reminder ->
                            MaintenanceReminderCard(
                                reminder = reminder,
                                currentOdometer = car.currentOdometer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaintenanceReminderCard(
    reminder: MaintenanceReminder,
    currentOdometer: Int
) {
    val remainingKm = reminder.nextChangeOdometer - currentOdometer
    val isUrgent = remainingKm <= 500
    val isWarning = remainingKm <= 1000

    val cardColor = when {
        isUrgent -> MaterialTheme.colorScheme.errorContainer
        isWarning -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isUrgent -> MaterialTheme.colorScheme.onErrorContainer
        isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isUrgent -> Icons.Default.Warning
                        isWarning -> Icons.Default.Info
                        else -> Icons.Default.Build
                    },
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = reminder.type.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Text(
                        text = if (remainingKm > 0) {
                            "через $remainingKm км"
                        } else {
                            "ПРОСРОЧЕНО на ${-remainingKm} км!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }

            // Прогресс
            val progress = if (reminder.intervalKm > 0) {
                ((currentOdometer - reminder.lastChangeOdometer).toFloat() / reminder.intervalKm).coerceIn(0f, 1f)
            } else 0f

            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(32.dp),
                color = textColor,
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}