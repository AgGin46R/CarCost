package com.aggin.carcost.presentation.screens.car_members

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.room.withTransaction
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import android.content.Intent
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

data class CarMembersUiState(
    val members: List<CarMember> = emptyList(),
    val currentUserRole: MemberRole? = null,
    val isLoading: Boolean = true,
    /** Готовый код приглашения; показывается после создания, владелец передаёт его сам */
    val inviteCode: String? = null,
    val inviteRole: MemberRole? = null,
    val errorMessage: String? = null
) {
    /**
     * Может звать участников и выгонять их.
     *
     * Роль берётся из локальной таблицы участников. Строки создателя там нет,
     * пока он не откроет этот экран: её заводит ensureOwnerRegistered() при
     * входе сюда, и сразу локально, без ожидания сети.
     *
     * Определить владельца иначе клиент не может — поля владельца в локальной
     * таблице машин нет вовсе, оно есть только на сервере.
     */
    val canManage: Boolean get() = currentUserRole == MemberRole.OWNER
}

class CarMembersViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.carMemberDao()
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

    /**
     * Забирает участников с сервера.
     *
     * Всё в одной транзакции намеренно. Строки перезаписываются парой
     * «удалить + вставить» (id той же записи на сервере может отличаться от
     * локального, и одним REPLACE по первичному ключу не обойтись). Без
     * транзакции Room объявляет таблицу изменённой после каждого шага, поток
     * отдаёт промежуточные списки, и при каждом открытии экрана участники
     * заметно мигают: пропадают и появляются по одному.
     *
     * Внутри транзакции инвалидация откладывается до коммита — список
     * обновляется один раз и сразу целиком.
     */
    private suspend fun syncMembersFromSupabase() {
        // Сеть — до транзакции: держать её открытой на время запроса нельзя
        val remoteMembers = supabaseMembers.getMembersByCarId(carId).getOrNull() ?: return

        db.withTransaction {
            // Ghost-записи с пустым userId/email — артефакты гонки при старте
            dao.deleteGhostMembers(carId)
            remoteMembers.forEach { member ->
                dao.removeMember(member.carId, member.userId)
                dao.insert(member)
            }
            dao.deletePendingMembers(carId)
        }
    }

    /**
     * Создаёт приглашение и возвращает ссылку, которой владелец делится сам.
     *
     * Письма приложение не отправляет и никогда не отправляло — раньше здесь
     * просто создавалась запись в БД, а пользователю показывалось «получит
     * письмо со ссылкой». Узнать о приглашении можно было, только уже имея
     * аккаунт на ровно тот email, который угадал владелец.
     *
     * Код снимает все эти ограничения: `accept_invitation` на сервере сверяет
     * только сам код, а почту не проверяет вообще — поэтому присоединится и тот,
     * кто ещё не ставил приложение, и вошедший через VK без почты.
     *
     * @param email необязателен. Если указан — приглашённый, у которого уже
     *              есть аккаунт с этим адресом, дополнительно увидит баннер
     *              на главном экране.
     */
    fun inviteMember(email: String, role: MemberRole) {
        com.aggin.carcost.data.analytics.Analytics.inviteCreated()
        viewModelScope.launch {
            supabaseMembers.createInvitation(carId, email.trim(), role)
                .onSuccess { token ->
                    _uiState.value = _uiState.value.copy(
                        inviteCode = token,
                        inviteRole = role
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = getApplication<Application>().getString(R.string.carmembers_ne_udalos_sozdat_priglashenie, e.message.orEmpty())
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
        _uiState.value = _uiState.value.copy(inviteCode = null, inviteRole = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    val isOwner: Boolean
        get() = uiState.value.canManage
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
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }

    if (showJoinDialog) {
        com.aggin.carcost.presentation.components.JoinByCodeDialog(
            onDismiss = { showJoinDialog = false },
            onSubmit = { code ->
                showJoinDialog = false
                navController.navigateOnce(Screen.AcceptInvite.createRoute(code))
            }
        )
    }

    // Код приглашения: приложение не рассылает письма, владелец передаёт код сам.
    // Ссылку carcost:// мессенджеры не делают кликабельной, поэтому именно код.
    uiState.inviteCode?.let { code ->
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        val pretty = SupabaseCarMembersRepository.InviteCode.format(code)
        AlertDialog(
            onDismissRequest = { viewModel.clearInviteSent() },
            icon = { Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.components_kod_priglasheniya)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.carmembers_peredayte_kod_tomu_kogo_priglashaete_v) +
                            stringResource(R.string.carmembers_cherez_prisoedinitsya_po_kodu_na_glavnom)
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = pretty,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.carmembers_deystvuet_7_dney_srabotaet_odin_raz),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(R.string.carmembers_priglashayu_vas_v_carcost_sovmestnyy) +
                                context.getString(R.string.carmembers_kod_priglasheniya_n_n, pretty) +
                                context.getString(R.string.carmembers_ustanovite_prilozhenie_i_vvedite_kod_na) +
                                context.getString(R.string.carmembers_prisoedinitsya_po_kodu)
                        )
                    }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.carmembers_otpravit_priglashenie)))
                    viewModel.clearInviteSent()
                }) { Text(stringResource(R.string.carmembers_otpravit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(pretty))
                    android.widget.Toast
                        .makeText(context, context.getString(R.string.carmembers_kod_skopirovan), android.widget.Toast.LENGTH_SHORT)
                        .show()
                    viewModel.clearInviteSent()
                }) { Text(stringResource(R.string.carmembers_skopirovat)) }
            }
        )
    }

    // Ошибка
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.common_error)) },
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
                title = { Text(stringResource(R.string.cardetail_uchastniki)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigateOnce(Screen.Chat.createRoute(carId)) }) {
                        Icon(Icons.Default.Chat, contentDescription = stringResource(R.string.cardetail_chat))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                // Обе стороны совместного доступа рядом: свой код — чтобы позвать
                // сюда, чужой — чтобы уйти в другую машину. Раньше «Присоединиться
                // по коду» висело на списке автомобилей, где его никто не связывал
                // с участниками, а приглашение пряталось за кнопкой внизу экрана.
                SharedAccessCard(
                    isOwner = uiState.canManage,
                    onInvite = { showInviteDialog = true },
                    onJoin = { showJoinDialog = true }
                )
            }

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
                            Text(stringResource(R.string.carmembers_net_uchastnikov), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.carmembers_priglasite_teh_kto_ezdit_na_etoy_mashine),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                items(uiState.members, key = { it.id }) { member ->
                    MemberCard(
                        member = member,
                        canRemove = uiState.canManage,
                        onRemove = { viewModel.removeMember(member) }
                    )
                }
            }
        }
    }
}

