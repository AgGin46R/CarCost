package com.aggin.carcost.presentation.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import java.text.NumberFormat
import java.util.*

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    carId: String,
    navController: NavController,
    viewModel: BudgetViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingCategory by remember { mutableStateOf<ExpenseCategory?>(null) }

    LaunchedEffect(carId) { viewModel.load(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Бюджет по категориям")
                        if (uiState.month > 0) {
                            Text(
                                "${MONTH_NAMES[uiState.month - 1]} ${uiState.year}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
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
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
                // Итоговая карточка
                item {
                    BudgetSummaryCard(uiState)
                }

                items(uiState.items, key = { it.category.name }) { item ->
                    BudgetCategoryCard(
                        item = item,
                        onEdit = { editingCategory = item.category },
                        onRemove = { viewModel.removeLimit(item.category) }
                    )
                }
            }
        }
    }

    editingCategory?.let { category ->
        val current = uiState.items.find { it.category == category }
        BudgetEditDialog(
            category = category,
            currentLimit = current?.limit,
            onDismiss = { editingCategory = null },
            onSave = { limit ->
                viewModel.setLimit(category, limit)
                editingCategory = null
            }
        )
    }
}

@Composable
private fun BudgetSummaryCard(uiState: BudgetUiState) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }
    val hasLimits = uiState.totalLimit > 0
    val overallProgress = if (hasLimits) (uiState.totalSpent / uiState.totalLimit).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Итого за месяц",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Потрачено", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${fmt.format(uiState.totalSpent)} ₽",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (hasLimits && uiState.totalSpent > uiState.totalLimit)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (hasLimits) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Лимит", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${fmt.format(uiState.totalLimit)} ₽",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (hasLimits) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (overallProgress >= 1f) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (uiState.totalSpent > uiState.totalLimit)
                        "Превышение: ${fmt.format(uiState.totalSpent - uiState.totalLimit)} ₽"
                    else
                        "Остаток: ${fmt.format(uiState.totalLimit - uiState.totalSpent)} ₽",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.totalSpent > uiState.totalLimit)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BudgetCategoryCard(
    item: BudgetItem,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 }

    // Skip categories with no spending and no limit
    if (item.spent == 0.0 && item.limit == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isOverBudget)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    categoryEmoji(item.category),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        categoryName(item.category),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append("${fmt.format(item.spent)} ₽")
                            if (item.limit != null) append(" / ${fmt.format(item.limit)} ₽")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isOverBudget) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Изменить лимит", modifier = Modifier.size(18.dp))
                }
                if (item.limit != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Удалить лимит",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (item.limit != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when {
                        item.isOverBudget -> MaterialTheme.colorScheme.error
                        item.progress > 0.8f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        if (item.isOverBudget)
                            "Перерасход: ${fmt.format(-item.remaining)} ₽"
                        else
                            "Осталось: ${fmt.format(item.remaining)} ₽",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isOverBudget) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    category: ExpenseCategory,
    currentLimit: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(currentLimit?.toInt()?.toString() ?: "") }
    val isValid = text.toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лимит: ${categoryName(category)}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Сумма в ₽") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let(onSave) }, enabled = isValid) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun categoryEmoji(c: ExpenseCategory) = when (c) {
    ExpenseCategory.FUEL -> "⛽"
    ExpenseCategory.MAINTENANCE -> "🔧"
    ExpenseCategory.REPAIR -> "🛠️"
    ExpenseCategory.INSURANCE -> "🛡️"
    ExpenseCategory.TAX -> "📋"
    ExpenseCategory.PARKING -> "🅿️"
    ExpenseCategory.TOLL -> "🛣️"
    ExpenseCategory.WASH -> "🚿"
    ExpenseCategory.FINE -> "⚠️"
    ExpenseCategory.ACCESSORIES -> "🔩"
    ExpenseCategory.OTHER -> "📦"
}

private fun categoryName(c: ExpenseCategory) = when (c) {
    ExpenseCategory.FUEL -> "Топливо"
    ExpenseCategory.MAINTENANCE -> "Обслуживание"
    ExpenseCategory.REPAIR -> "Ремонт"
    ExpenseCategory.INSURANCE -> "Страховка"
    ExpenseCategory.TAX -> "Налог"
    ExpenseCategory.PARKING -> "Парковка"
    ExpenseCategory.TOLL -> "Платная дорога"
    ExpenseCategory.WASH -> "Мойка"
    ExpenseCategory.FINE -> "Штраф"
    ExpenseCategory.ACCESSORIES -> "Аксессуары"
    ExpenseCategory.OTHER -> "Прочее"
}
