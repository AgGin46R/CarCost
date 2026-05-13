package com.aggin.carcost.presentation.screens.fluid_levels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.FluidType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidLevelsScreen(
    navController: NavController,
    carId: String
) {
    val context = LocalContext.current
    val viewModel: FluidLevelsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FluidLevelsViewModel(context.applicationContext as android.app.Application, carId) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уровни жидкостей") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Отслеживайте уровни технических жидкостей вашего автомобиля",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(uiState.items) { item ->
                    FluidLevelCard(
                        item = item,
                        onUpdate = { viewModel.openUpdateDialog(item.type) }
                    )
                }
            }
        }

        // Диалог обновления уровня
        if (uiState.showUpdateDialog && uiState.dialogFluidType != null) {
            FluidUpdateDialog(
                fluidType = uiState.dialogFluidType!!,
                initialLevel = uiState.items
                    .firstOrNull { it.type == uiState.dialogFluidType }
                    ?.existing?.level ?: 0.5f,
                initialNotes = uiState.items
                    .firstOrNull { it.type == uiState.dialogFluidType }
                    ?.existing?.notes ?: "",
                onConfirm = { level, notes ->
                    viewModel.saveFluidLevel(uiState.dialogFluidType!!, level, notes)
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
    }
}

@Composable
private fun FluidLevelCard(
    item: FluidLevelItem,
    onUpdate: () -> Unit
) {
    val levelColor = when {
        item.existing == null -> MaterialTheme.colorScheme.outline
        item.existing.level > 0.6f -> Color(0xFF4CAF50)
        item.existing.level > 0.3f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок + кнопка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = item.type.emoji, fontSize = 22.sp)
                    Column {
                        Text(
                            text = item.type.labelRu,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.existing != null) {
                            Text(
                                text = "Проверено ${dateFormat.format(Date(item.existing.checkedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Ещё не проверялось",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.isOverdue) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Требует проверки",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(onClick = onUpdate) {
                        Text("Обновить")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Прогресс-бар уровня
            val level = item.existing?.level ?: 0f
            val levelPct = (level * 100).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { level },
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = levelColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = if (item.existing != null) "$levelPct%" else "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Заметка
            if (!item.existing?.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.existing!!.notes!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Предупреждение об устаревшей проверке
            if (item.isOverdue && item.existing != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠ Рекомендуется проверить (интервал ${item.type.checkIntervalDays} дней)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluidUpdateDialog(
    fluidType: FluidType,
    initialLevel: Float,
    initialNotes: String,
    onConfirm: (Float, String) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(initialLevel) }
    var notes by remember { mutableStateOf(initialNotes) }

    val levelColor = when {
        sliderValue > 0.6f -> Color(0xFF4CAF50)
        sliderValue > 0.3f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${fluidType.emoji} ${fluidType.labelRu}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Укажите текущий уровень: ${(sliderValue * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = levelColor,
                    fontWeight = FontWeight.SemiBold
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..1f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = levelColor,
                        activeTrackColor = levelColor
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0.25f to "25%", 0.5f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (v, label) ->
                        FilterChip(
                            selected = sliderValue == v,
                            onClick = { sliderValue = v },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметка (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(sliderValue, notes) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
