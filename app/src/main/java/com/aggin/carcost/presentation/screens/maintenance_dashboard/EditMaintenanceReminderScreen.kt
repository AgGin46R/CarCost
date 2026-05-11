package com.aggin.carcost.presentation.screens.maintenance_dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMaintenanceReminderScreen(
    navController: NavController,
    preselectedCarId: String? = null,
    reminderId: String? = null,
    viewModel: EditMaintenanceReminderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(preselectedCarId) {
        viewModel.initForCreate(preselectedCarId)
    }
    LaunchedEffect(reminderId) {
        if (reminderId != null) viewModel.loadReminder(reminderId)
    }
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) navController.popBackStack()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var carExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    if (showDeleteDialog && reminderId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить напоминание?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReminder(reminderId) { navController.popBackStack() }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Редактировать ТО" else "Новое напоминание ТО") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (uiState.isEditMode && reminderId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                "Удалить",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Выбор автомобиля ─────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = carExpanded,
                onExpandedChange = { carExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.cars.firstOrNull { it.id == uiState.selectedCarId }
                        ?.let { "${it.brand} ${it.model}" } ?: "Выберите автомобиль",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Автомобиль") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = carExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = carExpanded,
                    onDismissRequest = { carExpanded = false }
                ) {
                    uiState.cars.forEach { car ->
                        DropdownMenuItem(
                            text = { Text("${car.brand} ${car.model} · ${car.licensePlate}") },
                            onClick = {
                                viewModel.updateCar(car.id)
                                carExpanded = false
                            }
                        )
                    }
                }
            }

            // ── Тип ТО ───────────────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип обслуживания") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    MaintenanceType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(type.displayName)
                                    Text(
                                        "Интервал по умолчанию: ${type.defaultInterval} км",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateType(type)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            // ── Последняя замена (одометр) ────────────────────────────────────
            OutlinedTextField(
                value = uiState.lastChangeOdometer,
                onValueChange = viewModel::updateLastOdometer,
                label = { Text("Одометр при последней замене (км)") },
                placeholder = { Text("например, 85000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Интервал замены ───────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.intervalKm,
                onValueChange = viewModel::updateIntervalKm,
                label = { Text("Интервал замены (км)") },
                placeholder = { Text("например, ${uiState.selectedType.defaultInterval}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        "По умолчанию для ${uiState.selectedType.displayName}: ${uiState.selectedType.defaultInterval} км",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            // ── Интервал по дням (опционально) ───────────────────────────────
            OutlinedTextField(
                value = uiState.intervalDays,
                onValueChange = viewModel::updateIntervalDays,
                label = { Text("Интервал по времени (дней, необязательно)") },
                placeholder = { Text("например, 180 (полгода)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Напоминание сработает раньше — по пробегу или по дате") }
            )

            // ── Дата следующего ТО ────────────────────────────────────────────
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            OutlinedTextField(
                value = uiState.nextChangeDateMs?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Дата следующего ТО (необязательно)") },
                placeholder = { Text("Нажмите для выбора даты") },
                trailingIcon = {
                    Row {
                        if (uiState.nextChangeDateMs != null) {
                            IconButton(onClick = { viewModel.updateNextChangeDateMs(null) }) {
                                Icon(Icons.Default.Delete, "Сбросить дату", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, "Выбрать дату")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Date picker dialog
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = uiState.nextChangeDateMs
                        ?: (System.currentTimeMillis() + 90L * 24 * 3600 * 1000)
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { viewModel.updateNextChangeDateMs(it) }
                            showDatePicker = false
                        }) { Text("Выбрать") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // ── Предпросмотр следующего ТО ────────────────────────────────────
            if (uiState.lastChangeOdometer.isNotBlank() && uiState.intervalKm.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Следующее ТО:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "${uiState.nextChangeOdometer} км",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ── Заметки ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Заметки (необязательно)") },
                placeholder = { Text("Например, использовать синтетику 5W-40") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // ── Ошибка ────────────────────────────────────────────────────────
            uiState.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Кнопка сохранения ─────────────────────────────────────────────
            Button(
                onClick = viewModel::save,
                enabled = uiState.canSave && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (uiState.isEditMode) "Сохранить изменения" else "Добавить напоминание")
                }
            }
        }
    }
}
