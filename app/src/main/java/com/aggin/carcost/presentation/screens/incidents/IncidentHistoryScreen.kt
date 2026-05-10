package com.aggin.carcost.presentation.screens.incidents

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.IncidentType
import com.aggin.carcost.presentation.components.SkeletonCardList
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentHistoryScreen(
    carId: String,
    navController: NavController,
    viewModel: IncidentHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История инцидентов") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, "Добавить инцидент")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            SkeletonCardList(count = 4, cardHeight = 110.dp)
        } else if (uiState.incidents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛡️", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Нет инцидентов",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Нажмите + чтобы добавить",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.incidents, key = { it.id }) { incident ->
                    IncidentCard(
                        incident = incident,
                        dateFormat = dateFormat,
                        onEdit = { viewModel.showEditDialog(incident) },
                        onDelete = { viewModel.showDeleteConfirm(incident) }
                    )
                }
            }
        }
    }

    // Add/Edit dialog
    if (uiState.showAddDialog) {
        IncidentFormDialog(
            uiState = uiState,
            dateFormat = dateFormat,
            onDismiss = { viewModel.hideDialog() },
            onSave = { viewModel.saveIncident() },
            onUpdateDate = { viewModel.updateFormDate(it) },
            onUpdateType = { viewModel.updateFormType(it) },
            onUpdateDescription = { viewModel.updateFormDescription(it) },
            onUpdateDamageAmount = { viewModel.updateFormDamageAmount(it) },
            onUpdateRepairCost = { viewModel.updateFormRepairCost(it) },
            onUpdateRepairDate = { viewModel.updateFormRepairDate(it) },
            onUpdateLocation = { viewModel.updateFormLocation(it) },
            onUpdateInsuranceClaim = { viewModel.updateFormInsuranceClaim(it) },
            onUpdateNotes = { viewModel.updateFormNotes(it) },
            onUploadPhoto = { viewModel.uploadPhoto(it) }
        )
    }

    // Delete confirmation
    uiState.showDeleteDialog?.let { incident ->
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("Удалить инцидент?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteIncident(incident) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun IncidentCard(
    incident: CarIncident,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(incident.type.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            incident.type.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            dateFormat.format(Date(incident.date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Редактировать", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Удалить", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(incident.description, style = MaterialTheme.typography.bodyMedium)

            if (incident.location != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("📍 ${incident.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (incident.damageAmount != null || incident.repairCost != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (incident.damageAmount != null) {
                        Column {
                            Text("Ущерб", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.0f ₽".format(incident.damageAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (incident.repairCost != null) {
                        Column {
                            Text("Ремонт", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.0f ₽".format(incident.repairCost), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (incident.insuranceClaimNumber != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Страховой номер: ${incident.insuranceClaimNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (incident.photoUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = incident.photoUri,
                    contentDescription = "Фото инцидента",
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun IncidentFormDialog(
    uiState: IncidentHistoryUiState,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdateDate: (Long) -> Unit,
    onUpdateType: (IncidentType) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onUpdateDamageAmount: (String) -> Unit,
    onUpdateRepairCost: (String) -> Unit,
    onUpdateRepairDate: (Long?) -> Unit,
    onUpdateLocation: (String) -> Unit,
    onUpdateInsuranceClaim: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onUploadPhoto: (Uri) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showRepairDatePicker by remember { mutableStateOf(false) }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onUploadPhoto(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uiState.editingIncident != null) "Редактировать инцидент" else "Добавить инцидент") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.showError) {
                    Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Дата инцидента
                OutlinedTextField(
                    value = dateFormat.format(Date(uiState.formDate)),
                    onValueChange = {},
                    label = { Text("Дата *") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, "Выбрать дату")
                        }
                    }
                )

                // Тип инцидента
                Text("Тип инцидента", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(IncidentType.values()) { type ->
                        FilterChip(
                            selected = uiState.formType == type,
                            onClick = { onUpdateType(type) },
                            label = { Text("${type.emoji} ${type.displayName}") }
                        )
                    }
                }

                // Описание
                OutlinedTextField(
                    value = uiState.formDescription,
                    onValueChange = onUpdateDescription,
                    label = { Text("Описание *") },
                    placeholder = { Text("Что произошло?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !uiState.isSaving
                )

                // Место
                OutlinedTextField(
                    value = uiState.formLocation,
                    onValueChange = onUpdateLocation,
                    label = { Text("Место") },
                    placeholder = { Text("ул. Ленина, 5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.formDamageAmount,
                        onValueChange = onUpdateDamageAmount,
                        label = { Text("Ущерб (₽)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !uiState.isSaving
                    )
                    OutlinedTextField(
                        value = uiState.formRepairCost,
                        onValueChange = onUpdateRepairCost,
                        label = { Text("Ремонт (₽)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !uiState.isSaving
                    )
                }

                OutlinedTextField(
                    value = uiState.formRepairDate?.let { dateFormat.format(Date(it)) } ?: "",
                    onValueChange = {},
                    label = { Text("Дата ремонта") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (uiState.formRepairDate != null) {
                                IconButton(onClick = { onUpdateRepairDate(null) }) {
                                    Icon(Icons.Default.Clear, "Очистить")
                                }
                            }
                            IconButton(onClick = { showRepairDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, "Выбрать дату")
                            }
                        }
                    }
                )

                OutlinedTextField(
                    value = uiState.formInsuranceClaim,
                    onValueChange = onUpdateInsuranceClaim,
                    label = { Text("Номер страховой выплаты") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )

                OutlinedTextField(
                    value = uiState.formNotes,
                    onValueChange = onUpdateNotes,
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !uiState.isSaving
                )

                // Фото
                if (uiState.formPhotoUri != null) {
                    AsyncImage(
                        model = uiState.formPhotoUri,
                        contentDescription = "Фото",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (mediaPermission.status.isGranted) {
                            galleryLauncher.launch("image/*")
                        } else {
                            mediaPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isUploadingPhoto && !uiState.isSaving
                ) {
                    if (uiState.isUploadingPhoto) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузка...")
                    } else {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.formPhotoUri != null) "Заменить фото" else "Прикрепить фото")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !uiState.isSaving) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiState.formDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onUpdateDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = state) }
    }

    if (showRepairDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiState.formRepairDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showRepairDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onUpdateRepairDate(it) }
                    showRepairDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRepairDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = state) }
    }
}
