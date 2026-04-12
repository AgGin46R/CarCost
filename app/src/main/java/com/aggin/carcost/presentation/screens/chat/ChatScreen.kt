package com.aggin.carcost.presentation.screens.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ChatMessage
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseChatRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ────────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val carName: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val currentUserId: String = "",
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    // message.id → playback progress 0f..1f
    val playbackProgress: Map<String, Float> = emptyMap(),
    // currently playing message id
    val playingMessageId: String? = null,
    val replyingTo: ChatMessage? = null
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

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFilePath: String? = null
    private val mediaPlayers = mutableMapOf<String, MediaPlayer>()

    init {
        val currentUserId = auth.getUserId() ?: ""

        // Корутина 1: Room Flow — стартует сразу, независимо от REST-запроса
        viewModelScope.launch {
            db.chatMessageDao().getMessagesByCarId(carId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }

        // Корутина 2: setup + первичная загрузка из Supabase (один раз при старте)
        // Далее все обновления приходят через RealtimeSyncManager WebSocket
        viewModelScope.launch {
            val carEntity = db.carDao().getCarById(carId)
            val carName = carEntity?.let { "${it.brand} ${it.model}" } ?: ""
            _uiState.update { it.copy(currentUserId = currentUserId, carName = carName) }
            supabaseChat.getMessages(carId).onSuccess { remote ->
                if (remote.isNotEmpty()) db.chatMessageDao().insertAll(remote)
            }
        }
        // Polling удалён: Realtime WebSocket в RealtimeSyncManager обновляет chat_messages
        // в реальном времени без лишних HTTP-запросов
    }

    /** Принудительно обновить сообщения из Supabase (вызывается при ON_RESUME). */
    fun refreshMessages() {
        viewModelScope.launch {
            supabaseChat.getMessages(carId).onSuccess { remote ->
                if (remote.isNotEmpty()) db.chatMessageDao().insertAll(remote)
            }
        }
    }

    fun setReplyTo(message: ChatMessage?) = _uiState.update { it.copy(replyingTo = message) }

    /** Send a text message, optionally with an image. */
    fun sendMessage(text: String, mediaUri: Uri? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && mediaUri == null) return
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""
        val replyTo = _uiState.value.replyingTo

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, replyingTo = null) }
            try {
                val messageId = UUID.randomUUID().toString()
                var mediaUrl: String? = null

                if (mediaUri != null) {
                    val bytes = compressImage(mediaUri)
                    if (bytes != null) {
                        mediaUrl = supabaseChat.uploadMedia(carId, messageId, bytes, "jpg", "image/jpeg").getOrNull()
                    }
                }

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = trimmed,
                    mediaUrl = mediaUrl,
                    mediaType = if (mediaUrl != null) "image" else null,
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.message?.take(100)?.ifBlank { replyTo.fileName }
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Send a file (pdf, doc, etc.) */
    fun sendFile(uri: Uri, fileName: String) {
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@launch
                val extension = fileName.substringAfterLast('.', "bin")
                val mimeType = mimeTypeForExtension(extension)
                val messageId = UUID.randomUUID().toString()

                val mediaUrl = supabaseChat.uploadMedia(carId, messageId, bytes, extension, mimeType).getOrNull()

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = "",
                    mediaUrl = mediaUrl,
                    mediaType = "file",
                    fileName = fileName
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Start audio recording. */
    fun startRecording(context: Context) {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            recordingFilePath = file.absolutePath
            mediaRecorder = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _uiState.update { it.copy(isRecording = true, recordingDurationSeconds = 0) }
            viewModelScope.launch {
                while (_uiState.value.isRecording) {
                    delay(1000)
                    _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
                }
            }
        } catch (e: Exception) {
            cancelRecording()
        }
    }

    /** Stop recording and send the voice message. */
    fun stopAndSendRecording() {
        val path = recordingFilePath ?: run { cancelRecording(); return }
        val durationSec = _uiState.value.recordingDurationSeconds
        val userId = auth.getUserId() ?: run { cancelRecording(); return }
        val email = auth.getCurrentUserEmail() ?: ""

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) { }
        mediaRecorder?.release()
        mediaRecorder = null
        _uiState.update { it.copy(isRecording = false) }

        if (durationSec < 1) {
            File(path).delete()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val bytes = File(path).readBytes()
                File(path).delete()
                val messageId = UUID.randomUUID().toString()
                val mediaUrl = supabaseChat.uploadMedia(
                    carId, messageId, bytes, "m4a", "audio/m4a"
                ).getOrNull()

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = "",
                    mediaUrl = mediaUrl,
                    mediaType = "audio",
                    fileName = "voice_${durationSec}s.m4a"
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Cancel recording without sending. */
    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) { }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingFilePath?.let { File(it).delete() }
        recordingFilePath = null
        _uiState.update { it.copy(isRecording = false, recordingDurationSeconds = 0) }
    }

    /** Send a video note (кружок) — short circular MP4 video. */
    fun sendVideoNote(videoFile: File, durationSec: Int) {
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val bytes = videoFile.readBytes()
                videoFile.delete()
                val messageId = UUID.randomUUID().toString()
                val uploadResult = supabaseChat.uploadMedia(
                    carId, messageId, bytes, "mp4", "video/mp4"
                )
                val mediaUrl = uploadResult.getOrNull()
                if (mediaUrl == null) {
                    android.util.Log.e("VideoNote", "upload failed: ${uploadResult.exceptionOrNull()?.message}")
                    return@launch
                }
                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = "",
                    mediaUrl = mediaUrl,
                    mediaType = "video_note",
                    fileName = "video_${durationSec}s.mp4"
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Toggle playback for a voice message. */
    fun togglePlayback(message: ChatMessage) {
        val url = message.mediaUrl ?: return
        val currentlyPlayingId = _uiState.value.playingMessageId

        if (currentlyPlayingId == message.id) {
            // Pause/resume
            mediaPlayers[message.id]?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _uiState.update { it.copy(playingMessageId = null) }
                } else {
                    player.start()
                    _uiState.update { it.copy(playingMessageId = message.id) }
                    trackPlayback(message.id, player)
                }
            }
            return
        }

        // Stop previous player
        currentlyPlayingId?.let { stopPlayer(it) }

        viewModelScope.launch {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(url)
                    prepareAsync()
                    setOnPreparedListener {
                        it.start()
                        _uiState.update { s -> s.copy(playingMessageId = message.id) }
                        trackPlayback(message.id, it)
                    }
                    setOnCompletionListener {
                        _uiState.update { s ->
                            s.copy(
                                playingMessageId = null,
                                playbackProgress = s.playbackProgress - message.id
                            )
                        }
                        mediaPlayers.remove(message.id)?.release()
                    }
                    setOnErrorListener { _, _, _ ->
                        _uiState.update { s -> s.copy(playingMessageId = null) }
                        true
                    }
                }
                mediaPlayers[message.id] = player
            } catch (_: Exception) {
                _uiState.update { it.copy(playingMessageId = null) }
            }
        }
    }

    private fun trackPlayback(messageId: String, player: MediaPlayer) {
        viewModelScope.launch {
            while (mediaPlayers[messageId] == player && player.isPlaying) {
                val duration = player.duration
                if (duration > 0) {
                    val progress = player.currentPosition.toFloat() / duration
                    _uiState.update { it.copy(playbackProgress = it.playbackProgress + (messageId to progress)) }
                }
                delay(100)
            }
        }
    }

    private fun stopPlayer(messageId: String) {
        mediaPlayers.remove(messageId)?.let { player ->
            try { player.stop() } catch (_: Exception) { }
            player.release()
        }
        _uiState.update { it.copy(playingMessageId = null) }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            val result = supabaseChat.deleteMessage(message.id)
            if (result.isSuccess) {
                db.chatMessageDao().deleteById(message.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayers.values.forEach { try { it.release() } catch (_: Exception) { } }
        mediaPlayers.clear()
        try { mediaRecorder?.release() } catch (_: Exception) { }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            val maxDim = 1200
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                Bitmap.createScaledBitmap(original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true)
            } else original
            ByteArrayOutputStream().also { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, out) }.toByteArray()
        } catch (_: Exception) { null }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showVideoRecorder by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val audioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingMediaUri = uri
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingFileUri = uri
            pendingFileName = getFileName(context, uri)
        }
    }

    DisposableEffect(carId) {
        ActiveChatTracker.activeCarId = carId
        onDispose { ActiveChatTracker.activeCarId = null }
    }

    // Сбрасываем счётчик непрочитанных при открытии чата
    LaunchedEffect(carId) {
        val settingsManager = SettingsManager(context)
        settingsManager.setLastChatSeen(carId)
    }

    // Обновляем сообщения при каждом возврате на экран
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMessages()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                isRecording = uiState.isRecording,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                pendingMediaUri = pendingMediaUri,
                pendingFileName = pendingFileName,
                replyingTo = uiState.replyingTo,
                onTextChange = { inputText = it },
                onAttachClick = { showAttachSheet = true },
                onRemoveImage = { pendingMediaUri = null },
                onRemoveFile = { pendingFileUri = null; pendingFileName = null },
                onCancelReply = { viewModel.setReplyTo(null) },
                onMicClick = {
                    if (audioPermission.status.isGranted) {
                        viewModel.startRecording(context)
                    } else {
                        audioPermission.launchPermissionRequest()
                    }
                },
                onVideoNoteClick = {
                    when {
                        !cameraPermission.status.isGranted -> cameraPermission.launchPermissionRequest()
                        !audioPermission.status.isGranted -> audioPermission.launchPermissionRequest()
                        else -> showVideoRecorder = true
                    }
                },
                onCancelRecording = { viewModel.cancelRecording() },
                onStopAndSend = { viewModel.stopAndSendRecording() },
                onSend = {
                    when {
                        pendingFileUri != null && pendingFileName != null -> {
                            viewModel.sendFile(pendingFileUri!!, pendingFileName!!)
                            pendingFileUri = null
                            pendingFileName = null
                        }
                        else -> {
                            viewModel.sendMessage(inputText, pendingMediaUri)
                            inputText = ""
                            pendingMediaUri = null
                        }
                    }
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
                    Text("Нет сообщений", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Напишите первым!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            isPlaying = uiState.playingMessageId == message.id,
                            playbackProgress = uiState.playbackProgress[message.id] ?: 0f,
                            onDelete = if (isMe) ({ viewModel.deleteMessage(message) }) else null,
                            onReply = { viewModel.setReplyTo(message) },
                            onImageClick = { url -> fullscreenImageUrl = url },
                            onPlayPause = { viewModel.togglePlayback(message) },
                            onOpenFile = { url, name -> openFile(context, url, name) }
                        )
                    }
                }
            }
        }
    }

    // Attach bottom sheet
    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text("Фото из галереи") },
                modifier = Modifier.clickable {
                    imagePicker.launch("image/*")
                    showAttachSheet = false
                }
            )
            ListItem(
                leadingContent = { Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.secondary) },
                headlineContent = { Text("Документ") },
                supportingContent = { Text("PDF, Word, TXT, JSON, PPTX") },
                modifier = Modifier.clickable {
                    filePicker.launch(arrayOf(
                        "application/pdf",
                        "text/plain",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "application/json"
                    ))
                    showAttachSheet = false
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // Video note recorder
    if (showVideoRecorder) {
        VideoNoteRecorderSheet(
            onDismiss = { showVideoRecorder = false },
            onVideoRecorded = { file, durationSec ->
                showVideoRecorder = false
                viewModel.sendVideoNote(file, durationSec)
            }
        )
    }

    // Fullscreen image viewer
    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImageUrl = null },
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

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    isSending: Boolean,
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    pendingMediaUri: Uri?,
    pendingFileName: String?,
    replyingTo: ChatMessage?,
    onTextChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveFile: () -> Unit,
    onCancelReply: () -> Unit,
    onMicClick: () -> Unit,
    onVideoNoteClick: () -> Unit,
    onCancelRecording: () -> Unit,
    onStopAndSend: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = (text.isNotBlank() || pendingMediaUri != null || pendingFileName != null) && !isSending
    val showMic = text.isBlank() && pendingMediaUri == null && pendingFileName == null

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
        ) {
            if (isRecording) {
                // ── Recording mode UI ──────────────────────────────────────────
                RecordingBar(
                    durationSeconds = recordingDurationSeconds,
                    onCancel = onCancelRecording,
                    onStop = onStopAndSend
                )
            } else {
                // ── Reply preview ──────────────────────────────────────────────
                if (replyingTo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            replyingTo.message.take(80),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = onCancelReply, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider()
                }

                // ── Pending image preview ──────────────────────────────────────
                if (pendingMediaUri != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = pendingMediaUri,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Фото прикреплено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = onRemoveImage) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    HorizontalDivider()
                }

                // ── Pending file preview ───────────────────────────────────────
                if (pendingFileName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            fileIcon(pendingFileName),
                            null,
                            tint = fileColor(pendingFileName),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            pendingFileName,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = onRemoveFile) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    HorizontalDivider()
                }

                // ── Input row ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onAttachClick, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
                    )

                    if (showMic) {
                        FilledIconButton(onClick = onMicClick, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Mic, "Голосовое сообщение")
                        }
                        FilledTonalIconButton(onClick = onVideoNoteClick, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Videocam, "Видеосообщение")
                        }
                    } else {
                        FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp)) {
                            if (isSending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBar(
    durationSeconds: Int,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, "Отменить запись", tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.scale(scale).size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(durationSeconds),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.weight(1f))
            FilledIconButton(
                onClick = onStop,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, "Остановить запись", tint = Color.White)
            }
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onDelete: (() -> Unit)?,
    onReply: () -> Unit,
    onImageClick: (String) -> Unit,
    onPlayPause: () -> Unit,
    onOpenFile: (String, String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }

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

    if (showContextMenu) {
        ModalBottomSheet(onDismissRequest = { showContextMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Ответить") },
                    leadingContent = { Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showContextMenu = false; onReply() }
                )
                if (message.message.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Копировать") },
                        leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                        modifier = Modifier.clickable {
                            showContextMenu = false
                            clipboard.setText(AnnotatedString(message.message))
                        }
                    )
                }
                if (onDelete != null) {
                    ListItem(
                        headlineContent = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { showContextMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    // Swipe-to-reply container
    Box(modifier = Modifier.fillMaxWidth()) {
        // Reply icon appears as user swipes
        if (offsetX.value > 8f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                modifier = Modifier
                    .align(if (isMe) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 12.dp)
                    .alpha((offsetX.value / swipeThresholdPx).coerceIn(0f, 1f))
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(if (isMe) -offsetX.value.roundToInt() else offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    coroutineScope {
                        launch {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetX.value >= swipeThresholdPx) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onReply()
                                        }
                                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val delta = if (isMe) -dragAmount else dragAmount
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + delta).coerceIn(0f, swipeThresholdPx * 1.3f))
                                    }
                                }
                            )
                        }
                        launch {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showContextMenu = true
                                }
                            )
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
            ) {
                if (!isMe) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(avatarColor(message.userEmail)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(message.userEmail.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    val bubbleShape = RoundedCornerShape(
                        topStart = if (isMe) 16.dp else 4.dp,
                        topEnd = if (isMe) 4.dp else 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp
                    )
                    val bubbleBg = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val onBubble = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    when (message.mediaType) {
                        "audio" -> {
                            Box(
                                modifier = Modifier.clip(bubbleShape).background(bubbleBg).padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 180.dp)
                            ) {
                                Column {
                                    // Reply quote
                                    if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                null, tint = onBubble
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        WaveformDecoration(isMe = isMe, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(6.dp))
                                        Text(parseDurationFromFileName(message.fileName), fontSize = 12.sp, color = onBubble)
                                    }
                                    if (isPlaying) {
                                        LinearProgressIndicator(
                                            progress = { playbackProgress },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            color = onBubble.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        "file" -> {
                            Card(
                                shape = bubbleShape,
                                colors = CardDefaults.cardColors(containerColor = bubbleBg),
                                modifier = Modifier.clickable {
                                    message.mediaUrl?.let { url -> onOpenFile(url, message.fileName ?: "file") }
                                }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).widthIn(min = 160.dp)) {
                                    if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(fileIcon(message.fileName ?: ""), null, tint = if (isMe) onBubble else fileColor(message.fileName ?: ""), modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(message.fileName ?: "Файл", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onBubble)
                                            Text("Нажмите, чтобы открыть", fontSize = 11.sp, color = onBubble.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }

                        "video_note" -> {
                            VideoNoteBubble(
                                url = message.mediaUrl ?: "",
                                durationLabel = parseDurationFromFileName(message.fileName)
                            )
                        }

                        else -> {
                            message.mediaUrl?.let { url ->
                                if (message.mediaType == null || message.mediaType == "image") {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Фото",
                                        modifier = Modifier.widthIn(max = 240.dp).clip(bubbleShape).clickable { onImageClick(url) },
                                        contentScale = ContentScale.FillWidth
                                    )
                                    if (message.message.isNotBlank()) Spacer(Modifier.height(4.dp))
                                }
                            }
                            if (message.message.isNotBlank() || message.replyToId != null) {
                                Box(modifier = Modifier.clip(bubbleShape).background(bubbleBg).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Column {
                                        if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble)
                                        if (message.message.isNotBlank()) {
                                            Text(message.message, color = onBubble, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    ) {
                        Text(timeFmt.format(Date(message.createdAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(text: String?, contentColor: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(modifier = Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(6.dp))
        Text(
            text ?: "Сообщение",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.75f)
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun WaveformDecoration(isMe: Boolean, modifier: Modifier = Modifier) {
    val heights = remember { listOf(0.4f, 0.7f, 1.0f, 0.6f, 0.9f, 0.5f, 0.8f, 0.3f, 0.7f, 0.5f) }
    val color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun parseDurationFromFileName(fileName: String?): String {
    if (fileName == null) return "0:00"
    val match = Regex("(?:voice|video)_(\\d+)s\\.(?:m4a|mp4)").find(fileName)
    val sec = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return formatDuration(sec)
}

private fun fileIcon(fileName: String): ImageVector {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Description
        "pptx", "ppt" -> Icons.Default.Slideshow
        "json" -> Icons.Default.Code
        "txt" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}

private fun fileColor(fileName: String): Color {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "pdf" -> Color(0xFFE53935)
        "doc", "docx" -> Color(0xFF1565C0)
        "pptx", "ppt" -> Color(0xFFD84315)
        "json" -> Color(0xFF558B2F)
        "txt" -> Color(0xFF546E7A)
        else -> Color(0xFF78909C)
    }
}

private fun mimeTypeForExtension(ext: String): String {
    return when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "json" -> "application/json"
        "txt" -> "text/plain"
        "m4a" -> "audio/m4a"
        else -> "application/octet-stream"
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "file"
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx)
        }
    }
    return name
}

private fun openFile(context: Context, url: String, fileName: String) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val downloadsDir = File(context.cacheDir, "downloads").also { it.mkdirs() }
            val file = File(downloadsDir, fileName)
            if (!file.exists()) {
                val bytes = URL(url).readBytes()
                file.writeBytes(bytes)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val ext = fileName.substringAfterLast('.', "")
            val mime = mimeTypeForExtension(ext)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

// ── Video Note Recorder ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VideoNoteRecorderSheet(
    onDismiss: () -> Unit,
    onVideoRecorded: (File, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    // True only after ProcessCameraProvider finishes binding — prevents null-vc taps
    var isCameraReady by remember { mutableStateOf(false) }

    // Stop recording and cleanup when sheet is dismissed
    DisposableEffect(Unit) {
        onDispose { recording?.stop() }
    }

    // Elapsed timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && elapsedSec < 60) {
                delay(1_000)
                elapsedSec++
                if (elapsedSec >= 60) recording?.stop()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            recording?.stop()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when {
                    isRecording -> formatDuration(elapsedSec)
                    !isCameraReady -> "Инициализация камеры..."
                    else -> "Нажмите для записи"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            // Circular CameraX preview
            // IMPORTANT: COMPATIBLE mode forces TextureView (instead of SurfaceView),
            // which renders inside the Compose layer and correctly respects clip(CircleShape).
            // SurfaceView (PERFORMANCE mode) renders on a separate hardware layer that
            // ignores Compose clipping → black or square preview.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            // TextureView — respects Compose clipping → proper circle
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    val recorder = Recorder.Builder()
                                        .setQualitySelector(QualitySelector.from(Quality.SD))
                                        .build()
                                    val vc = VideoCapture.withOutput(recorder)
                                    cameraProvider.unbindAll()
                                    // Try front camera first (selfie-style), fall back to back
                                    val selector = try {
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview, vc
                                        )
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } catch (_: Exception) {
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview, vc
                                        )
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }
                                    videoCapture = vc
                                    isCameraReady = true
                                } catch (e: Exception) {
                                    android.util.Log.e("VideoNote", "Camera bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Loading overlay until camera is ready
                if (!isCameraReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Record / Stop button with circular progress ring (60 sec limit)
            Box(contentAlignment = Alignment.Center) {
                if (isRecording) {
                    CircularProgressIndicator(
                        progress = { elapsedSec / 60f },
                        modifier = Modifier.size(88.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                }
                FilledIconButton(
                    onClick = {
                        if (isRecording) {
                            recording?.stop()
                        } else {
                            val vc = videoCapture ?: return@FilledIconButton
                            elapsedSec = 0
                            val file = File(context.cacheDir, "video_note_${System.currentTimeMillis()}.mp4")
                            val outputOptions = FileOutputOptions.Builder(file).build()
                            recording = vc.output
                                .prepareRecording(context, outputOptions)
                                .withAudioEnabled()
                                .start(ContextCompat.getMainExecutor(context)) { event ->
                                    when (event) {
                                        is VideoRecordEvent.Start -> isRecording = true
                                        is VideoRecordEvent.Finalize -> {
                                            isRecording = false
                                            if (!event.hasError()) {
                                                onVideoRecorded(file, elapsedSec)
                                            } else {
                                                file.delete()
                                                onDismiss()
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                        }
                    },
                    enabled = isCameraReady,
                    modifier = Modifier.size(72.dp),
                    colors = if (isRecording)
                        IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Остановить" else "Записать",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ── Video Note Bubble ─────────────────────────────────────────────────────────

@Composable
fun VideoNoteBubble(url: String, durationLabel: String) {
    if (url.isBlank()) return

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var firstFrameReady by remember { mutableStateOf(false) }
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
    }

    // Extract first frame as thumbnail so there's no black screen while buffering
    LaunchedEffect(url) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(url, emptyMap())
                thumbnail = retriever.getFrameAtTime(
                    0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
            } catch (_: Exception) { }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) isPlaying = false
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onRenderedFirstFrame() {
                firstFrameReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).apply {
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: android.graphics.SurfaceTexture, w: Int, h: Int
                        ) {
                            exoPlayer.setVideoSurface(android.view.Surface(st))
                        }
                        override fun onSurfaceTextureDestroyed(
                            st: android.graphics.SurfaceTexture
                        ): Boolean {
                            exoPlayer.clearVideoSurface()
                            return true
                        }
                        override fun onSurfaceTextureSizeChanged(
                            st: android.graphics.SurfaceTexture, w: Int, h: Int
                        ) {}
                        override fun onSurfaceTextureUpdated(
                            st: android.graphics.SurfaceTexture
                        ) {}
                    }
                }
            },
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
                        exoPlayer.play()
                    }
                }
        )

        // Show thumbnail until ExoPlayer renders its first frame
        if (!firstFrameReady) {
            val bmp = thumbnail
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(160.dp).clip(CircleShape)
                )
            }
        }

        // Play button overlay when not playing
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        // Duration badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(durationLabel, fontSize = 11.sp, color = Color.White)
        }
    }
}
