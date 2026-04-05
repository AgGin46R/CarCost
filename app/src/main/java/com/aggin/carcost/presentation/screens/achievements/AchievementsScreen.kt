package com.aggin.carcost.presentation.screens.achievements

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.AchievementType
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

data class AchievementsUiState(
    val unlocked: List<Achievement> = emptyList(),
    val locked: List<AchievementType> = emptyList(),
    val isLoading: Boolean = true
)

class AchievementsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).achievementDao()
    private val auth = SupabaseAuthRepository()

    val uiState: StateFlow<AchievementsUiState> = dao
        .getAchievements(auth.getUserId() ?: "")
        .map { unlocked ->
            val unlockedTypes = unlocked.map { it.type }.toSet()
            val locked = AchievementType.values().filter { it !in unlockedTypes }
            AchievementsUiState(unlocked = unlocked, locked = locked, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AchievementsUiState()
        )
}

class AchievementsViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AchievementsViewModel(app) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: AchievementsViewModel = viewModel(
        factory = AchievementsViewModelFactory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Достижения") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.unlocked.isNotEmpty()) {
                item {
                    Text(
                        "Разблокировано (${uiState.unlocked.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(uiState.unlocked, key = { it.id }) { achievement ->
                    AchievementCard(
                        icon = achievement.type.icon,
                        title = achievement.type.title,
                        description = achievement.type.description,
                        unlocked = true,
                        dateText = dateFmt.format(Date(achievement.unlockedAt))
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (uiState.locked.isNotEmpty()) {
                item {
                    Text(
                        "Ещё не разблокировано (${uiState.locked.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.locked) { type ->
                    AchievementCard(
                        icon = type.icon,
                        title = type.title,
                        description = type.description,
                        unlocked = false,
                        dateText = null
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    icon: String,
    title: String,
    description: String,
    unlocked: Boolean,
    dateText: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (unlocked) 1f else 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (unlocked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 24.sp)
            }

            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dateText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Разблокировано $dateText",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (unlocked) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.Default.Lock, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
