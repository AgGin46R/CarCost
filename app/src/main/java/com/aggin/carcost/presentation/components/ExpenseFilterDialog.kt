package com.aggin.carcost.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ExpenseTag // <-- ИЗМЕНЕН ТИП
import com.aggin.carcost.presentation.screens.car_detail.ExpenseFilter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseFilterDialog(
    currentFilter: ExpenseFilter,
    availableTags: List<ExpenseTag>, // <-- ИЗМЕНЕН ТИП НА ExpenseTag
    onFilterApplied: (ExpenseFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategories by remember { mutableStateOf(currentFilter.categories) }
    var selectedTags by remember { mutableStateOf(currentFilter.tags) }
    var minAmount by remember { mutableStateOf(currentFilter.minAmount?.toString() ?: "") }
    var maxAmount by remember { mutableStateOf(currentFilter.maxAmount?.toString() ?: "") }
    var startDate by remember { mutableStateOf(currentFilter.startDate) }
    var endDate by remember { mutableStateOf(currentFilter.endDate) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтр расходов") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Категории
                Text("Категории", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.values().forEach { category ->
                        FilterChip(
                            selected = category in selectedCategories,
                            onClick = {
                                selectedCategories = if (category in selectedCategories) {
                                    selectedCategories - category
                                } else {
                                    selectedCategories + category
                                }
                            },
                            label = { Text(category.name) }
                        )
                    }
                }

                // Теги
                if (availableTags.isNotEmpty()) {
                    Text("Теги", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTags.forEach { tag -> // <-- теперь это ExpenseTag
                            FilterChip(
                                selected = tag.id in selectedTags,
                                onClick = {
                                    selectedTags = if (tag.id in selectedTags) {
                                        selectedTags - tag.id
                                    } else {
                                        selectedTags + tag.id
                                    }
                                },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }

                // Сумма
                Text("Сумма", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAmount,
                        onValueChange = { minAmount = it },
                        label = { Text("От") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = maxAmount,
                        onValueChange = { maxAmount = it },
                        label = { Text("До") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Дата
                Text("Период", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(startDate?.let { formatDate(it) } ?: "С даты")
                    }
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(endDate?.let { formatDate(it) } ?: "По дату")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newFilter = ExpenseFilter(
                        categories = selectedCategories,
                        tags = selectedTags,
                        startDate = startDate,
                        endDate = endDate,
                        minAmount = minAmount.toDoubleOrNull(),
                        maxAmount = maxAmount.toDoubleOrNull()
                    )
                    onFilterApplied(newFilter)
                }
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = datePickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Отмена") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Отмена") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}