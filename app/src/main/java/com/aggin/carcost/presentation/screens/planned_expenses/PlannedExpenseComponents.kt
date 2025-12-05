package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aggin.carcost.data.local.database.entities.*

// Компонент приоритета
@Composable
fun PriorityBadge(priority: PlannedExpensePriority) {
    val (color, text) = when (priority) {
        PlannedExpensePriority.LOW -> MaterialTheme.colorScheme.surfaceVariant to "Низкий"
        PlannedExpensePriority.MEDIUM -> MaterialTheme.colorScheme.tertiary to "Средний"
        PlannedExpensePriority.HIGH -> MaterialTheme.colorScheme.primary to "Высокий"
        PlannedExpensePriority.URGENT -> MaterialTheme.colorScheme.error to "Срочно"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

// Компонент статуса
@Composable
fun StatusBadge(status: PlannedExpenseStatus) {
    val (icon, text, color) = when (status) {
        PlannedExpenseStatus.PLANNED -> Triple(
            Icons.Default.Schedule,
            "Запланировано",
            MaterialTheme.colorScheme.primary
        )
        PlannedExpenseStatus.IN_PROGRESS -> Triple(
            Icons.Default.Autorenew,
            "В процессе",
            MaterialTheme.colorScheme.tertiary
        )
        PlannedExpenseStatus.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            "Выполнено",
            MaterialTheme.colorScheme.surfaceVariant
        )
        PlannedExpenseStatus.CANCELLED -> Triple(
            Icons.Default.Cancel,
            "Отменено",
            MaterialTheme.colorScheme.error
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// Диалог выбора категории
@Composable
fun CategoryPickerDialog(
    selectedCategory: ExpenseCategory,
    onCategorySelected: (ExpenseCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите категорию") },
        text = {
            Column {
                ExpenseCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = category == selectedCategory,
                            onClick = { onCategorySelected(category) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            getCategoryIcon(category),
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getCategoryName(category))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// Диалог выбора приоритета
@Composable
fun PriorityPickerDialog(
    selectedPriority: PlannedExpensePriority,
    onPrioritySelected: (PlannedExpensePriority) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите приоритет") },
        text = {
            Column {
                PlannedExpensePriority.entries.forEach { priority ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPrioritySelected(priority) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = priority == selectedPriority,
                            onClick = { onPrioritySelected(priority) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(priority)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// Диалог выбора статуса
@Composable
fun StatusPickerDialog(
    selectedStatus: PlannedExpenseStatus,
    onStatusSelected: (PlannedExpenseStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите статус") },
        text = {
            Column {
                PlannedExpenseStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusSelected(status) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = status == selectedStatus,
                            onClick = { onStatusSelected(status) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}