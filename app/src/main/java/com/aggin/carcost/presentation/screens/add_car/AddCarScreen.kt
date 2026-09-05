package com.aggin.carcost.presentation.screens.add_car

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
import com.aggin.carcost.data.reference.VehicleCatalog
import com.aggin.carcost.presentation.components.SuggestField
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
import com.aggin.carcost.data.local.database.entities.VehicleType
import com.aggin.carcost.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddCarScreen(
    navController: NavController,
    viewModel: AddCarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.home_dobavit_avtomobil)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
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
                        contentDescription = stringResource(R.string.home_foto_avtomobilya),
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
                                stringResource(R.string.addcar_nazhmite_kameru_dlya_foto),
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
                        Icon(Icons.Default.CameraAlt, stringResource(R.string.addcar_dobavit_foto), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = stringResource(R.string.addexp_osnovnaya_informatsiya),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Марка
            SuggestField(
                value = uiState.brand,
                onValueChange = { viewModel.updateBrand(it) },
                suggestions = VehicleCatalog.suggestBrands(uiState.vehicleType, uiState.brand),
                label = stringResource(R.string.addcar_marka),
                placeholder = "Toyota",
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            )

            // Модель
            SuggestField(
                value = uiState.model,
                onValueChange = { viewModel.updateModel(it) },
                suggestions = VehicleCatalog.suggestModels(
                    uiState.vehicleType, uiState.brand, uiState.model
                ),
                label = stringResource(R.string.addcar_model),
                placeholder = "Camry",
                modifier = Modifier.fillMaxWidth(),
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
                    label = { Text(stringResource(R.string.addcar_god)) },
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
                    label = { Text(stringResource(R.string.addcar_nomer)) },
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
                label = { Text(stringResource(R.string.addcar_tekuschiy_probeg_km)) },
                placeholder = { Text("50000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isSaving
            )

            Text(
                text = stringResource(R.string.addcar_vid_tehniki),
                style = MaterialTheme.typography.bodyMedium
            )

            VehicleTypeSelector(
                selected = uiState.vehicleType,
                onSelected = { viewModel.updateVehicleType(it) },
                enabled = !uiState.isSaving
            )

            // Тип топлива
            Text(
                text = stringResource(R.string.addcar_tip_topliva),
                style = MaterialTheme.typography.bodyMedium
            )

            FuelTypeSelector(
                selectedFuelType = uiState.fuelType,
                onFuelTypeSelected = { viewModel.updateFuelType(it) },
                enabled = !uiState.isSaving
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.addcar_dopolnitelno),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Объём бака. У электромобиля это ёмкость батареи — величина по сути
            // та же, меняется только подпись и единица
            OutlinedTextField(
                value = uiState.tankCapacity,
                onValueChange = { viewModel.updateTankCapacity(it) },
                label = {
                    Text(
                        if (uiState.fuelType == FuelType.ELECTRIC) stringResource(R.string.addcar_emkost_batarei)
                        else stringResource(R.string.addcar_obem_baka)
                    )
                },
                placeholder = {
                    Text(if (uiState.fuelType == FuelType.ELECTRIC) "60" else "45")
                },
                supportingText = {
                    Text(stringResource(R.string.addcar_nuzhen_chtoby_napominat_o_skoroy_zapravke))
                },
                suffix = {
                    Text(if (uiState.fuelType == FuelType.ELECTRIC) stringResource(R.string.addexp_kvt_ch) else stringResource(R.string.addexp_l))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isSaving
            )

            // Мощность двигателя — из ПТС. Нужна только для транспортного
            // налога, поэтому и подпись говорит об этом прямо
            OutlinedTextField(
                value = uiState.enginePowerHp,
                onValueChange = { viewModel.updateEnginePowerHp(it) },
                label = { Text(stringResource(R.string.car_engine_power)) },
                placeholder = { Text("106") },
                supportingText = { Text(stringResource(R.string.car_engine_power_hint)) },
                suffix = { Text(stringResource(R.string.car_hp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isSaving
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

            // Цвет
            OutlinedTextField(
                value = uiState.color,
                onValueChange = { viewModel.updateColor(it) },
                label = { Text(stringResource(R.string.addcar_tsvet)) },
                placeholder = { Text(stringResource(R.string.addcar_chernyy)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            // Валюта
            Text(text = stringResource(R.string.addcar_valyuta_ucheta), style = MaterialTheme.typography.bodyMedium)
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
                label = { Text(stringResource(R.string.addcar_tsena_pokupki)) },
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
                label = { Text(stringResource(R.string.addcar_data_pokupki)) },
                placeholder = { Text(stringResource(R.string.addcar_vyberite_datu)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                enabled = !uiState.isSaving,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, stringResource(R.string.addcar_vybrat_datu))
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
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }

            Text(
                text = stringResource(R.string.addexp_obyazatelnye_polya),
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
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FuelTypeSelector(
    selectedFuelType: FuelType,
    onFuelTypeSelected: (FuelType) -> Unit,
    enabled: Boolean = true
) {
    // Раньше здесь были прибитые гвоздями ряды, и «Подключаемый гибрид» в них
    // просто не попал: значение в перечислении появилось, а выбрать его было
    // нельзя. Список из самого перечисления такого больше не допустит.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        FuelType.entries.forEach { type ->
            FuelTypeChip(
                label = type.labelRu(),
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

@Composable
private fun FuelType.labelRu(): String = when (this) {
    FuelType.GASOLINE -> stringResource(R.string.home_benzin)
    FuelType.DIESEL -> stringResource(R.string.home_dizel)
    FuelType.ELECTRIC -> stringResource(R.string.home_elektro)
    FuelType.HYBRID -> stringResource(R.string.home_gibrid)
    FuelType.PLUGIN_HYBRID -> stringResource(R.string.addcar_gibrid_s_rozetkoy)
    FuelType.GAS -> stringResource(R.string.home_gaz)
    FuelType.OTHER -> stringResource(R.string.home_drugoe)
}

/** Автомобиль или мотоцикл — от этого зависят списки ТО и подписи */
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