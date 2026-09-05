package com.aggin.carcost.presentation.screens.export

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
import androidx.compose.runtime.saveable.rememberSaveable

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

    // Выбор файла копии. Ничего не применяется сразу — сначала показываем сводку
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.peekBackup(it) } }

    uiState.pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            icon = { Icon(Icons.Default.Restore, contentDescription = null) },
            title = { Text(stringResource(R.string.export_vosstanovit_dannye)) },
            text = {
                Column {
                    Text(stringResource(R.string.export_v_fayle, backup.summary(LocalContext.current)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.export_zapisi_s_sovpadayuschimi_identifikatorami) +
                            stringResource(R.string.export_ostalnye_dobavleny_povtornoe) +
                            stringResource(R.string.export_dubley_ne_sozdaet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isRestoring,
                    onClick = { viewModel.confirmRestore() }
                ) { Text(if (uiState.isRestoring) stringResource(R.string.export_vosstanavlivaem) else stringResource(R.string.export_vosstanovit)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isRestoring,
                    onClick = { viewModel.cancelRestore() }
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
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
                title = { Text(stringResource(R.string.cardetail_eksport_dannyh)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                            text = stringResource(R.string.export_eksport_dannyh_dlya_avtomobilya),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${uiState.car?.brand} ${uiState.car?.model}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.export_vyberite_format_i_period_dlya_eksporta),
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
                            text = stringResource(R.string.export_eksport_v_pdf),
                            icon = Icons.Default.PictureAsPdf,
                            onClick = { viewModel.exportToPdf() },
                            enabled = !uiState.isExporting
                        )

                        ExportButton(
                            text = stringResource(R.string.export_eksport_v_csv),
                            icon = Icons.Default.TableRows,
                            onClick = { viewModel.exportToCsv() },
                            enabled = !uiState.isExporting
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Паспорт стоит за разделителем вместе с бэкапом, а не
                        // рядом с отчётами: фильтры периода и категорий на него
                        // не действуют, и соседство с ними обещало бы обратное
                        ExportButton(
                            text = stringResource(R.string.passport_export),
                            icon = Icons.Default.Description,
                            onClick = { viewModel.exportVehiclePassport() },
                            enabled = !uiState.isExporting,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Text(
                            text = stringResource(R.string.passport_export_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        ExportButton(
                            text = stringResource(R.string.export_rezervnaya_kopiya_vse_avto),
                            icon = Icons.Default.BackupTable,
                            onClick = { viewModel.exportBackup() },
                            enabled = !uiState.isExporting,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ExportButton(
                            text = stringResource(R.string.export_vosstanovit_iz_kopii),
                            icon = Icons.Default.Restore,
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !uiState.isExporting && !uiState.isRestoring,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Text(
                            text = stringResource(R.string.export_v_kopiyu_vhodyat_avtomobili_rashody_to) +
                                stringResource(R.string.export_strahovki_intsidenty_byudzhety_tseli_i) +
                                stringResource(R.string.export_starye_csv_kopii_vosstanovit_nelzya),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        if (uiState.isExporting) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.export_sozdanie_fayla),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                else -> {
                    Text(stringResource(R.string.export_ne_udalos_zagruzit_dannye_ob_avtomobile))
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
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

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
                Text(stringResource(R.string.export_filtr_po_periodu), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (startDate != null || endDate != null) {
                    TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(stringResource(R.string.action_reset), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(startDate?.let { stringResource(R.string.export_s, fmt.format(Date(it))) } ?: stringResource(R.string.export_nachalo), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(endDate?.let { stringResource(R.string.export_po, fmt.format(Date(it))) } ?: stringResource(R.string.export_konets), style = MaterialTheme.typography.labelSmall)
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
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
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
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
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
                Text(stringResource(R.string.components_kategorii), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (allSelected) stringResource(R.string.export_snyat_vse) else stringResource(R.string.map_vse), style = MaterialTheme.typography.labelSmall)
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