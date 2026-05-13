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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🤖 CarBot", fontWeight = FontWeight.Bold)
                        if (uiState.cars.size > 1 && uiState.selectedCarId != null) {
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
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Car selector if multiple cars
                    if (uiState.cars.size > 1) {
                        var expanded by remember { mutableStateOf(false) }
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                uiState.cars.firstOrNull { it.id == uiState.selectedCarId }
                                    ?.let { "${it.brand} ${it.model}" } ?: "Авто",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            uiState.cars.forEach { car ->
                                DropdownMenuItem(
                                    text = { Text("${car.brand} ${car.model}") },
                                    onClick = {
                                        viewModel.selectCar(car.id)
                                        expanded = false
                                    }
                                )
                            }
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
            // Messages list
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

            // Quick suggestions (visible only when very few messages)
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
                            label = {
                                Text(suggestion, fontSize = 12.sp, maxLines = 1)
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Input row
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
            // Render markdown-style **bold** text
            MarkdownText(text = message.text, color = textColor)
        }
    }
}

@Composable
private fun MarkdownText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    // Simple **bold** support using AnnotatedString
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
