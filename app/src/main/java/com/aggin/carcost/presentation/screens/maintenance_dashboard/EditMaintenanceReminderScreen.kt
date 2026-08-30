package com.aggin.carcost.presentation.screens.maintenance_dashboard

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
import androidx.compose.runtime.saveable.rememberSaveable

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

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var carExpanded by rememberSaveable { mutableStateOf(false) }
    var typeExpanded by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog && reminderId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.maintenancedashboard_udalit_napominanie)) },
            text = { Text(stringResource(R.string.cardetail_eto_deystvie_nelzya_otmenit)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReminder(reminderId) { navController.popBackStack() }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) stringResource(R.string.maintenancedashboard_redaktirovat_to) else stringResource(R.string.maintenancedashboard_novoe_napominanie_to)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.isEditMode && reminderId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.action_delete),
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
                .imePadding()
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
                        ?.let { "${it.brand} ${it.model}" } ?: stringResource(R.string.maintenancedashboard_vyberite_avtomobil),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.maintenancedashboard_avtomobil)) },
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
                    value = stringResource(uiState.selectedType.displayNameRes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.addexp_tip_obsluzhivaniya)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    // Список зависит от выбранной машины: электромобилю не
                    // предлагаем масло, свечи и ремень ГРМ, зато предлагаем
                    // редуктор и охлаждение батареи
                    val selectedCar = uiState.cars.firstOrNull { it.id == uiState.selectedCarId }
                    val carFuelType = selectedCar?.fuelType
                        ?: com.aggin.carcost.data.local.database.entities.FuelType.GASOLINE
                    val carVehicleType = selectedCar?.vehicleType
                        ?: com.aggin.carcost.data.local.database.entities.VehicleType.CAR
                    com.aggin.carcost.data.local.database.entities
                        .maintenanceTypesFor(carFuelType, carVehicleType).forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(type.displayNameRes))
                                    Text(
                                        stringResource(R.string.maintenancedashboard_interval_po_umolchaniyu_km, type.defaultInterval),
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
                label = { Text(stringResource(R.string.maintenancedashboard_odometr_pri_posledney_zamene_km)) },
                placeholder = { Text(stringResource(R.string.maintenancedashboard_naprimer_85000)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Интервал замены ───────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.intervalKm,
                onValueChange = viewModel::updateIntervalKm,
                label = { Text(stringResource(R.string.maintenancedashboard_interval_zameny_km)) },
                placeholder = { Text(stringResource(R.string.maintenancedashboard_naprimer, uiState.selectedType.defaultInterval)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        stringResource(R.string.maintenancedashboard_po_umolchaniyu_dlya_km, stringResource(uiState.selectedType.displayNameRes), uiState.selectedType.defaultInterval),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            // ── Интервал по дням (опционально) ───────────────────────────────
            OutlinedTextField(
                value = uiState.intervalDays,
                onValueChange = viewModel::updateIntervalDays,
                label = { Text(stringResource(R.string.maintenancedashboard_interval_po_vremeni_dney_neobyazatelno)) },
                placeholder = { Text(stringResource(R.string.maintenancedashboard_naprimer_180_polgoda)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.maintenancedashboard_napominanie_srabotaet_ranshe_po_probegu)) }
            )

            // ── Дата следующего ТО ────────────────────────────────────────────
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            OutlinedTextField(
                value = uiState.nextChangeDateMs?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.maintenancedashboard_data_sleduyuschego_to_neobyazatelno)) },
                placeholder = { Text(stringResource(R.string.maintenancedashboard_nazhmite_dlya_vybora_daty)) },
                trailingIcon = {
                    Row {
                        if (uiState.nextChangeDateMs != null) {
                            IconButton(onClick = { viewModel.updateNextChangeDateMs(null) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.maintenancedashboard_sbrosit_datu), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, stringResource(R.string.addcar_vybrat_datu))
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
                        }) { Text(stringResource(R.string.maintenancedashboard_vybrat)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
                            stringResource(R.string.maintenancedashboard_sleduyuschee_to),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            stringResource(R.string.home_km, uiState.nextChangeOdometer),
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
                label = { Text(stringResource(R.string.documents_zametki_neobyazatelno)) },
                placeholder = { Text(stringResource(R.string.maintenancedashboard_naprimer_ispolzovat_sintetiku_5w_40)) },
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
                    Text(if (uiState.isEditMode) stringResource(R.string.editexp_sohranit_izmeneniya) else stringResource(R.string.maintenancedashboard_dobavit_napominanie))
                }
            }
        }
    }
}
