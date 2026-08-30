package com.aggin.carcost.presentation.screens.search

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.formatDateCompact
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Фильтры поиска: автомобиль, категории, период, диапазон сумм.
 *
 * Повторяет набор из ExpenseFilterDialog (который работает внутри одного
 * автомобиля), но применяется к глобальному поиску и добавляет выбор машины.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    filter: SearchFilter,
    cars: List<Car>,
    onApply: (SearchFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var carId by remember { mutableStateOf(filter.carId) }
    var categories by remember { mutableStateOf(filter.categories) }
    var startDate by remember { mutableStateOf(filter.startDate) }
    var endDate by remember { mutableStateOf(filter.endDate) }
    var minAmount by remember { mutableStateOf(filter.minAmount?.toString() ?: "") }
    var maxAmount by remember { mutableStateOf(filter.maxAmount?.toString() ?: "") }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.search_filtry), style = MaterialTheme.typography.titleLarge)

            if (cars.size > 1) {
                Text(stringResource(R.string.maintenancedashboard_avtomobil), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = carId == null,
                        onClick = { carId = null },
                        label = { Text(stringResource(R.string.map_vse)) }
                    )
                    cars.forEach { car ->
                        FilterChip(
                            selected = carId == car.id,
                            onClick = { carId = if (carId == car.id) null else car.id },
                            label = { Text("${car.brand} ${car.model}") }
                        )
                    }
                }
            }

            Text(stringResource(R.string.components_kategorii), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.values().forEach { category ->
                    FilterChip(
                        selected = category in categories,
                        onClick = {
                            categories = if (category in categories) categories - category
                            else categories + category
                        },
                        label = { Text(category.displayName()) }
                    )
                }
            }

            Text(stringResource(R.string.components_period), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text(startDate?.let { formatDateCompact(it) } ?: stringResource(R.string.components_s_daty)) }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text(endDate?.let { formatDateCompact(it) } ?: stringResource(R.string.components_po_datu)) }
            }

            Text(stringResource(R.string.components_summa), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minAmount,
                    onValueChange = { minAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.components_ot)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxAmount,
                    onValueChange = { maxAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.components_do)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reset))
                }
                Button(
                    onClick = {
                        onApply(
                            SearchFilter(
                                carId = carId,
                                categories = categories,
                                startDate = startDate,
                                endDate = endDate,
                                minAmount = minAmount.toDoubleOrNull(),
                                maxAmount = maxAmount.toDoubleOrNull()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = state.selectedDateMillis
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Включаем весь выбранный день, иначе расходы этого дня отсеются
                    endDate = state.selectedDateMillis?.plus(24 * 60 * 60 * 1000L - 1)
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}
