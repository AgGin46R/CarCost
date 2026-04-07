package com.aggin.carcost.presentation.screens.car_members

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
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CarMembersUiState(
    val members: List<CarMember> = emptyList(),
    val currentUserRole: MemberRole? = null,
    val isLoading: Boolean = true,
    val inviteSentToEmail: String? = null,  // показывается после успешной отправки
    val errorMessage: String? = null
)

class CarMembersViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).carMemberDao()
    private val auth = SupabaseAuthRepository()
    private val supabaseMembers = SupabaseCarMembersRepository(auth)
    private val currentUserId = auth.getUserId() ?: ""

    private val _uiState = MutableStateFlow(CarMembersUiState())
    val uiState: StateFlow<CarMembersUiState> = _uiState.asStateFlow()

    init {
        // Collect from local DB — role defaults to null until sync completes
        viewModelScope.launch {
            dao.getMembersByCarId(carId).collect { members ->
                _uiState.value = _uiState.value.copy(
                    members = members,
                    currentUserRole = members.find { it.userId == currentUserId }?.role,
                    isLoading = false
                )
            }
        }
        // IMPORTANT: sync first so we know the real role, then register owner if needed
        viewModelScope.launch {
            syncMembersFromSupabase()
            ensureOwnerRegistered()
        }
    }

    private suspend fun ensureOwnerRegistered() {
        // Если currentUserId пустой — auth ещё не загрузился, ничего не делаем
        // (иначе вставляется ghost-запись с пустым userId/email)
        if (currentUserId.isBlank()) return

        val existing = dao.getRoleForUser(carId, currentUserId)
        if (existing == null) {
            val email = auth.getCurrentUserEmail() ?: return  // нет email — пропускаем
            dao.removeMember(carId, currentUserId)
            dao.insert(CarMember(
                id = UUID.randomUUID().toString(),
                carId = carId,
                userId = currentUserId,
                email = email,
                role = MemberRole.OWNER
            ))
            supabaseMembers.ensureOwner(carId)
        }
    }

    private suspend fun syncMembersFromSupabase() {
        // Чистим ghost-записи с пустым userId/email перед синхронизацией
        dao.deleteGhostMembers(carId)

        val remoteMembers = supabaseMembers.getMembersByCarId(carId).getOrNull() ?: return
        remoteMembers.forEach { member ->
            dao.removeMember(member.carId, member.userId)
            dao.insert(member)
        }
        dao.deletePendingMembers(carId)
    }

    /** Создаёт приглашение в Supabase и отправляет его напрямую на email */
    fun inviteMember(email: String, role: MemberRole) {
        viewModelScope.launch {
            supabaseMembers.createInvitation(carId, email, role)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(inviteSentToEmail = email)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Не удалось создать приглашение: ${e.message}"
                    )
                }
        }
    }

    fun removeMember(member: CarMember) {
        viewModelScope.launch {
            dao.delete(member)
            supabaseMembers.removeMember(carId, member.userId)
        }
    }

    fun clearInviteSent() {
        _uiState.value = _uiState.value.copy(inviteSentToEmail = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    val isOwner: Boolean
        get() = uiState.value.currentUserRole == MemberRole.OWNER
}

class CarMembersViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CarMembersViewModel(app, carId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarMembersScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: CarMembersViewModel = viewModel(
        factory = CarMembersViewModelFactory(context.applicationContext as Application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()
    var showInviteDialog by remember { mutableStateOf(false) }

    // Подтверждение успешной отправки приглашения
    uiState.inviteSentToEmail?.let { email ->
        AlertDialog(
            onDismissRequest = { viewModel.clearInviteSent() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Приглашение отправлено") },
            text = { Text("Приглашение отправлено на $email. Пользователь получит письмо со ссылкой для присоединения.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearInviteSent() }) { Text("OK") }
            }
        )
    }

    // Ошибка
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Ошибка") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    if (showInviteDialog) {
        InviteMemberDialog(
            onConfirm = { email, role ->
                viewModel.inviteMember(email, role)
                showInviteDialog = false
            },
            onDismiss = { showInviteDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Участники авто") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Chat.createRoute(carId)) }) {
                        Icon(Icons.Default.Chat, contentDescription = "Чат")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.currentUserRole == MemberRole.OWNER) {
                ExtendedFloatingActionButton(
                    onClick = { showInviteDialog = true },
                    icon = { Icon(Icons.Default.PersonAdd, null) },
                    text = { Text("Пригласить") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                RoleInfoCard()
            }

            if (uiState.members.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Group, null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Нет участников", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Пригласите других водителей\nили механиков для совместного учёта",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                items(uiState.members, key = { it.id }) { member ->
                    MemberCard(
                        member = member,
                        canRemove = uiState.currentUserRole == MemberRole.OWNER,
                        onRemove = { viewModel.removeMember(member) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Роли участников", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            RoleRow("👑", "Владелец", "Полный доступ, управление участниками")
            RoleRow("🚗", "Водитель", "Добавление расходов, просмотр")
            RoleRow("🔧", "Механик", "Управление ТО и напоминаниями")
        }
    }
}

@Composable
private fun RoleRow(icon: String, role: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, fontSize = 14.sp)
        Column {
            Text(role, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemberCard(
    member: CarMember,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val roleColor = when (member.role) {
        MemberRole.OWNER -> MaterialTheme.colorScheme.primary
        MemberRole.DRIVER -> MaterialTheme.colorScheme.secondary
        MemberRole.MECHANIC -> MaterialTheme.colorScheme.tertiary
    }

    val roleIcon = when (member.role) {
        MemberRole.OWNER -> "👑"
        MemberRole.DRIVER -> "🚗"
        MemberRole.MECHANIC -> "🔧"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(roleColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(roleIcon, fontSize = 22.sp)
            }

            Column(Modifier.weight(1f)) {
                Text(member.email, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when (member.role) {
                        MemberRole.OWNER -> "Владелец"
                        MemberRole.DRIVER -> "Водитель"
                        MemberRole.MECHANIC -> "Механик"
                    },
                    fontSize = 12.sp,
                    color = roleColor
                )
                if (member.userId.startsWith("pending_")) {
                    Text("Ожидает подтверждения",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (canRemove && member.role != MemberRole.OWNER) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.RemoveCircle, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun InviteMemberDialog(
    onConfirm: (String, MemberRole) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(MemberRole.DRIVER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить участника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Роль", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemberRole.values().filter { it != MemberRole.OWNER }.forEach { role ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    when (role) {
                                        MemberRole.DRIVER -> "Водитель"
                                        MemberRole.MECHANIC -> "Механик"
                                        else -> role.name
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    when (role) {
                                        MemberRole.DRIVER -> "Добавление расходов"
                                        MemberRole.MECHANIC -> "Управление ТО"
                                        else -> ""
                                    },
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (email.isNotBlank() && email.contains("@")) {
                    onConfirm(email, selectedRole)
                }
            }) { Text("Пригласить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