/**
 * Совместный доступ: позвать сюда и уйти в чужую машину.
 *
 * Приглашать может только владелец, а присоединиться — кто угодно, поэтому
 * первая строка условная, вторая всегда на месте.
 */
@Composable
private fun SharedAccessCard(
    isOwner: Boolean,
    onInvite: () -> Unit,
    onJoin: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (isOwner) {
                SharedAccessRow(
                    icon = Icons.Default.PersonAdd,
                    title = stringResource(R.string.carmembers_priglasit_v_etu_mashinu),
                    subtitle = stringResource(R.string.carmembers_sozdat_kod_i_peredat_ego_samomu),
                    onClick = onInvite
                )
                HorizontalDivider()
            }
            SharedAccessRow(
                icon = Icons.Default.VpnKey,
                title = stringResource(R.string.components_prisoedinitsya_po_kodu),
                subtitle = stringResource(R.string.carmembers_esli_kod_prislali_vam_dlya_drugoy_mashiny),
                onClick = onJoin
            )
        }
    }
}

@Composable
private fun SharedAccessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun RoleInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Ограничения на запись по ролям были и откачены: в общей машине запись
        // добавляет тот, кто сейчас на сервисе, а не тот, у кого нужная роль.
        // Текст описывает то, что закреплено политиками на сервере.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.carmembers_roli_uchastnikov), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            RoleRow("👑", stringResource(R.string.carmembers_vladelets), stringResource(R.string.carmembers_priglashaet_i_udalyaet_uchastnikov_mozhet))
            RoleRow("🔧", stringResource(R.string.home_mehanik), stringResource(R.string.carmembers_vedet_mashinu_naravne_s_vladeltsem))
            RoleRow("🚗", stringResource(R.string.home_voditel), stringResource(R.string.carmembers_vedet_mashinu_naravne_s_vladeltsem))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.carmembers_rashody_to_dokumenty_i_chat_dostupny_vsem) +
                    stringResource(R.string.carmembers_rol_lish_pomechaet_kto_chem_zanimaetsya),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        MemberRole.OWNER -> stringResource(R.string.carmembers_vladelets)
                        MemberRole.DRIVER -> stringResource(R.string.home_voditel)
                        MemberRole.MECHANIC -> stringResource(R.string.home_mehanik)
                    },
                    fontSize = 12.sp,
                    color = roleColor
                )
                if (member.userId.startsWith("pending_")) {
                    Text(stringResource(R.string.carmembers_ozhidaet_podtverzhdeniya),
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
    var email by rememberSaveable { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(MemberRole.DRIVER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.carmembers_priglasit_uchastnika)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.carmembers_vy_poluchite_kod_i_peredadite_ego_sami),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.carmembers_email_neobyazatelno)) },
                    supportingText = {
                        Text(stringResource(R.string.carmembers_esli_u_cheloveka_uzhe_est_carcost_na_etoy))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(stringResource(R.string.carmembers_rol), style = MaterialTheme.typography.labelMedium)
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
                                        MemberRole.DRIVER -> stringResource(R.string.home_voditel)
                                        MemberRole.MECHANIC -> stringResource(R.string.home_mehanik)
                                        else -> role.name
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    when (role) {
                                        MemberRole.DRIVER -> stringResource(R.string.carmembers_dobavlenie_rashodov)
                                        MemberRole.MECHANIC -> stringResource(R.string.carmembers_upravlenie_to)
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
            // Email необязателен: код работает и без него. Но если введён —
            // должен быть похож на адрес, иначе баннер всё равно не найдёт адресата
            val emailValid = email.isBlank() || email.contains("@")
            TextButton(
                enabled = emailValid,
                onClick = { onConfirm(email, selectedRole) }
            ) { Text(stringResource(R.string.carmembers_sozdat_kod)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
