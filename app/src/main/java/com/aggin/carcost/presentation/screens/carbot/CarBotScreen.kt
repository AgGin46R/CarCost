package com.aggin.carcost.presentation.screens.carbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

private val suggestions = listOf(
    "💰 Расходы за месяц",
    "🔧 Когда масло?",
    "⛽ Средний расход",
    "📋 Последние расходы",
    "📊 Самый дорогой месяц",
    "💵 Всего потратил",
    "📍 Поездки GPS",
    "💳 Бюджет",
    "🛡 Страховка",
    "🚗 Инфо об авто"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarBotScreen(navController: NavController) {
    val viewModel: CarBotViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAiSetupDialog by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // AI setup dialog
    if (showAiSetupDialog) {
        AlertDialog(
            onDismissRequest = { showAiSetupDialog = false },
            title = { Text("Подключить AI-модель") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Локальная AI-модель Gemma 2B (~1 ГБ). Работает полностью офлайн.")
                    if (GemmaModelManager.hasDirectUrl) {
                        Text(
                            "Нажмите «Скачать» — модель загрузится автоматически.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Ссылка для скачивания ещё не настроена. Обновите приложение позже.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (GemmaModelManager.hasDirectUrl) {
                    Button(
                        onClick = {
                            showAiSetupDialog = false
                            viewModel.downloadModel(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("⬇ Скачать (~1 ГБ)") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiSetupDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // Download progress dialog
    if (uiState.isDownloadingModel) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка AI-модели...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemma 2B скачивается (~1 ГБ). Не закрывайте приложение.")
                    Spacer(Modifier.height(4.dp))
                    if (uiState.modelDownloadProgress > 0) {
                        LinearProgressIndicator(
                            progress = { uiState.modelDownloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${uiState.modelDownloadProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🤖 CarBot", fontWeight = FontWeight.Bold)
                        when {
                            uiState.isModelReady ->
                                Text(
                                    "✨ AI активен",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            uiState.isModelInitializing ->
                                Text(
                                    "⏳ AI запускается...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            uiState.modelInitError != null ->
                                Text(
                                    "⚠️ AI: ошибка запуска",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            uiState.cars.size > 1 && uiState.selectedCarId != null -> {
                                val car = uiState.cars.firstOrNull { it.id == uiState.selectedCarId }
                                if (car != null) {
                                    Text(
                                        "${car.brand} ${car.model}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // AI setup button (when not downloaded and not downloading)
                    if (!uiState.isModelDownloaded && !uiState.isDownloadingModel) {
                        TextButton(onClick = { showAiSetupDialog = true }) {
                            Text("✨ AI", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Retry button when engine init failed
                    if (uiState.modelInitError != null && !uiState.isModelInitializing) {
                        TextButton(onClick = { viewModel.retryInitGemma(context) }) {
                            Text(
                                "↺ AI",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 3-dot menu: car selector + delete model
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (uiState.cars.size > 1) {
                            uiState.cars.forEach { car ->
                                DropdownMenuItem(
                                    text = {
                                        val selected = car.id == uiState.selectedCarId
                                        Text(
                                            "${if (selected) "✓ " else ""}${car.brand} ${car.model}",
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectCar(car.id)
                                        menuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                        }
                        if (uiState.isModelDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Удалить AI-модель") },
                                onClick = {
                                    viewModel.deleteModel()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (uiState.isProcessing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            TypingIndicator()
                        }
                    }
                }
            }

            if (uiState.messages.size <= 1 && !uiState.isProcessing) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.sendSuggestion(suggestion) },
                            label = { Text(suggestion, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Спросите что-нибудь…", fontSize = 14.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                IconButton(
                    onClick = { viewModel.sendMessage(uiState.inputText) },
                    enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (uiState.inputText.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (uiState.inputText.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: BotMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isFromUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isFromUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isFromUser)
        RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
    else
        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MarkdownText(text = message.text, color = textColor)
            if (message.isAiGenerated) {
                Text(
                    "✨",
                    modifier = Modifier.align(Alignment.TopEnd),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val annotated = buildBoldAnnotatedString(text, color)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
        modifier = modifier
    )
}

private fun buildBoldAnnotatedString(
    text: String,
    baseColor: androidx.compose.ui.graphics.Color
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = baseColor
                    )
                )
                builder.append(text.substring(i + 2, end))
                builder.pop()
                i = end + 2
                continue
            }
        }
        builder.append(text[i])
        i++
    }
    return builder.toAnnotatedString()
}

@Composable
private fun TypingIndicator() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}
