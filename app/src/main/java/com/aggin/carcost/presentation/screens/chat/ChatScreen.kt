package com.aggin.carcost.presentation.screens.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ChatMessage
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ────────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val carName: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val currentUserId: String = ""
)

class ChatViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val auth = SupabaseAuthRepository()
    private val supabaseChat = SupabaseChatRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        val currentUserId = auth.getUserId() ?: ""
        viewModelScope.launch {
            val carEntity = db.carDao().getCarById(carId)
            val carName = carEntity?.let { "${it.brand} ${it.model}" } ?: ""
            _uiState.update { it.copy(currentUserId = currentUserId, carName = carName) }

            // Initial sync from Supabase
            supabaseChat.getMessages(carId).onSuccess { remote ->
                remote.forEach { db.chatMessageDao().insert(it) }
            }

            // Observe local DB (Realtime keeps it updated)
            db.chatMessageDao().getMessagesByCarId(carId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun sendMessage(text: String, mediaUri: Uri? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && mediaUri == null) return
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val messageId = UUID.randomUUID().toString()
                var mediaUrl: String? = null

                // Upload image if provided
                if (mediaUri != null) {
                    val bytes = compressImage(mediaUri)
                    if (bytes != null) {
                        mediaUrl = supabaseChat.uploadMedia(carId, messageId, bytes).getOrNull()
                    }
                }

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = trimmed,
                    mediaUrl = mediaUrl
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            val result = supabaseChat.deleteMessage(message.id)
            if (result.isSuccess) {
                db.chatMessageDao().deleteById(message.id)
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: return null
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            val maxDim = 1200
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt(),
                    (original.height * ratio).toInt(),
                    true
                )
            } else original
            ByteArrayOutputStream().also { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}

class ChatViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(app, carId) as T
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(carId: String, navController: NavController) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> pendingMediaUri = uri }

    // Suppress notifications while in this chat
    DisposableEffect(carId) {
        ActiveChatTracker.activeCarId = carId
        onDispose { ActiveChatTracker.activeCarId = null }
    }

    // ── Scroll management ──────────────────────────────────────────────────────
    // Track whether initial scroll to bottom has been done (instant, no animation)
    var hasScrolledInitially by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.messages.isNotEmpty() && !hasScrolledInitially) {
            val grouped = uiState.messages.groupByDate()
            val lastIndex = grouped.size + uiState.messages.size - 1
            listState.scrollToItem(lastIndex)
            hasScrolledInitially = true
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (hasScrolledInitially && uiState.messages.isNotEmpty()) {
            val grouped = uiState.messages.groupByDate()
            val lastIndex = grouped.size + uiState.messages.size - 1
            listState.animateScrollToItem(lastIndex)
        }
    }
    // ──────────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Чат", fontWeight = FontWeight.Bold)
                        if (uiState.carName.isNotBlank()) {
                            Text(
                                uiState.carName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                isSending = uiState.isSending,
                pendingMediaUri = pendingMediaUri,
                onTextChange = { inputText = it },
                onAttachImage = { imagePicker.launch("image/*") },
                onRemoveImage = { pendingMediaUri = null },
                onSend = {
                    viewModel.sendMessage(inputText, pendingMediaUri)
                    inputText = ""
                    pendingMediaUri = null
                    keyboard?.hide()
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Нет сообщений",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Напишите первым!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val grouped = uiState.messages.groupByDate()
                grouped.forEach { (dateLabel, msgs) ->
                    item(key = "date_$dateLabel") { DateSeparator(dateLabel) }
                    items(msgs, key = { it.id }) { message ->
                        val isMe = message.userId == uiState.currentUserId
                        ChatBubble(
                            message = message,
                            isMe = isMe,
                            onDelete = if (isMe) ({ viewModel.deleteMessage(message) }) else null,
                            onImageClick = { url -> fullscreenImageUrl = url }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen image viewer
    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Фото",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    isSending: Boolean,
    pendingMediaUri: Uri?,
    onTextChange: (String) -> Unit,
    onAttachImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = (text.isNotBlank() || pendingMediaUri != null) && !isSending

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Image preview strip
            if (pendingMediaUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = pendingMediaUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Фото прикреплено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemoveImage) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Удалить фото",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Attach image button
                IconButton(
                    onClick = onAttachImage,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Прикрепить фото",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    onDelete: (() -> Unit)?,
    onImageClick: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить сообщение?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete?.invoke() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColor(message.userEmail)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    message.userEmail.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text(
                    message.userEmail.substringBefore("@"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            // Image bubble (if has media)
            message.mediaUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Фото",
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(url) },
                    contentScale = ContentScale.FillWidth
                )
                if (message.message.isNotBlank()) Spacer(Modifier.height(4.dp))
            }

            // Text bubble (if has text)
            if (message.message.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = if (isMe) 16.dp else 4.dp,
                                topEnd = if (isMe) 4.dp else 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(
                            if (isMe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        message.message,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    timeFmt.format(Date(message.createdAt)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onDelete != null) {
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete, null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun List<ChatMessage>.groupByDate(): List<Pair<String, List<ChatMessage>>> {
    val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val today = keyFmt.format(Date())
    val yesterday = keyFmt.format(Date(System.currentTimeMillis() - 86_400_000))

    return groupBy { msg ->
        when (keyFmt.format(Date(msg.createdAt))) {
            today -> "Сегодня"
            yesterday -> "Вчера"
            else -> dateFmt.format(Date(msg.createdAt))
        }
    }.toList()
}

private fun avatarColor(email: String): Color {
    val colors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFD32F2F),
        Color(0xFF7B1FA2), Color(0xFFF57C00), Color(0xFF00796B),
        Color(0xFF5D4037), Color(0xFF0288D1)
    )
    return colors[email.hashCode().and(0x7FFFFFFF) % colors.size]
}
