package com.aggin.carcost.presentation.screens.add_car

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddCarScreen(
    navController: NavController,
    viewModel: AddCarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateCarPhoto(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить автомобиль") },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ошибка
            if (uiState.showError) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Фото автомобиля
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.photoUri != null) {
                    AsyncImage(
                        model = uiState.photoUri,
                        contentDescription = "Фото автомобиля",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "Нажмите камеру для фото",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                SmallFloatingActionButton(
                    onClick = {
                        if (!uiState.isUploadingPhoto) {
                            if (mediaPermission.status.isGranted) {
                                galleryLauncher.launch("image/*")
                            } else {
                                mediaPermission.launchPermissionRequest()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (uiState.isUploadingPhoto) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CameraAlt, "Добавить фото", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = "Основная информация",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Марка
            OutlinedTextField(
                value = uiState.brand,
                onValueChange = { viewModel.updateBrand(it) },
                label = { Text("Марка *") },
                placeholder = { Text("Toyota") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            // Модель
            OutlinedTextField(
                value = uiState.model,
                onValueChange = { viewModel.updateModel(it) },
                label = { Text("Модель *") },
                placeholder = { Text("Camry") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Год
                OutlinedTextField(
                    value = uiState.year,
                    onValueChange = { viewModel.updateYear(it) },
                    label = { Text("Год *") },
                    placeholder = { Text("2020") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !uiState.isSaving
                )

                // Гос. номер
                OutlinedTextField(
                    value = uiState.licensePlate,
                    onValueChange = { viewModel.updateLicensePlate(it) },
                    label = { Text("Номер *") },
                    placeholder = { Text("A123BC") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )
            }

            // Пробег
            OutlinedTextField(
                value = uiState.currentOdometer,
                onValueChange = { viewModel.updateOdometer(it) },
                label = { Text("Текущий пробег (км) *") },
                placeholder = { Text("50000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isSaving
            )

            // Тип топлива
            Text(
                text = "Тип топлива",
                style = MaterialTheme.typography.bodyMedium
            )

            FuelTypeSelector(
                selectedFuelType = uiState.fuelType,
                onFuelTypeSelected = { viewModel.updateFuelType(it) },
                enabled = !uiState.isSaving
            )

            Divider()

            Text(
                text = "Дополнительно",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // VIN
            OutlinedTextField(
                value = uiState.vin,
                onValueChange = { viewModel.updateVin(it) },
                label = { Text("VIN номер") },
                placeholder = { Text("1HGBH41JXMN109186") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            // Цвет
            OutlinedTextField(
                value = uiState.color,
                onValueChange = { viewModel.updateColor(it) },
                label = { Text("Цвет") },
                placeholder = { Text("Черный") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            // Валюта
            Text(text = "Валюта учёта", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CurrencyUtils.SUPPORTED_CURRENCIES) { cur ->
                    FilterChip(
                        selected = uiState.currency == cur,
                        onClick = { viewModel.updateCurrency(cur) },
                        label = { Text("$cur ${CurrencyUtils.symbol(cur)}") },
                        enabled = !uiState.isSaving
                    )
                }
            }

            // Цена покупки
            OutlinedTextField(
                value = uiState.purchasePrice,
                onValueChange = { viewModel.updatePurchasePrice(it) },
                label = { Text("Цена покупки") },
                placeholder = { Text("25000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isSaving,
                suffix = { Text("₽") }
            )

            // Дата покупки
            OutlinedTextField(
                value = uiState.purchaseDate?.let {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it))
                } ?: "",
                onValueChange = {},
                label = { Text("Дата покупки") },
                placeholder = { Text("Выберите дату") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                enabled = !uiState.isSaving,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, "Выбрать дату")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка сохранения
            Button(
                onClick = {
                    viewModel.saveCar {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Сохранить", style = MaterialTheme.typography.titleMedium)
                }
            }

            Text(
                text = "* Обязательные поля",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.purchaseDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.updatePurchaseDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun FuelTypeSelector(
    selectedFuelType: FuelType,
    onFuelTypeSelected: (FuelType) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FuelTypeChip(
                label = "Бензин",
                selected = selectedFuelType == FuelType.GASOLINE,
                onClick = { onFuelTypeSelected(FuelType.GASOLINE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            FuelTypeChip(
                label = "Дизель",
                selected = selectedFuelType == FuelType.DIESEL,
                onClick = { onFuelTypeSelected(FuelType.DIESEL) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FuelTypeChip(
                label = "Электро",
                selected = selectedFuelType == FuelType.ELECTRIC,
                onClick = { onFuelTypeSelected(FuelType.ELECTRIC) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            FuelTypeChip(
                label = "Гибрид",
                selected = selectedFuelType == FuelType.HYBRID,
                onClick = { onFuelTypeSelected(FuelType.HYBRID) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FuelTypeChip(
                label = "Газ",
                selected = selectedFuelType == FuelType.GAS,
                onClick = { onFuelTypeSelected(FuelType.GAS) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FuelTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled
    )
}