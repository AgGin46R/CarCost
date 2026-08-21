package com.aggin.carcost.presentation.screens.edit_car

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.VehicleType
import com.aggin.carcost.util.CurrencyUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EditCarScreen(
    carId: String, // ✅ String UUID
    navController: NavController,
    viewModel: EditCarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
                title = { Text("Редактировать автомобиль") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Удаление автомобиля вместе со всей историей расходов —
                    // право владельца, а не любого участника
                    if (uiState.canDeleteCar) {
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (uiState.photoUri != null) {
                            AsyncImage(
                                model = uiState.photoUri,
                                contentDescription = "Фото автомобиля",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                if (mediaPermission.status.isGranted) {
                                    galleryLauncher.launch("image/*")
                                } else {
                                    mediaPermission.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            if (uiState.isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Сменить фото",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Text(
                        "Нажмите на камеру чтобы выбрать фото",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                OutlinedTextField(
                    value = uiState.tankCapacity,
                    onValueChange = { viewModel.updateTankCapacity(it) },
                    label = {
                        Text(
                            if (uiState.fuelType == FuelType.ELECTRIC) "Ёмкость батареи"
                            else "Объём бака"
                        )
                    },
                    supportingText = { Text("Нужен, чтобы напоминать о скорой заправке") },
                    suffix = {
                        Text(if (uiState.fuelType == FuelType.ELECTRIC) "кВт·ч" else "л")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !uiState.isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                VehicleTypeSelector(
                    selected = uiState.vehicleType,
                    onSelected = { viewModel.updateVehicleType(it) },
                    enabled = !uiState.isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                FuelTypeSelector(
                    selectedFuelType = uiState.fuelType,
                    onFuelTypeSelected = { viewModel.updateFuelType(it) },
                    enabled = !uiState.isSaving
                )

                HorizontalDivider()

                Text(
                    text = "Дополнительно",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // VIN
                OutlinedTextField(
                    value = uiState.vin,
                    onValueChange = { viewModel.updateVin(it) },
                    label = { Text("VIN") },
                    placeholder = { Text("1HGBH41JXMN109186") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )

                // Цвет — палитра + текстовое поле
                CarColorPicker(
                    currentColor = uiState.color,
                    onColorSelected = { viewModel.updateColor(it) },
                    enabled = !uiState.isSaving
                )

                // Валюта
                Text("Валюта учёта", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CurrencyUtils.SUPPORTED_CURRENCIES) { cur ->
                        FilterChip(
                            selected = uiState.currency == cur,
                            onClick = { viewModel.updateCurrency(cur) },
                            label = { Text("$cur ${CurrencyUtils.symbol(cur)}") },
                            enabled = !uiState.isSaving
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопка сохранения
                Button(
                    onClick = {
                        viewModel.updateCar {
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
                        Text("Сохранить изменения", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Text(
                    text = "* Обязательные поля",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Диалог удаления
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Удалить автомобиль?") },
            text = {
                Text("Вместе с автомобилем будут удалены все расходы. Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCar {
                            navController.popBackStack(
                                navController.graph.startDestinationRoute!!,
                                inclusive = false
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FuelTypeSelector(
    selectedFuelType: FuelType,
    onFuelTypeSelected: (FuelType) -> Unit,
    enabled: Boolean = true
) {
    // Здесь лежала своя копия прибитых гвоздями рядов — и «Подключаемый гибрид»
    // в неё не попал: значение появилось в перечислении, форма добавления его
    // получила, а эта копия осталась со старым набором. Строим из перечисления,
    // чтобы расхождение не могло повториться.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        FuelType.entries.forEach { type ->
            FuelTypeChip(
                label = fuelTypeLabel(type),
                selected = selectedFuelType == type,
                onClick = { onFuelTypeSelected(type) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
        if (FuelType.entries.size % 2 != 0) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun fuelTypeLabel(type: FuelType): String = when (type) {
    FuelType.GASOLINE -> "Бензин"
    FuelType.DIESEL -> "Дизель"
    FuelType.ELECTRIC -> "Электро"
    FuelType.HYBRID -> "Гибрид"
    FuelType.PLUGIN_HYBRID -> "Гибрид с розеткой"
    FuelType.GAS -> "Газ"
    FuelType.OTHER -> "Другое"
}

/** Автомобиль или мотоцикл. Тот же выбор, что и при создании */
@Composable
fun VehicleTypeSelector(
    selected: VehicleType,
    onSelected: (VehicleType) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VehicleType.entries.forEach { type ->
            FuelTypeChip(
                label = type.labelRu,
                selected = selected == type,
                onClick = { onSelected(type) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Car Color Picker
// ---------------------------------------------------------------------------

/** Common car colors as hex strings → display name */
val CAR_COLOR_PALETTE: List<Pair<String, String>> = listOf(
    "#FFFFFF" to "Белый",
    "#C0C0C0" to "Серебристый",
    "#808080" to "Серый",
    "#1C1C1C" to "Чёрный",
    "#C0392B" to "Красный",
    "#E67E22" to "Оранжевый",
    "#F1C40F" to "Жёлтый",
    "#27AE60" to "Зелёный",
    "#2980B9" to "Синий",
    "#1A237E" to "Тёмно-синий",
    "#6C3483" to "Фиолетовый",
    "#795548" to "Коричневый",
)

@Composable
fun CarColorPicker(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Цвет автомобиля", style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CAR_COLOR_PALETTE) { (hex, name) ->
                val isSelected = currentColor.equals(hex, ignoreCase = true)
                val parsed = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(parsed)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                        )
                        .clickable(enabled = enabled) { onColorSelected(hex) }
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (parsed.luminance() > 0.4f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        // Also allow free-text entry for custom colors
        OutlinedTextField(
            value = currentColor,
            onValueChange = { if (enabled) onColorSelected(it) },
            label = { Text("Или введите вручную") },
            placeholder = { Text("#RRGGBB или название") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            supportingText = { Text("Выберите из палитры или введите HEX-цвет", fontSize = 11.sp) }
        )
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
