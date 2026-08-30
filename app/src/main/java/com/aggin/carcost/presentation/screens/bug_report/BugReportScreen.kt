package com.aggin.carcost.presentation.screens.bug_report

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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
                title = { Text(stringResource(R.string.bugreport_soobschit_ob_oshibke)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
                label = { Text(stringResource(R.string.bugreport_opishite_problemu)) },
                placeholder = { Text(stringResource(R.string.bugreport_chto_poshlo_ne_tak_chto_vy_hoteli_sdelat)) },
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
                        Text(stringResource(R.string.bugreport_minimum_10_simvolov))
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
                Text(if (uiState.isLoading) stringResource(R.string.bugreport_otpravka) else stringResource(R.string.bugreport_otpravit_otchet))
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
                // Было «Помогите нам стать лучше» и «Опишите проблему максимально
                // подробно» — первое ничего не сообщает, второе дословно повторяет
                // подпись поля ниже. Вместо этого — что писать, чтобы по отчёту
                // можно было воспроизвести ошибку
                Text(
                    text = stringResource(R.string.bugreport_kak_opisat_oshibku),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bugreport_chto_vy_delali_chego_ozhidali_i_chto) +
                        stringResource(R.string.bugreport_esli_poluchaetsya_povtorit_napishite),
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
            text = stringResource(R.string.bugreport_chto_budet_otpravleno),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        DataCollectionItem(
            icon = Icons.Default.Description,
            text = stringResource(R.string.bugreport_vashe_opisanie_problemy)
        )
        DataCollectionItem(
            icon = Icons.Default.Smartphone,
            text = stringResource(R.string.bugreport_informatsiya_ob_ustroystve_model_versiya)
        )
        DataCollectionItem(
            icon = Icons.Default.Analytics,
            text = stringResource(R.string.bugreport_logi_prilozheniya_poslednie_200_zapisey)
        )
        DataCollectionItem(
            icon = Icons.Default.Person,
            text = stringResource(R.string.bugreport_vash_email_dlya_obratnoy_svyazi)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.bugreport_my_ne_sobiraem_personalnye_dannye_o),
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