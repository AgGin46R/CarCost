package com.aggin.carcost.presentation.screens.export

import android.app.Application // <-- Добавьте этот импорт
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext // <-- Добавьте этот импорт
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
// Удалите импорт com.aggin.carcost.presentation.viewmodel_factory.viewModelFactory, если он есть

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    carId: Long
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
                            text = "Выберите формат для экспорта отчета. Файл будет содержать полную информацию об автомобиле, всех расходах и напоминаниях.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))

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

                        if (uiState.isExporting) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Text(
                                text = "Создание отчета...",
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

@Composable
private fun ExportButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}