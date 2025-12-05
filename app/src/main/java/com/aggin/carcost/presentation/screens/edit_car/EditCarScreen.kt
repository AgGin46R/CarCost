package com.aggin.carcost.presentation.screens.edit_car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.FuelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCarScreen(
    carId: String, // ✅ String UUID
    navController: NavController,
    viewModel: EditCarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
                    IconButton(onClick = { viewModel.showDeleteDialog() }) {
                        Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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
                contentAlignment = androidx.compose.ui.Alignment.Center
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