package com.aggin.carcost.presentation.screens.bug_report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen(
    navController: NavController,
    viewModel: BugReportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Показываем диалог успеха и возвращаемся назад
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сообщить об ошибке") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
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
            // Информационная карточка
            InfoCard()

            // Поле для описания проблемы
            OutlinedTextField(
                value = uiState.userDescription,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Опишите проблему") },
                placeholder = { Text("Что пошло не так? Что вы хотели сделать?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                maxLines = 10,
                enabled = !uiState.isLoading,
                isError = uiState.errorMessage != null,
                supportingText = {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Минимум 10 символов")
                    }
                }
            )

            // Информация о собираемых данных
            DataCollectionInfo()

            // Кнопка отправки
            Button(
                onClick = { viewModel.submitBugReport() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.userDescription.length >= 10
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isLoading) "Отправка..." else "Отправить отчет")
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Помогите нам стать лучше",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Опишите проблему максимально подробно. Это поможет нам быстрее её исправить.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun DataCollectionInfo() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Что будет отправлено:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        DataCollectionItem(
            icon = Icons.Default.Description,
            text = "Ваше описание проблемы"
        )
        DataCollectionItem(
            icon = Icons.Default.Smartphone,
            text = "Информация об устройстве (модель, версия Android)"
        )
        DataCollectionItem(
            icon = Icons.Default.Analytics,
            text = "Логи приложения (последние 200 записей)"
        )
        DataCollectionItem(
            icon = Icons.Default.Person,
            text = "Ваш email для обратной связи"
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Мы не собираем персональные данные о ваших автомобилях и расходах.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun DataCollectionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}