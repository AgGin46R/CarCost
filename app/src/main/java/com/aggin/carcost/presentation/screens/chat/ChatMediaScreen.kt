package com.aggin.carcost.presentation.screens.chat

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Вложения чата, собранные по разделам — как в мессенджерах.
 *
 * Новых данных не потребовалось: всё уже лежит в chat_messages, где у каждого
 * сообщения есть media_type и media_url. Экран лишь показывает то же самое под
 * другим углом — не лентой разговора, а списком того, чем обменивались.
 *
 * Разделов три. Музыки и «сервисов», как во ВКонтакте, здесь нет и не будет:
 * голосовые сообщения имеют смысл только внутри разговора, отдельным списком
 * их никто не слушает.
 */

enum class MediaTab(val title: String) {
    PHOTOS("Фото"),
    VIDEOS("Видео"),
    FILES("Файлы")
}

data class ChatMediaUiState(
    val photos: List<ChatMessage> = emptyList(),
    val videos: List<ChatMessage> = emptyList(),
    val files: List<ChatMessage> = emptyList(),
    val carName: String = "",
    val isLoading: Boolean = true
)

class ChatMediaViewModel(app: Application, private val carId: String) : ViewModel() {

    private val db = AppDatabase.getDatabase(app)

    private val _uiState = MutableStateFlow(ChatMediaUiState())
    val uiState: StateFlow<ChatMediaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            db.carDao().getCarById(carId)?.let { car ->
                _uiState.update { it.copy(carName = "${car.brand} ${car.model}".trim()) }
            }
        }
        viewModelScope.launch {
            // Читаем из локальной базы, а не с сервера: она уже наполнена чатом,
            // и экран открывается мгновенно даже без сети
            db.chatMessageDao().getMessagesByCarId(carId).collect { messages ->
                val withMedia = messages.filter { !it.mediaUrl.isNullOrBlank() }
                _uiState.update {
                    it.copy(
                        // Снимки идут от новых к старым: свежее ищут чаще
                        photos = withMedia.filter { m -> m.mediaType == null || m.mediaType == "image" }
                            .sortedByDescending { m -> m.createdAt },
                        videos = withMedia.filter { m -> m.mediaType == "video" }
                            .sortedByDescending { m -> m.createdAt },
                        files = withMedia.filter { m -> m.mediaType == "file" }
                            .sortedByDescending { m -> m.createdAt },
                        isLoading = false
                    )
                }
            }
        }
    }
}

class ChatMediaViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatMediaViewModel(app, carId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMediaScreen(navController: NavController, carId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ChatMediaViewModel = viewModel(
        factory = ChatMediaViewModelFactory(app, carId)
    )
    val uiState by viewModel.uiState.collectAsState()

    var tab by rememberSaveable { mutableStateOf(MediaTab.PHOTOS) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var pdfPreview by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Вложения", fontWeight = FontWeight.Bold)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = tab.ordinal) {
                MediaTab.entries.forEach { entry ->
                    val count = when (entry) {
                        MediaTab.PHOTOS -> uiState.photos.size
                        MediaTab.VIDEOS -> uiState.videos.size
                        MediaTab.FILES -> uiState.files.size
                    }
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = {
                            // Число рядом с названием: сразу видно, где искать,
                            // и не нужно открывать пустой раздел, чтобы это понять
                            Text(if (count > 0) "${entry.title} $count" else entry.title)
                        }
                    )
                }
            }

            val items = when (tab) {
                MediaTab.PHOTOS -> uiState.photos
                MediaTab.VIDEOS -> uiState.videos
                MediaTab.FILES -> uiState.files
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        when (tab) {
                            MediaTab.PHOTOS -> "Фотографий пока нет"
                            MediaTab.VIDEOS -> "Видео пока нет"
                            MediaTab.FILES -> "Файлов пока нет"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                tab == MediaTab.FILES -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { message ->
                        FileRow(
                            message = message,
                            onClick = {
                                val url = message.mediaUrl ?: return@FileRow
                                val name = message.fileName ?: "file"
                                if (name.substringAfterLast('.', "").lowercase() == "pdf") {
                                    pdfPreview = url to name
                                } else {
                                    openFile(context, url, name)
                                }
                            },
                            onDownload = {
                                downloadAttachment(
                                    context, message.mediaUrl.orEmpty(), message.fileName, "file"
                                )
                            }
                        )
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items, key = { it.id }) { message ->
                        MediaCell(
                            url = message.mediaUrl.orEmpty(),
                            isVideo = message.mediaType == "video",
                            onClick = {
                                val url = message.mediaUrl ?: return@MediaCell
                                if (message.mediaType == "video") openFile(context, url, "video.mp4")
                                else fullscreenImageUrl = url
                            },
                            // Долгое нажатие — сохранить. Отдельной кнопки на плитке
                            // не делаю: она закрыла бы собой само изображение, а
                            // сетка нужна прежде всего чтобы просматривать.
                            onLongClick = {
                                downloadAttachment(
                                    context,
                                    message.mediaUrl.orEmpty(),
                                    null,
                                    message.mediaType ?: "image"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ZoomableImage(
                url = url,
                onDismiss = { fullscreenImageUrl = null }
            )
        }
    }

    pdfPreview?.let { (url, name) ->
        PdfPreviewDialog(
            url = url,
            fileName = name,
            onDismiss = { pdfPreview = null },
            onOpenExternally = {
                pdfPreview = null
                openFile(context, url, name)
            }
        )
    }
}

/** Плитка сетки: снимок или первый кадр видео со значком воспроизведения. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MediaCell(
    url: String,
    isVideo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Coil сам вытаскивает кадр из видео по адресу — отдельная библиотека
        // для превью не нужна
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Видео",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(36.dp).align(Alignment.Center)
            )
        }
    }
}

/** Строка списка файлов: значок по расширению, название и дата отправки. */
@Composable
private fun FileRow(message: ChatMessage, onClick: () -> Unit, onDownload: () -> Unit) {
    val name = message.fileName ?: "Файл"
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                fileIcon(name),
                contentDescription = null,
                tint = fileColor(name),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(message.createdAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Скачать")
            }
        }
    }
}
