package com.aggin.carcost.presentation.screens.add_expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import java.text.SimpleDateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aggin.carcost.presentation.navigation.Screen
import com.aggin.carcost.presentation.components.TagSelector
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    carId: Long,
    navController: NavController,
    viewModel: AddExpenseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // --- НОВЫЙ БЛОК: ЛОГИКА ПОЛУЧЕНИЯ ДАННЫХ СО СКАНЕРА ---
    // Получаем доступ к savedStateHandle, чтобы читать данные, переданные с другого экрана
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

    // Этот эффект сработает каждый раз, когда экран становится активным (включая возврат со сканера)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Пробуем получить значение "scanned_amount"
        val scannedAmount = savedStateHandle?.get<Double>("scanned_amount")
        if (scannedAmount != null) {
            // Если значение есть, обновляем ViewModel
            viewModel.updateAmount(scannedAmount.toString())
            // Очищаем значение, чтобы оно не применилось повторно
            savedStateHandle.remove<Double>("scanned_amount")
        }

        // То же самое для даты
        val scannedDate = savedStateHandle?.get<Long>("scanned_date")
        if (scannedDate != null) {
            viewModel.updateDate(scannedDate)
            savedStateHandle.remove<Long>("scanned_date")
        }
    }
    // --- КОНЕЦ НОВОГО БЛОКА ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить расход") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
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

            // Категория
            Text(
                text = "Категория",
                style = MaterialTheme.typography.titleMedium
            )
            CategorySelector(
                selectedCategory = uiState.category,
                onCategorySelected = { viewModel.updateCategory(it) },
                enabled = !uiState.isSaving
            )

            // --- НОВАЯ КНОПКА "СКАНИРОВАТЬ ЧЕК" ---
            OutlinedButton(
                onClick = { navController.navigate(Screen.ReceiptScan.createRoute(carId)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ReceiptLong, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сканировать чек")
            }
            // --- КОНЕЦ НОВОЙ КНОПКИ ---

            Divider()

            // Основная информация
            Text(
                text = "Основная информация",
                style = MaterialTheme.typography.titleMedium
            )

            // Сумма
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Сумма *") },
                placeholder = { Text("100.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isSaving,
                suffix = { Text("₽") }
            )

            // Пробег
            OutlinedTextField(
                value = uiState.odometer,
                onValueChange = { viewModel.updateOdometer(it) },
                label = { Text("Пробег (км) *") },
                placeholder = { Text("50000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isSaving
            )

            // Дата
            var showDatePicker by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = formatDate(uiState.date),
                onValueChange = { },
                label = { Text("Дата") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = !uiState.isSaving,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, null)
                    }
                }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    selectedDate = uiState.date,
                    onDateSelected = { viewModel.updateDate(it) },
                    onDismiss = { showDatePicker = false }
                )
            }

            // Описание
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Описание") },
                placeholder = { Text("Заправка на Shell") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !uiState.isSaving
            )

            // Место
            OutlinedTextField(
                value = uiState.location,
                onValueChange = { viewModel.updateLocation(it) },
                label = { Text("Место") },
                placeholder = { Text("Shell, ул. Ленина") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            Divider()

            // Теги
            TagSelector(
                availableTags = uiState.availableTags,
                selectedTags = uiState.selectedTags,
                onTagSelected = { viewModel.addTag(it) },
                onTagRemoved = { viewModel.removeTag(it) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            // Специфичные поля для категорий
            when (uiState.category) {
                ExpenseCategory.FUEL -> {
                    Divider()
                    Text(
                        text = "Детали заправки",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.fuelLiters,
                        onValueChange = { viewModel.updateFuelLiters(it) },
                        label = { Text("Литров") },
                        placeholder = { Text("45.5") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !uiState.isSaving,
                        suffix = { Text("л") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Полный бак")
                        Switch(
                            checked = uiState.isFullTank,
                            onCheckedChange = { viewModel.updateIsFullTank(it) },
                            enabled = !uiState.isSaving
                        )
                    }
                }

                ExpenseCategory.MAINTENANCE -> {
                    Divider()
                    Text(
                        text = "Детали обслуживания",
                        style = MaterialTheme.typography.titleMedium
                    )

                    ServiceTypeDropdown(
                        selectedServiceType = uiState.serviceType,
                        onServiceTypeSelected = { viewModel.updateServiceType(it) },
                        enabled = !uiState.isSaving
                    )

                    OutlinedTextField(
                        value = uiState.workshopName,
                        onValueChange = { viewModel.updateWorkshopName(it) },
                        label = { Text("Название СТО") },
                        placeholder = { Text("Автосервис №1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isSaving
                    )
                }

                ExpenseCategory.REPAIR -> {
                    Divider()
                    Text(
                        text = "Детали ремонта",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.workshopName,
                        onValueChange = { viewModel.updateWorkshopName(it) },
                        label = { Text("Название СТО") },
                        placeholder = { Text("Автосервис №1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isSaving
                    )
                }

                else -> { /* Нет специфичных полей */ }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка сохранения
            Button(
                onClick = {
                    viewModel.saveExpense {
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
}

@Composable
fun CategorySelector(
    selectedCategory: ExpenseCategory,
    onCategorySelected: (ExpenseCategory) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Ряд 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(
                label = "⛽ Топливо",
                selected = selectedCategory == ExpenseCategory.FUEL,
                onClick = { onCategorySelected(ExpenseCategory.FUEL) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "🔧 ТО",
                selected = selectedCategory == ExpenseCategory.MAINTENANCE,
                onClick = { onCategorySelected(ExpenseCategory.MAINTENANCE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "🛠️ Ремонт",
                selected = selectedCategory == ExpenseCategory.REPAIR,
                onClick = { onCategorySelected(ExpenseCategory.REPAIR) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }

        // Ряд 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(
                label = "🛡️ Страховка",
                selected = selectedCategory == ExpenseCategory.INSURANCE,
                onClick = { onCategorySelected(ExpenseCategory.INSURANCE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "🧾 Налог",
                selected = selectedCategory == ExpenseCategory.TAX,
                onClick = { onCategorySelected(ExpenseCategory.TAX) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "🅿️ Парковка",
                selected = selectedCategory == ExpenseCategory.PARKING,
                onClick = { onCategorySelected(ExpenseCategory.PARKING) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }

        // Ряд 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(
                label = "💧 Мойка",
                selected = selectedCategory == ExpenseCategory.WASH,
                onClick = { onCategorySelected(ExpenseCategory.WASH) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "⚠️ Штраф",
                selected = selectedCategory == ExpenseCategory.FINE,
                onClick = { onCategorySelected(ExpenseCategory.FINE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = "➕ Другое",
                selected = selectedCategory == ExpenseCategory.OTHER,
                onClick = { onCategorySelected(ExpenseCategory.OTHER) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        enabled = enabled
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTypeDropdown(
    selectedServiceType: ServiceType?,
    onServiceTypeSelected: (ServiceType?) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedServiceType?.let { getServiceTypeName(it) } ?: "Выберите тип",
            onValueChange = {},
            readOnly = true,
            label = { Text("Тип обслуживания") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ServiceType.values().forEach { serviceType ->
                DropdownMenuItem(
                    text = { Text(getServiceTypeName(serviceType)) },
                    onClick = {
                        onServiceTypeSelected(serviceType)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun getServiceTypeName(serviceType: ServiceType) = when (serviceType) {
    ServiceType.OIL_CHANGE -> "Замена масла"
    ServiceType.OIL_FILTER -> "Масляный фильтр"
    ServiceType.AIR_FILTER -> "Воздушный фильтр"
    ServiceType.FUEL_FILTER -> "Топливный фильтр"
    ServiceType.CABIN_FILTER -> "Салонный фильтр"
    ServiceType.SPARK_PLUGS -> "Свечи зажигания"
    ServiceType.BRAKE_PADS -> "Тормозные колодки"
    ServiceType.BRAKE_FLUID -> "Тормозная жидкость"
    ServiceType.COOLANT -> "Охлаждающая жидкость"
    ServiceType.TRANSMISSION_FLUID -> "Трансмиссионное масло"
    ServiceType.TIMING_BELT -> "Ремень ГРМ"
    ServiceType.TIRES -> "Шины"
    ServiceType.BATTERY -> "Аккумулятор"
    ServiceType.ALIGNMENT -> "Развал-схождение"
    ServiceType.BALANCING -> "Балансировка"
    ServiceType.INSPECTION -> "Техосмотр"
    ServiceType.FULL_SERVICE -> "Полное ТО"
    ServiceType.OTHER -> "Другое"
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}