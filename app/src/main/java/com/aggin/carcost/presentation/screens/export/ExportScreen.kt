package com.aggin.carcost.presentation.screens.export

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    carId: String // ✅ String UUID
) {
    // --- ИЗМЕНЕНИЯ ЗДЕСЬ ---
    // 1. Получаем контекст приложения
    val application = LocalContext.current.applicationContext as Application
    // 2. Создаем ViewModel с помощью нашей новой фабрики
    val viewModel: ExportViewModel = viewModel(
        factory = ExportViewModelFactory(application, carId)
    )
    // --- КОНЕЦ ИЗМЕНЕНИЙ ---

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ... остальной код вашего ExportScreen остается без изменений ...

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.exportSuccessMessage) {
        uiState.exportSuccessMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Экспорт данных") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.car != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Экспорт данных для автомобиля",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${uiState.car?.brand} ${uiState.car?.model}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Выберите формат и период для экспорта отчёта.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Фильтр периода
                        PeriodFilterCard(
                            startDate = uiState.filterStartDate,
                            endDate = uiState.filterEndDate,
                            onStartDateSelected = { viewModel.setDateFilter(it, uiState.filterEndDate) },
                            onEndDateSelected = { viewModel.setDateFilter(uiState.filterStartDate, it) },
                            onClear = { viewModel.setDateFilter(null, null) }
                        )

                        // Фильтр по категориям
                        CategoryFilterCard(
                            selectedCategories = uiState.selectedCategories,
                            onToggle = { viewModel.toggleCategory(it) },
                            onSelectAll = { viewModel.selectAllCategories() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ExportButton(
                            text = "Экспорт в PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = { viewModel.exportToPdf() },
                            enabled = !uiState.isExporting
                        )

                        ExportButton(
                            text = "Экспорт в CSV",
                            icon = Icons.Default.TableRows,
                            onClick = { viewModel.exportToCsv() },
                            enabled = !uiState.isExporting
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ExportButton(
                            text = "Резервная копия (все авто)",
                            icon = Icons.Default.BackupTable,
                            onClick = { viewModel.exportBackup() },
                            enabled = !uiState.isExporting,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        if (uiState.isExporting) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Text(
                                text = "Создание файла...",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                else -> {
                    Text("Не удалось загрузить данные об автомобиле.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodFilterCard(
    startDate: Long?,
    endDate: Long?,
    onStartDateSelected: (Long?) -> Unit,
    onEndDateSelected: (Long?) -> Unit,
    onClear: () -> Unit
) {
    val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Фильтр по периоду", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (startDate != null || endDate != null) {
                    TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(startDate?.let { "С: ${fmt.format(Date(it))}" } ?: "Начало", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(endDate?.let { "По: ${fmt.format(Date(it))}" } ?: "Конец", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onStartDateSelected(state.selectedDateMillis)
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Устанавливаем конец дня
                    val endMs = state.selectedDateMillis?.let { it + 86399999L }
                    onEndDateSelected(endMs)
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun CategoryFilterCard(
    selectedCategories: Set<ExpenseCategory>,
    onToggle: (ExpenseCategory) -> Unit,
    onSelectAll: () -> Unit
) {
    val allSelected = selectedCategories.size == ExpenseCategory.entries.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Категории", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (allSelected) "Снять все" else "Все", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ExpenseCategory.entries) { cat ->
                    FilterChip(
                        selected = cat in selectedCategories,
                        onClick = { onToggle(cat) },
                        label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}