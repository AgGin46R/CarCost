package com.aggin.carcost.presentation.screens.chat

import android.app.Application
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.ChatMessage
import com.aggin.carcost.data.remote.repository.SupabaseChatRepository
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Data ─────────────────────────────────────────────────────────────────────

data class CarChatPreview(
    val car: Car,
    val lastMessage: ChatMessage?
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChatsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val supabaseChat = SupabaseChatRepository()

    init {
        // После перезахода Room пустой — подтянуть последние сообщения из Supabase
        viewModelScope.launch {
            try {
                val cars = db.carDao().getAllActiveCars().first()
                cars.forEach { car ->
                    supabaseChat.getMessages(car.id).onSuccess { messages ->
                        messages.forEach {
                            try { db.chatMessageDao().insert(it) } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("ChatsListVM", "Initial message sync skipped: ${e.message}")
            }
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatPreviews: StateFlow<List<CarChatPreview>> = db.carDao()
        .getAllActiveCars()
        .flatMapLatest { cars ->
            if (cars.isEmpty()) return@flatMapLatest flowOf(emptyList())
            // For each car, combine with its last message
            val perCarFlows = cars.map { car ->
                db.chatMessageDao()
                    .getLastMessageByCarId(car.id)
                    .map { lastMsg -> CarChatPreview(car, lastMsg) }
            }
            combine(perCarFlows) { it.toList() }
        }
        .map { previews ->
            // Sort: cars with messages first (by message time desc), then cars without messages
            previews.sortedWith(compareByDescending { it.lastMessage?.createdAt ?: Long.MIN_VALUE })
        }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    navController: NavController,
    viewModel: ChatsListViewModel = viewModel()
) {
    val previews by viewModel.chatPreviews.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                com.aggin.carcost.presentation.components.SkeletonChatList(count = 6)
            }
        } else if (previews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Нет автомобилей",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Добавьте автомобиль чтобы начать общаться",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(previews, key = { it.car.id }) { preview ->
                    CarChatItem(
                        preview = preview,
                        onClick = {
                            navController.navigate(Screen.Chat.createRoute(preview.car.id))
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CarChatItem(
    preview: CarChatPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with car icon
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${preview.car.brand} ${preview.car.model}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                preview.lastMessage?.let { msg ->
                    Text(
                        text = formatTime(msg.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview.lastMessage?.let { msg ->
                    val sender = msg.userEmail.substringBefore("@")
                    "$sender: ${msg.message}"
                } ?: "Нет сообщений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (preview.lastMessage != null) 0.7f else 0.4f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    val dayMs = 24 * 60 * 60 * 1000L
    return when {
        diff < dayMs -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
        diff < 7 * dayMs -> SimpleDateFormat("EEE", Locale("ru")).format(Date(epochMs))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(epochMs))
    }
}
