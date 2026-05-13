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
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.aggin.carcost.data.remote.repository.SupabaseUserDto
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
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
import com.aggin.carcost.presentation.components.SkeletonChatList

// ── ViewModel ────────────────────────────────────────────────────────────────

data class MemberProfile(
    val userId: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val isOnline: Boolean,
    val role: String?
)

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
    val audioSpeed: Float = 1f,
    val replyingTo: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val searchQuery: String = "",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val pageOffset: Int = 0,
    val sendError: String? = null,
    // messageId → list of reactions
    val reactions: Map<String, List<com.aggin.carcost.data.local.database.entities.ChatReaction>> = emptyMap(),
    // Car members available for @mention (email → userId)
    val memberEmails: List<String> = emptyList(),
    // Filtered suggestion list while typing @...
    val mentionSuggestions: List<String> = emptyList(),
    // Users who sent a message in the last 10 minutes (by userId)
    val onlineUserIds: Set<String> = emptySet(),
    // userId → cached profile for member card
    val memberProfileCache: Map<String, MemberProfile> = emptyMap()
)

class ChatViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val auth = SupabaseAuthRepository()
    private val supabaseChat = SupabaseChatRepository()
    private val supabaseReactions = com.aggin.carcost.data.remote.repository.SupabaseChatReactionsRepository()

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
                // Recompute online status whenever messages change
                val threshold = System.currentTimeMillis() - ONLINE_THRESHOLD_MS
                val online = messages.filter { it.createdAt >= threshold }.map { it.userId }.toSet()
                _uiState.update { it.copy(onlineUserIds = online) }
            }
        }

        // Корутина 1b: Reactions Flow — пересобирается каждый раз при изменении messages.
        // Подписываемся на список ID и комбинируем с reactions DAO.
        viewModelScope.launch {
            _uiState
                .map { it.messages.map(com.aggin.carcost.data.local.database.entities.ChatMessage::id) }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) flowOf(emptyList())
                    else db.chatReactionDao().getReactionsForMessages(ids)
                }
                .collect { list ->
                    val grouped = list.groupBy { it.messageId }
                    _uiState.update { it.copy(reactions = grouped) }
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
            // Fetch all reactions for this car's messages
            supabaseReactions.getReactionsForCar(carId).onSuccess { remoteReactions ->
                remoteReactions.forEach { r -> try { db.chatReactionDao().insert(r) } catch (_: Exception) {} }
            }
        }
        // Polling удалён: Realtime WebSocket в RealtimeSyncManager обновляет chat_messages
        // в реальном времени без лишних HTTP-запросов

        // Load car members for @mention suggestions
        viewModelScope.launch {
            db.carMemberDao().getMembersByCarId(carId).collect { members ->
                _uiState.update { it.copy(memberEmails = members.map { m -> m.email }) }
            }
        }
    }

    /** Load and cache member profile for the profile card dialog. */
    fun fetchMemberProfile(userId: String, email: String) {
        if (_uiState.value.memberProfileCache.containsKey(userId)) return
        viewModelScope.launch {
            val online = _uiState.value.onlineUserIds.contains(userId)
            val localUser = db.userDao().getUserById(userId).firstOrNull()
            val member = db.carMemberDao().getMembersByCarId(carId).firstOrNull()
                ?.find { it.userId == userId }
            val profile = MemberProfile(
                userId = userId,
                email = email,
                displayName = localUser?.displayName,
                photoUrl = localUser?.photoUrl,
                isOnline = online,
                role = member?.role?.name
            )
            _uiState.update { it.copy(memberProfileCache = it.memberProfileCache + (userId to profile)) }
            // Try fetching from Supabase users table if not in local DB
            if (localUser == null) {
                try {
                    val remoteUser = com.aggin.carcost.supabase.from("users")
                        .select { filter { eq("id", userId) } }
                        .decodeSingleOrNull<com.aggin.carcost.data.remote.repository.SupabaseUserDto>()
                    if (remoteUser != null) {
                        val updated = profile.copy(
                            displayName = remoteUser.displayName,
                            photoUrl = remoteUser.photoUrl
                        )
                        _uiState.update { it.copy(memberProfileCache = it.memberProfileCache + (userId to updated)) }
                        db.userDao().insertUser(
                            com.aggin.carcost.data.local.database.entities.User(
                                uid = userId,
                                email = email,
                                displayName = remoteUser.displayName,
                                photoUrl = remoteUser.photoUrl
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /** Called each time the user changes the input text. If the last word starts with '@',
     *  populate mentionSuggestions for the autocomplete popup. */
    fun onInputChanged(text: String) {
        val lastWord = text.substringAfterLast(' ').substringAfterLast('\n')
        if (lastWord.startsWith("@") && lastWord.length > 1) {
            val query = lastWord.removePrefix("@").lowercase()
            val suggestions = _uiState.value.memberEmails.filter {
                it.lowercase().contains(query)
            }.take(5)
            _uiState.update { it.copy(mentionSuggestions = suggestions) }
        } else {
            _uiState.update { it.copy(mentionSuggestions = emptyList()) }
        }
    }

    /** Replace the current @-token with the chosen member email. */
    fun applyMentionSuggestion(currentText: String, email: String): String {
        val idx = currentText.lastIndexOf('@')
        val result = if (idx >= 0) currentText.substring(0, idx) + "@$email " else currentText
        _uiState.update { it.copy(mentionSuggestions = emptyList()) }
        return result
    }

    /** Принудительно обновить сообщения из Supabase (вызывается при ON_RESUME). */
    fun refreshMessages() {
        viewModelScope.launch {
            supabaseChat.getMessages(carId).onSuccess { remote ->
                if (remote.isNotEmpty()) db.chatMessageDao().insertAll(remote)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 40
        const val ONLINE_THRESHOLD_MS = 10 * 60 * 1000L   // 10 minutes
    }

    /** Подгрузить более старые сообщения при скролле вверх. */
    fun loadMoreMessages() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreMessages) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val totalCount = db.chatMessageDao().getMessagesByCarId(carId).first().size
            val offset = totalCount // skip what we already have
            supabaseChat.getMessagesPaged(carId, PAGE_SIZE, offset).onSuccess { older ->
                if (older.isNotEmpty()) {
                    db.chatMessageDao().insertAll(older)
                    _uiState.update { it.copy(hasMoreMessages = older.size == PAGE_SIZE) }
                } else {
                    _uiState.update { it.copy(hasMoreMessages = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(hasMoreMessages = false) }
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    fun setReplyTo(message: ChatMessage?) = _uiState.update { it.copy(replyingTo = message) }

    fun startEditing(message: ChatMessage) = _uiState.update { it.copy(editingMessage = message) }
    fun cancelEditing() = _uiState.update { it.copy(editingMessage = null) }
    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun submitEdit(newText: String) {
        val msg = _uiState.value.editingMessage ?: return
        val trimmed = newText.trim()
        if (trimmed.isBlank() || trimmed == msg.message) { cancelEditing(); return }
        _uiState.update { it.copy(editingMessage = null) }
        viewModelScope.launch {
            supabaseChat.updateMessage(msg.id, trimmed).onSuccess {
                val updated = msg.copy(message = trimmed, isEdited = true)
                db.chatMessageDao().insert(updated)
            }
        }
    }

    /** Safely read bytes from a content:// or file:// URI into a temp cache file.
     *  This avoids SecurityException when the ContentResolver permission expires
     *  between the picker callback and the actual read (e.g. on config change). */
    private fun readBytesFromUri(uri: Uri, extension: String = "tmp"): ByteArray? {
        return try {
            val app = getApplication<Application>()
            // Copy to temp file first — guarantees permission is held during the read
            val tmp = File(app.cacheDir, "upload_${System.currentTimeMillis()}.$extension")
            app.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: run {
                android.util.Log.e("ChatVM", "openInputStream returned null for $uri")
                return null
            }
            val bytes = tmp.readBytes()
            tmp.delete()
            bytes
        } catch (e: Exception) {
            android.util.Log.e("ChatVM", "readBytesFromUri failed: ${e.message}", e)
            null
        }
    }

    fun clearSendError() = _uiState.update { it.copy(sendError = null) }

    /** Send a text message, optionally with an image. */
    fun sendMessage(text: String, mediaUri: Uri? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && mediaUri == null) return
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""
        val replyTo = _uiState.value.replyingTo

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, replyingTo = null, sendError = null) }
            try {
                val messageId = UUID.randomUUID().toString()
                var mediaUrl: String? = null

                if (mediaUri != null) {
                    val raw = readBytesFromUri(mediaUri, "jpg")
                    if (raw == null) {
                        _uiState.update { it.copy(isSending = false, sendError = "Не удалось прочитать изображение") }
                        return@launch
                    }
                    // Compress the image (scale to 1200px max, JPEG 82%)
                    val bytes = try {
                        val original = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        if (original == null) raw else {
                            val maxDim = 1200
                            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
                            val scaled = if (ratio < 1f)
                                android.graphics.Bitmap.createScaledBitmap(original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true)
                            else original
                            val out = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                            original.recycle()
                            if (scaled !== original) scaled.recycle()
                            out.toByteArray()
                        }
                    } catch (_: Exception) { raw }
                    val uploadResult = supabaseChat.uploadMedia(carId, messageId, bytes, "jpg", "image/jpeg")
                    if (uploadResult.isFailure) {
                        _uiState.update { it.copy(isSending = false, sendError = "Ошибка загрузки фото: ${uploadResult.exceptionOrNull()?.message}") }
                        return@launch
                    }
                    mediaUrl = uploadResult.getOrNull()
                }

                // Extract @mentions from text
                val mentionPattern = Regex("@([\\w.@+-]+)")
                val mentionedEmails = mentionPattern.findAll(trimmed).map { it.groupValues[1] }.toSet()
                val mentionsJson = if (mentionedEmails.isNotEmpty())
                    "[${mentionedEmails.joinToString(",") { "\"$it\"" }}]" else null

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = trimmed,
                    mediaUrl = mediaUrl,
                    mediaType = if (mediaUrl != null) "image" else null,
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.message?.take(100)?.ifBlank { replyTo.fileName },
                    mentions = mentionsJson
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "sendMessage failed", e)
                _uiState.update { it.copy(sendError = "Ошибка отправки: ${e.message}") }
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
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val extension = fileName.substringAfterLast('.', "bin")
                val bytes = readBytesFromUri(uri, extension)
                if (bytes == null) {
                    _uiState.update { it.copy(isSending = false, sendError = "Не удалось прочитать файл") }
                    return@launch
                }
                val mimeType = mimeTypeForExtension(extension)
                val messageId = UUID.randomUUID().toString()

                val uploadResult = supabaseChat.uploadMedia(carId, messageId, bytes, extension, mimeType)
                if (uploadResult.isFailure) {
                    _uiState.update { it.copy(isSending = false, sendError = "Ошибка загрузки файла: ${uploadResult.exceptionOrNull()?.message}") }
                    return@launch
                }
                val mediaUrl = uploadResult.getOrNull()

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
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "sendFile failed", e)
                _uiState.update { it.copy(sendError = "Ошибка отправки файла: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Send a video file from gallery. */
    fun sendVideo(uri: Uri, context: Context) {
        val userId = auth.getUserId() ?: return
        val email = auth.getCurrentUserEmail() ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                // Determine MIME type first, before copying
                val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                val extension = when {
                    mimeType.contains("webm") -> "webm"
                    mimeType.contains("3gp") -> "3gp"
                    else -> "mp4"
                }
                // Normalise to mp4 if needed — bucket only allows video/mp4
                val uploadMime = if (extension != "mp4") "video/mp4" else mimeType
                val uploadExt = "mp4"

                val bytes = readBytesFromUri(uri, uploadExt)
                if (bytes == null) {
                    _uiState.update { it.copy(isSending = false, sendError = "Не удалось прочитать видео") }
                    return@launch
                }
                val messageId = UUID.randomUUID().toString()
                val uploadResult = supabaseChat.uploadMedia(carId, messageId, bytes, uploadExt, uploadMime)
                if (uploadResult.isFailure) {
                    _uiState.update { it.copy(isSending = false, sendError = "Ошибка загрузки видео: ${uploadResult.exceptionOrNull()?.message}") }
                    return@launch
                }
                val mediaUrl = uploadResult.getOrNull()

                val message = ChatMessage(
                    id = messageId,
                    carId = carId,
                    userId = userId,
                    userEmail = email,
                    message = "",
                    mediaUrl = mediaUrl,
                    mediaType = "video",
                    fileName = "video.mp4"
                )
                db.chatMessageDao().insert(message)
                supabaseChat.sendMessage(message)
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "sendVideo failed", e)
                _uiState.update { it.copy(sendError = "Ошибка отправки видео: ${e.message}") }
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
                        val speed = _uiState.value.audioSpeed
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && speed != 1f) {
                            try { it.playbackParams = it.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                        }
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

    /** Cycle audio playback speed: 1× → 1.5× → 2× → 1× */
    fun cycleAudioSpeed() {
        val next = when (_uiState.value.audioSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        _uiState.update { it.copy(audioSpeed = next) }
        val playingId = _uiState.value.playingMessageId ?: return
        mediaPlayers[playingId]?.let { player ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(next)
                } catch (_: Exception) {}
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

    /**
     * Toggle a reaction: if the current user already has this emoji on this
     * message, remove it; otherwise add it. Local DB is updated optimistically;
     * Supabase is then called and reverted on failure.
     */
    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUserId.ifEmpty { auth.getUserId() ?: return@launch }
            val userEmail = auth.getCurrentUserEmail() ?: return@launch
            val existing = db.chatReactionDao().findByUserAndEmoji(messageId, userId, emoji)
            if (existing != null) {
                // Remove
                db.chatReactionDao().deleteById(existing.id)
                supabaseReactions.removeReaction(existing.id).onFailure {
                    // revert
                    try { db.chatReactionDao().insert(existing) } catch (_: Exception) {}
                }
            } else {
                val reaction = com.aggin.carcost.data.local.database.entities.ChatReaction(
                    messageId = messageId,
                    userId = userId,
                    userEmail = userEmail,
                    emoji = emoji
                )
                try { db.chatReactionDao().insert(reaction) } catch (_: Exception) {}
                supabaseReactions.addReaction(reaction).onFailure {
                    try { db.chatReactionDao().deleteById(reaction.id) } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayers.values.forEach { try { it.release() } catch (_: Exception) { } }
        mediaPlayers.clear()
        try { mediaRecorder?.release() } catch (_: Exception) { }
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
    val screenScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    // userId to email — used to open profile card
    var selectedMemberProfile by remember { mutableStateOf<Pair<String, String>?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    // Show send errors via Snackbar
    LaunchedEffect(uiState.sendError) {
        val err = uiState.sendError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.clearSendError()
    }

    val audioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingMediaUri = uri
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingFileUri = uri
            pendingFileName = getFileName(context, uri)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendVideo(uri, context)
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

    // Подгрузка старых сообщений при скролле вверх
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 3 && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            viewModel.loadMoreMessages()
        }
    }

    // Pre-fill input when editing a message
    LaunchedEffect(uiState.editingMessage) {
        val editing = uiState.editingMessage
        if (editing != null) inputText = editing.message
        else if (uiState.replyingTo == null) { /* keep text when replying */ }
    }

    LaunchedEffect(uiState.messages.size) {
        if (hasScrolledInitially && uiState.messages.isNotEmpty()) {
            val grouped = uiState.messages.groupByDate()
            val lastIndex = grouped.size + uiState.messages.size - 1
            listState.animateScrollToItem(lastIndex)
        }
    }

    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Поиск по чату...") },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                }
                            )
                        } else {
                            Column {
                                Text("Чат", fontWeight = FontWeight.Bold)
                                if (uiState.carName.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            uiState.carName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Online indicator — show count of recently active users
                                        val onlineCount = uiState.onlineUserIds
                                            .count { it != uiState.currentUserId }
                                        if (onlineCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF4CAF50))
                                            )
                                            Text(
                                                text = "$onlineCount онлайн",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showSearch) {
                                showSearch = false
                                viewModel.setSearchQuery("")
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Поиск",
                                tint = if (showSearch) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
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
                editingMessage = uiState.editingMessage,
                onTextChange = { inputText = it; viewModel.onInputChanged(it) },
                mentionSuggestions = uiState.mentionSuggestions,
                onMentionSelected = { email ->
                    inputText = viewModel.applyMentionSuggestion(inputText, email)
                },
                onAttachClick = { showAttachSheet = true },
                onRemoveImage = { pendingMediaUri = null },
                onRemoveFile = { pendingFileUri = null; pendingFileName = null },
                onCancelReply = { viewModel.setReplyTo(null) },
                onCancelEdit = { viewModel.cancelEditing(); inputText = "" },
                onMicClick = {
                    if (audioPermission.status.isGranted) {
                        viewModel.startRecording(context)
                    } else {
                        audioPermission.launchPermissionRequest()
                    }
                },
                onCancelRecording = { viewModel.cancelRecording() },
                onStopAndSend = { viewModel.stopAndSendRecording() },
                onSend = {
                    when {
                        uiState.editingMessage != null -> {
                            viewModel.submitEdit(inputText)
                            inputText = ""
                        }
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
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            SkeletonChatList(count = 7)
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
                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                val filteredMessages = if (uiState.searchQuery.isBlank()) uiState.messages
                    else uiState.messages.filter { it.message.contains(uiState.searchQuery, ignoreCase = true) }
                val grouped = filteredMessages.groupByDate()
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
                            onEdit = { viewModel.startEditing(message) },
                            onAvatarClick = if (!isMe) ({
                                viewModel.fetchMemberProfile(message.userId, message.userEmail)
                                selectedMemberProfile = message.userId to message.userEmail
                            }) else ({}),
                            onJumpToMessage = { targetId ->
                                screenScope.launch {
                                    val grouped = uiState.messages.groupByDate()
                                    var flatIndex = 0
                                    outer@ for ((_, msgs) in grouped) {
                                        flatIndex++ // date separator item
                                        for (msg in msgs) {
                                            if (msg.id == targetId) {
                                                listState.animateScrollToItem(flatIndex)
                                                break@outer
                                            }
                                            flatIndex++
                                        }
                                    }
                                }
                            },
                            onImageClick = { url -> fullscreenImageUrl = url },
                            onPlayPause = { viewModel.togglePlayback(message) },
                            onCycleSpeed = { viewModel.cycleAudioSpeed() },
                            audioSpeed = uiState.audioSpeed,
                            onOpenFile = { url, name -> openFile(context, url, name) },
                            reactions = uiState.reactions[message.id].orEmpty(),
                            currentUserId = uiState.currentUserId,
                            onToggleReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                            isOnline = uiState.onlineUserIds.contains(message.userId)
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
                leadingContent = { Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.tertiary) },
                headlineContent = { Text("Видео") },
                supportingContent = { Text("MP4, MOV, WebM") },
                modifier = Modifier.clickable {
                    videoPicker.launch("video/*")
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

    // Member profile card dialog
    selectedMemberProfile?.let { (userId, email) ->
        val profile = uiState.memberProfileCache[userId]
        MemberProfileCard(
            email = email,
            displayName = profile?.displayName,
            photoUrl = profile?.photoUrl,
            isOnline = profile?.isOnline ?: uiState.onlineUserIds.contains(userId),
            role = profile?.role,
            onDismiss = { selectedMemberProfile = null }
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
    editingMessage: ChatMessage?,
    onTextChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveFile: () -> Unit,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onMicClick: () -> Unit,
    onCancelRecording: () -> Unit,
    onStopAndSend: () -> Unit,
    onSend: () -> Unit,
    mentionSuggestions: List<String> = emptyList(),
    onMentionSelected: (String) -> Unit = {}
) {
    val canSend = (text.isNotBlank() || pendingMediaUri != null || pendingFileName != null) && !isSending
    val showMic = text.isBlank() && pendingMediaUri == null && pendingFileName == null

    // Mention suggestions popup (shown above the input bar)
    if (mentionSuggestions.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column {
                mentionSuggestions.forEach { email ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMentionSelected(email) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AlternateEmail, null, modifier = Modifier.size(16.dp))
                        Text(email, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

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
                // ── Edit indicator ─────────────────────────────────────────────
                if (editingMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Редактирование", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(editingMessage.message.take(80), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onCancelEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider()
                }

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

private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onDelete: (() -> Unit)?,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onJumpToMessage: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onPlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    audioSpeed: Float,
    onOpenFile: (String, String) -> Unit,
    reactions: List<com.aggin.carcost.data.local.database.entities.ChatReaction> = emptyList(),
    currentUserId: String = "",
    onToggleReaction: (String) -> Unit = {},
    isOnline: Boolean = false,
    onAvatarClick: () -> Unit = {}
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
                // Emoji picker row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    REACTION_EMOJIS.forEach { emoji ->
                        val isSelected = reactions.any { it.emoji == emoji && it.userId == currentUserId }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    onToggleReaction(emoji)
                                    showContextMenu = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
                HorizontalDivider()
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
                if (onDelete != null && message.message.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Редактировать") },
                        leadingContent = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable { showContextMenu = false; onEdit() }
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
                    Box(modifier = Modifier.size(36.dp)) {
                        // Avatar circle — clickable to show profile card
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(avatarColor(message.userEmail))
                                .clickable { onAvatarClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(message.userEmail.take(1).uppercase(), color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        // Online indicator dot (bottom-right)
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.Center)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                            }
                        }
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
                                    if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble, onClick = { onJumpToMessage(message.replyToId) })
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
                                        if (isPlaying) {
                                            Spacer(Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(onBubble.copy(alpha = 0.15f))
                                                    .clickable { onCycleSpeed() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    when (audioSpeed) {
                                                        1.5f -> "1.5×"
                                                        2f -> "2×"
                                                        else -> "1×"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onBubble
                                                )
                                            }
                                        }
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
                                    if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble, onClick = { onJumpToMessage(message.replyToId) })
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

                        "video" -> {
                            VideoPlayerBubble(
                                url = message.mediaUrl ?: "",
                                bubbleShape = bubbleShape
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
                                        if (message.replyToId != null) ReplyQuote(message.replyToText, onBubble, onClick = { onJumpToMessage(message.replyToId) })
                                        if (message.message.isNotBlank()) {
                                            MentionText(
                                                text = message.message,
                                                baseColor = onBubble,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    ) {
                        if (message.isEdited) {
                            Text("изменено", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Text(timeFmt.format(Date(message.createdAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Reaction chips row — one chip per unique emoji with count.
                    if (reactions.isNotEmpty()) {
                        ReactionChipsRow(
                            reactions = reactions,
                            currentUserId = currentUserId,
                            onToggleReaction = onToggleReaction,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionChipsRow(
    reactions: List<com.aggin.carcost.data.local.database.entities.ChatReaction>,
    currentUserId: String,
    onToggleReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = reactions.groupBy { it.emoji }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        grouped.forEach { (emoji, items) ->
            val iReacted = items.any { it.userId == currentUserId }
            val bg = if (iReacted) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onToggleReaction(emoji) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 13.sp)
                    if (items.size > 1) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            items.size.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(
    text: String?,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberProfileCard(
    email: String,
    displayName: String?,
    photoUrl: String?,
    isOnline: Boolean,
    role: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(avatarColor(email)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                email.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                        }
                    }
                    // Online dot
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Name
                Text(
                    displayName ?: email.substringBefore("@"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Online status chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isOnline)
                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        )
                        Text(
                            if (isOnline) "Онлайн" else "Не в сети",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Role badge
                if (role != null) {
                    Spacer(Modifier.height(6.dp))
                    val roleLabel = when (role) {
                        "OWNER" -> "Владелец"
                        "DRIVER" -> "Водитель"
                        else -> role
                    }
                    Text(
                        roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
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

// ── Video Player Bubble (regular video attachment) ────────────────────────────

@Composable
fun VideoPlayerBubble(url: String, bubbleShape: androidx.compose.ui.graphics.Shape) {
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

    LaunchedEffect(url) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(url, emptyMap())
                thumbnail = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) isPlaying = false }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onRenderedFirstFrame() { firstFrameReady = true }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .aspectRatio(16f / 9f)
            .clip(bubbleShape),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).apply {
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                            exoPlayer.setVideoSurface(android.view.Surface(st))
                        }
                        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                            exoPlayer.clearVideoSurface(); return true
                        }
                        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                    else {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
                        exoPlayer.play()
                    }
                }
        )

        if (!firstFrameReady) {
            val bmp = thumbnail
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        if (!isPlaying) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ── Mention-aware text renderer ────────────────────────────────────────────────

/**
 * Renders [text] with @mention tokens highlighted in the primary color.
 */
@Composable
private fun MentionText(
    text: String,
    baseColor: Color,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, primaryColor) {
        buildAnnotatedString {
            val pattern = Regex("@[\\w.@+-]+")
            var last = 0
            for (match in pattern.findAll(text)) {
                append(text.substring(last, match.range.first))
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                    append(match.value)
                }
                last = match.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(
        text = annotated,
        color = baseColor,
        fontSize = fontSize
    )
}
