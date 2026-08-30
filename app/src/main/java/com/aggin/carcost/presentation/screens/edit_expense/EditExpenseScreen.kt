package com.aggin.carcost.presentation.screens.edit_expense

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.presentation.components.TagSelector  // ✅ ДОБАВЛЕНО
import com.aggin.carcost.presentation.screens.add_expense.CategoryChip
import java.text.SimpleDateFormat
import java.util.*
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.formatDateLong
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    carId: String, // ✅ String UUID
    expenseId: String, // ✅ String UUID
    navController: NavController,
    viewModel: EditExpenseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(carId, expenseId) {
        viewModel.loadExpense(carId, expenseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editexp_redaktirovat_rashod)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
                uiState.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Категория
                Text(
                    text = stringResource(R.string.addexp_kategoriya_2),
                    style = MaterialTheme.typography.titleMedium
                )
                CategorySelector(
                    selectedCategory = uiState.category,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    enabled = !uiState.isSaving
                )

                HorizontalDivider()

                // Основная информация
                Text(
                    text = stringResource(R.string.addexp_osnovnaya_informatsiya),
                    style = MaterialTheme.typography.titleMedium
                )

                // Сумма
                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text(stringResource(R.string.addexp_summa)) },
                    placeholder = { Text("100.00") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !uiState.isSaving,
                    suffix = { Text("₽") },
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { error ->
                        { Text(error) }
                    }
                )

                // Пробег
                OutlinedTextField(
                    value = uiState.odometer,
                    onValueChange = { viewModel.updateOdometer(it) },
                    label = { Text(stringResource(R.string.addexp_probeg_km)) },
                    placeholder = { Text("50000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !uiState.isSaving,
                    isError = uiState.odometerError != null,
                    supportingText = uiState.odometerError?.let { error ->
                        { Text(error) }
                    }
                )

                // Дата
                var showDatePicker by rememberSaveable { mutableStateOf(false) }

                OutlinedTextField(
                    value = formatDate(uiState.date),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.cardetail_data)) },
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
                    label = { Text(stringResource(R.string.cardetail_opisanie)) },
                    placeholder = { Text(stringResource(R.string.addexp_zapravka_na_shell)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !uiState.isSaving
                )

                // Место
                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = { viewModel.updateLocation(it) },
                    label = { Text(stringResource(R.string.cardetail_mesto)) },
                    placeholder = { Text(stringResource(R.string.addexp_shell_ul_lenina)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )

                HorizontalDivider()

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
                        Text(
                            text = stringResource(R.string.addexp_detali_zapravki),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = uiState.fuelLiters,
                            onValueChange = { viewModel.updateFuelLiters(it) },
                            label = { Text(stringResource(R.string.addexp_litrov)) },
                            placeholder = { Text("45.5") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving,
                            suffix = { Text(stringResource(R.string.addexp_l)) }
                        )

                        OutlinedTextField(
                            value = uiState.fuelGrade,
                            onValueChange = { viewModel.updateFuelGrade(it) },
                            label = { Text(stringResource(R.string.cardetail_marka_topliva)) },
                            placeholder = { Text(stringResource(R.string.addexp_ai_95)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !uiState.isSaving
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.addexp_polnyy_bak))
                            Switch(
                                checked = uiState.isFullTank,
                                onCheckedChange = { viewModel.updateIsFullTank(it) },
                                enabled = !uiState.isSaving
                            )
                        }

                        HorizontalDivider()
                    }

                    ExpenseCategory.CHARGING -> {
                        Text(
                            text = stringResource(R.string.addexp_detali_zaryadki),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = uiState.energyKwh,
                            onValueChange = { viewModel.updateEnergyKwh(it) },
                            label = { Text(stringResource(R.string.addexp_kilovatt_chasov)) },
                            placeholder = { Text("42.0") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving,
                            suffix = { Text(stringResource(R.string.addexp_kvt_ch)) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.addexp_zaryad_do_100))
                            Switch(
                                checked = uiState.isFullTank,
                                onCheckedChange = { viewModel.updateIsFullTank(it) },
                                enabled = !uiState.isSaving
                            )
                        }

                        HorizontalDivider()
                    }

                    ExpenseCategory.MAINTENANCE -> {
                        Text(
                            text = stringResource(R.string.addexp_detali_obsluzhivaniya),
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
                            label = { Text(stringResource(R.string.addexp_nazvanie_sto)) },
                            placeholder = { Text(stringResource(R.string.addexp_avtoservis_1)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !uiState.isSaving
                        )

                        HorizontalDivider()
                    }

                    ExpenseCategory.REPAIR -> {
                        Text(
                            text = stringResource(R.string.addexp_detali_remonta),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = uiState.workshopName,
                            onValueChange = { viewModel.updateWorkshopName(it) },
                            label = { Text(stringResource(R.string.addexp_nazvanie_sto)) },
                            placeholder = { Text(stringResource(R.string.addexp_avtoservis_1)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !uiState.isSaving
                        )

                        HorizontalDivider()
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
                        Text(stringResource(R.string.editexp_sohranit_izmeneniya), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Text(
                    text = stringResource(R.string.addexp_obyazatelnye_polya),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                label = stringResource(R.string.cardetail_toplivo),
                selected = selectedCategory == ExpenseCategory.FUEL,
                onClick = { onCategorySelected(ExpenseCategory.FUEL) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.cardetail_to),
                selected = selectedCategory == ExpenseCategory.MAINTENANCE,
                onClick = { onCategorySelected(ExpenseCategory.MAINTENANCE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.addexp_remont),
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
                label = stringResource(R.string.addexp_strahovka),
                selected = selectedCategory == ExpenseCategory.INSURANCE,
                onClick = { onCategorySelected(ExpenseCategory.INSURANCE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.addexp_nalog),
                selected = selectedCategory == ExpenseCategory.TAX,
                onClick = { onCategorySelected(ExpenseCategory.TAX) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.cardetail_parkovka),
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
                label = stringResource(R.string.addexp_doroga),
                selected = selectedCategory == ExpenseCategory.TOLL,
                onClick = { onCategorySelected(ExpenseCategory.TOLL) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.addexp_moyka),
                selected = selectedCategory == ExpenseCategory.WASH,
                onClick = { onCategorySelected(ExpenseCategory.WASH) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.addexp_shtraf),
                selected = selectedCategory == ExpenseCategory.FINE,
                onClick = { onCategorySelected(ExpenseCategory.FINE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }

        // Ряд 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(
                label = stringResource(R.string.addexp_aksessuary),
                selected = selectedCategory == ExpenseCategory.ACCESSORIES,
                onClick = { onCategorySelected(ExpenseCategory.ACCESSORIES) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            CategoryChip(
                label = stringResource(R.string.addexp_drugoe),
                selected = selectedCategory == ExpenseCategory.OTHER,
                onClick = { onCategorySelected(ExpenseCategory.OTHER) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            // Пустой слот для симметрии
            Spacer(modifier = Modifier.weight(1f))
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
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedServiceType?.let { getServiceTypeName(it) } ?: stringResource(R.string.addexp_vyberite_tip),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.addexp_tip_obsluzhivaniya)) },
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

@Composable
fun getServiceTypeName(serviceType: ServiceType) = serviceType.displayName()

fun formatDate(timestamp: Long): String = formatDateLong(timestamp)

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
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}