package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
import androidx.compose.runtime.saveable.rememberSaveable

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
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showPriorityDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plannedexpenses_novyy_plan_pokupki)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Название
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.plannedexpenses_nazvanie)) },
                placeholder = { Text(stringResource(R.string.plannedexpenses_naprimer_zamena_amortizatorov)) },
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
                            text = stringResource(R.string.plannedexpenses_kategoriya),
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
                label = { Text(stringResource(R.string.cardetail_opisanie)) },
                placeholder = { Text(stringResource(R.string.plannedexpenses_dopolnitelnaya_informatsiya)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Ориентировочная стоимость
            OutlinedTextField(
                value = uiState.estimatedAmount,
                onValueChange = { viewModel.updateEstimatedAmount(it) },
                label = { Text(stringResource(R.string.plannedexpenses_orientirovochnaya_tsena)) },
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
                            text = stringResource(R.string.plannedexpenses_prioritet),
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
                            text = stringResource(R.string.plannedexpenses_planiruemaya_data),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.targetDate != null) {
                                formatDate(uiState.targetDate!!)
                            } else {
                                stringResource(R.string.plannedexpenses_ne_ukazana)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row {
                        if (uiState.targetDate != null) {
                            IconButton(onClick = { viewModel.updateTargetDate(null) }) {
                                Icon(Icons.Default.Clear, stringResource(R.string.documents_ochistit))
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
                label = { Text(stringResource(R.string.plannedexpenses_planiruemyy_probeg)) },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = { Text(stringResource(R.string.plannedexpenses_km), style = MaterialTheme.typography.bodyLarge) },
                singleLine = true
            )

            // Заметки
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text(stringResource(R.string.incidents_zametki)) },
                placeholder = { Text(stringResource(R.string.plannedexpenses_dopolnitelnye_zametki)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // Ссылка на магазин
            OutlinedTextField(
                value = uiState.shopUrl,
                onValueChange = { viewModel.updateShopUrl(it) },
                label = { Text(stringResource(R.string.plannedexpenses_ssylka_na_tovar)) },
                placeholder = { Text("https://...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, null) },
                singleLine = true
            )

            // Повторение (Recurrence)
            Text(
                stringResource(R.string.plannedexpenses_povtorenie),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(null to stringResource(R.string.action_no), "MONTHLY" to stringResource(R.string.plannedexpenses_mes), "WEEKLY" to stringResource(R.string.plannedexpenses_ned), "YEARLY" to stringResource(R.string.plannedexpenses_god)).forEach { (type, label) ->
                    FilterChip(
                        selected = uiState.recurrenceType == type,
                        onClick = { viewModel.updateRecurrenceType(type) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                Text(if (uiState.isSaving) stringResource(R.string.plannedexpenses_sohranenie) else stringResource(R.string.plannedexpenses_sohranit_plan))
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
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}