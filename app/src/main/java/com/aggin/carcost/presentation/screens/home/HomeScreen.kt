package com.aggin.carcost.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
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
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CarCost") },
                actions = {
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.cars.isEmpty()) {
            EmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            CarsList(
                cars = uiState.cars,
                reminders = uiState.remindersByCarId,
                modifier = Modifier.padding(paddingValues),
                onCarClick = { car ->
                    navController.navigate(Screen.CarDetail.createRoute(car.id))
                }
            )
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
    reminders: Map<String, List<MaintenanceReminder>>, // ✅ String UUID
    modifier: Modifier = Modifier,
    onCarClick: (Car) -> Unit
) {
    var expandedCarId by remember { mutableStateOf<String?>(null) } // ✅ String UUID

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
                }
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
    onExpandToggle: () -> Unit
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
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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