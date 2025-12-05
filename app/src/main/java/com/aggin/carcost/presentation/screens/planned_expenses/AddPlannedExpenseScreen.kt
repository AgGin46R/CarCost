package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlannedExpenseScreen(
    carId: String,
    navController: NavController,
    viewModel: AddPlannedExpenseViewModel = viewModel(
        factory = AddPlannedExpenseViewModelFactory(
            carId = carId,
            application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    // Диалоги
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый план покупки") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Название
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Название *") },
                placeholder = { Text("Например: Замена амортизаторов") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.titleError != null
            )
            if (uiState.titleError != null) {
                Text(
                    text = uiState.titleError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Категория
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCategoryDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Категория *",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                getCategoryIcon(uiState.category),
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getCategoryName(uiState.category),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }

            // Описание
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Описание") },
                placeholder = { Text("Дополнительная информация") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Ориентировочная стоимость
            OutlinedTextField(
                value = uiState.estimatedAmount,
                onValueChange = { viewModel.updateEstimatedAmount(it) },
                label = { Text("Ориентировочная цена") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Text("₽", style = MaterialTheme.typography.bodyLarge) },
                singleLine = true
            )

            // Приоритет
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPriorityDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Приоритет",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriorityBadge(uiState.priority)
                    }
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }

            // Целевая дата
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Планируемая дата",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.targetDate != null) {
                                formatDate(uiState.targetDate!!)
                            } else {
                                "Не указана"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row {
                        if (uiState.targetDate != null) {
                            IconButton(onClick = { viewModel.updateTargetDate(null) }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                        Icon(Icons.Default.CalendarToday, null)
                    }
                }
            }

            // Целевой пробег
            OutlinedTextField(
                value = uiState.targetOdometer,
                onValueChange = { viewModel.updateTargetOdometer(it) },
                label = { Text("Планируемый пробег") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = { Text("км", style = MaterialTheme.typography.bodyLarge) },
                singleLine = true
            )

            // Заметки
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Заметки") },
                placeholder = { Text("Дополнительные заметки") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // Ссылка на магазин
            OutlinedTextField(
                value = uiState.shopUrl,
                onValueChange = { viewModel.updateShopUrl(it) },
                label = { Text("Ссылка на товар") },
                placeholder = { Text("https://...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, null) },
                singleLine = true
            )

            // Кнопка сохранения
            Button(
                onClick = { viewModel.savePlannedExpense() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isSaving) "Сохранение..." else "Сохранить план")
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Диалог выбора категории
    if (showCategoryDialog) {
        CategoryPickerDialog(
            selectedCategory = uiState.category,
            onCategorySelected = {
                viewModel.updateCategory(it)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Диалог выбора приоритета
    if (showPriorityDialog) {
        PriorityPickerDialog(
            selectedPriority = uiState.priority,
            onPrioritySelected = {
                viewModel.updatePriority(it)
                showPriorityDialog = false
            },
            onDismiss = { showPriorityDialog = false }
        )
    }

    // Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.targetDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        viewModel.updateTargetDate(it)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}