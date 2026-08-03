package com.aggin.carcost.presentation.screens.profile

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.domain.gamification.DriverScore
import com.aggin.carcost.presentation.navigation.Screen
import com.aggin.carcost.ui.theme.AccentScheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

/** Домен синтетических адресов, которые Edge Function vk-auth выдаёт VK-аккаунтам без почты */
private const val VK_EMAIL_DOMAIN = "vk.carcost.app"

/**
 * Приглашение в чужой автомобиль отправляется на email. У VK-пользователя без
 * почты адрес синтетический, и пригласить его никто не сможет, пока он сам не
 * сообщит этот адрес — поэтому даём его скопировать.
 */
@Composable
private fun InvitationAddressCard(address: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Адрес для приглашений",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Сообщите его тому, кто добавит вас в свой автомобиль",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(address))
                android.widget.Toast
                    .makeText(context, "Адрес скопирован", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать")
            }
        }
    }
}

/**
 * Способы входа в аккаунт.
 *
 * Без привязки вход через ВКонтакте создаёт ОТДЕЛЬНЫЙ аккаунт: человек с
 * профилем по email нажимал «Войти через VK» и попадал в пустой аккаунт без
 * своих машин. Привязка сводит оба способа входа к одному пользователю.
 */
@Composable
private fun SignInMethodsCard(
    vkLink: com.aggin.carcost.data.remote.repository.VkIdentity?,
    /** Ответ сервера получен. Пока false, vkLink == null не означает «не привязан» */
    isLinkKnown: Boolean,
    canUnlink: Boolean,
    isLinking: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    // Привязка — единственное на этом экране, что нельзя узнать локально.
    // Пока она едет, экран не имеет права ничего утверждать.
    val notLinked = isLinkKnown && vkLink == null
    var showUnlinkDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Способы входа",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.aggin.carcost.R.drawable.ic_vk),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("ВКонтакте", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            vkLink != null && vkLink.displayName.isNotBlank() -> vkLink.displayName
                            vkLink != null -> "Привязан"
                            notLinked -> "Не привязан"
                            else -> "Проверяем…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    isLinking -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    // Кнопки нет, пока неизвестно: «Привязать» на привязанном
                    // аккаунте — приглашение к бессмысленному действию
                    !isLinkKnown -> Unit
                    vkLink == null -> TextButton(onClick = onLink) { Text("Привязать") }
                    canUnlink -> TextButton(onClick = { showUnlinkDialog = true }) {
                        Text("Отвязать", color = MaterialTheme.colorScheme.error)
                    }
                    // Иначе — привязан и отвязать нельзя: кнопки нет вовсе
                }
            }

            if (notLinked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "После привязки вход через ВКонтакте будет вести в этот же аккаунт, " +
                        "а не создавать новый",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text("Отвязать ВКонтакте?") },
            text = { Text("Вход через ВКонтакте перестанет вести в этот аккаунт. Данные останутся на месте.") },
            confirmButton = {
                TextButton(onClick = { showUnlinkDialog = false; onUnlink() }) {
                    Text("Отвязать", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinByCodeDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoOptionsDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val currentTheme by settingsManager.themeFlow.collectAsState(initial = "System")
    val currentAccent by settingsManager.accentFlow.collectAsState(initial = "Blue")
    var showAppearanceDialog by rememberSaveable { mutableStateOf(false) }

    val notifMaintenance by settingsManager.notifMaintenanceFlow.collectAsState(initial = true)
    val notifInsurance by settingsManager.notifInsuranceFlow.collectAsState(initial = true)
    val notifDigest by settingsManager.notifDigestFlow.collectAsState(initial = true)
    val notifFuel by settingsManager.notifFuelFlow.collectAsState(initial = true)
    val notifBudgetAlert by settingsManager.notifBudgetAlertFlow.collectAsState(initial = true)
    val quietHoursEnabled by settingsManager.quietHoursEnabledFlow.collectAsState(initial = false)
    val quietHoursStart by settingsManager.quietHoursStartFlow.collectAsState(initial = 22)
    val quietHoursEnd by settingsManager.quietHoursEndFlow.collectAsState(initial = 8)

    // Разрешение на камеру
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Разрешение на галерею (READ_MEDIA_IMAGES на Android 13+, READ_EXTERNAL_STORAGE ниже)
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Лаунчер для выбора фото из галереи
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateProfilePhoto(it) }
    }

    // Лаунчер для создания фото с камеры
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.tempCameraUri?.let { uri ->
                viewModel.updateProfilePhoto(uri)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Показываем ошибки через Snackbar
    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // У аккаунтов ВКонтакте без почты адрес синтетический — показывать его
            // как «email пользователя» бессмысленно
            val userEmail = uiState.user?.email ?: ""
            val isSyntheticEmail = userEmail.endsWith("@$VK_EMAIL_DOMAIN")

            ProfileHeader(
                displayName = uiState.user?.displayName ?: "Пользователь",
                email = if (isSyntheticEmail) "Вход через ВКонтакте" else userEmail,
                photoUrl = uiState.user?.photoUrl,
                isUploading = uiState.isUploadingPhoto,
                onPhotoClick = { showPhotoOptionsDialog = true }
            )

            if (isSyntheticEmail) {
                Spacer(modifier = Modifier.height(16.dp))
                InvitationAddressCard(address = userEmail)
            }

            Spacer(modifier = Modifier.height(16.dp))
            SignInMethodsCard(
                vkLink = uiState.vkLink,
                isLinkKnown = uiState.isVkLinkKnown,
                // Аккаунт создан через VK — отвязка отняла бы единственный способ войти
                canUnlink = !uiState.isVkAccount,
                isLinking = uiState.isLinkingVk,
                onLink = { viewModel.linkVk(context) },
                onUnlink = { viewModel.unlinkVk() }
            )

            uiState.vkLinkMessage?.let { message ->
                LaunchedEffect(message) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    viewModel.clearVkLinkMessage()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StatisticsSection(
                carsCount = uiState.statistics.carsCount,
                totalExpenses = uiState.statistics.totalExpenses,
                totalOdometer = uiState.statistics.totalOdometer
            )

            uiState.driverScore?.let { score ->
                Spacer(modifier = Modifier.height(16.dp))
                DriverScoreCard(score = score)
            }

            Spacer(modifier = Modifier.height(24.dp))

            NotificationSection(
                notifMaintenance = notifMaintenance,
                notifInsurance = notifInsurance,
                notifDigest = notifDigest,
                notifFuel = notifFuel,
                notifBudgetAlert = notifBudgetAlert,
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                onToggleMaintenance = { viewModel.setNotifMaintenance(it) },
                onToggleInsurance = { viewModel.setNotifInsurance(it) },
                onToggleDigest = { viewModel.setNotifDigest(it) },
                onToggleFuel = { viewModel.setNotifFuel(it) },
                onToggleBudgetAlert = { viewModel.setNotifBudgetAlert(it) },
                onToggleQuietHours = { viewModel.setQuietHoursEnabled(it) },
                onQuietHoursStartChange = { viewModel.setQuietHoursStart(it) },
                onQuietHoursEndChange = { viewModel.setQuietHoursEnd(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionsSection(
                navController = navController,
                onEditProfile = { showEditDialog = true },
                onChangePassword = { showPasswordDialog = true },
                onChangeAppearance = { showAppearanceDialog = true },
                onJoinByCode = { showJoinByCodeDialog = true },
                onBackup = { viewModel.exportBackup(context) },
                onDeleteAccount = { viewModel.startAccountDeletion() },
                onLogout = { showLogoutDialog = true },
                // У аккаунтов через VK и Google пароля нет — ни сменить его,
                // ни проверить текущий невозможно
                showChangePassword = uiState.hasPasswordLogin,
                isBackupInProgress = uiState.isCreatingBackup
            )

            Spacer(modifier = Modifier.height(16.dp))
            AppInfoSection()
        }
    }

    if (showJoinByCodeDialog) {
        com.aggin.carcost.presentation.components.JoinByCodeDialog(
            onDismiss = { showJoinByCodeDialog = false },
            onSubmit = { code ->
                showJoinByCodeDialog = false
                navController.navigateOnce(Screen.AcceptInvite.createRoute(code))
            }
        )
    }

    AccountDeletionDialogs(
        state = uiState.deletion,
        onCancel = { viewModel.cancelAccountDeletion() },
        onProceedToBackup = { viewModel.proceedToBackupOffer() },
        onCreateBackup = { viewModel.createBackupBeforeDeletion() },
        onShareBackup = { file -> viewModel.shareBackup(context, file) },
        onProceedToConfirm = { viewModel.proceedToFinalConfirm() },
        onTypedChange = { viewModel.updateDeletionConfirmText(it) },
        onDelete = { viewModel.deleteAccount(navController) }
    )

    // Диалог выбора источника фото
    if (showPhotoOptionsDialog) {
        PhotoOptionsDialog(
            onDismiss = { showPhotoOptionsDialog = false },
            onGalleryClick = {
                if (mediaPermission.status.isGranted) {
                    galleryLauncher.launch("image/*")
                    showPhotoOptionsDialog = false
                } else {
                    mediaPermission.launchPermissionRequest()
                    // Диалог остаётся открытым — пользователь выдаёт разрешение и нажимает снова
                }
            },
            onCameraClick = {
                if (cameraPermissionState.status.isGranted) {
                    val uri = viewModel.createTempImageUri(context)
                    if (uri != null) {
                        cameraLauncher.launch(uri)
                        showPhotoOptionsDialog = false
                    }
                } else {
                    cameraPermissionState.launchPermissionRequest()
                    // Диалог остаётся открытым — пользователь выдаёт разрешение и нажимает снова
                }
            },
            onRemoveClick = {
                viewModel.removeProfilePhoto()
                showPhotoOptionsDialog = false
            },
            hasPhoto = uiState.user?.photoUrl != null
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentName = uiState.user?.displayName ?: "",
            onDismiss = { showEditDialog = false },
            onConfirm = { newName ->
                viewModel.updateDisplayName(newName)
                showEditDialog = false
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            state = uiState.passwordChangeState,
            onDismiss = {
                showPasswordDialog = false
                viewModel.resetPasswordChangeState()
            },
            onConfirm = { oldPassword, newPassword ->
                // Диалог закрывается только после успеха: при неверном старом
                // пароле пользователь должен увидеть ошибку и попробовать снова
                viewModel.changePassword(oldPassword, newPassword)
            }
        )
    }

    // Успех закрывает диалог сам
    LaunchedEffect(uiState.passwordChangeState) {
        if (uiState.passwordChangeState is PasswordChangeState.Success) {
            showPasswordDialog = false
            android.widget.Toast
                .makeText(context, "Пароль изменён", android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.resetPasswordChangeState()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isSigningOut) showLogoutDialog = false },
            title = { Text("Выход из аккаунта") },
            text = {
                if (uiState.isSigningOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Отправляем данные на сервер…")
                    }
                } else {
                    Text("Вы уверены, что хотите выйти?")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSigningOut,
                    onClick = { viewModel.signOut(navController) }
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSigningOut,
                    onClick = { showLogoutDialog = false }
                ) { Text("Отмена") }
            }
        )
    }

    // Выход остановлен: данные не уехали на сервер, а выход стирает локальную базу.
    // Раньше в этой ситуации записи молча пропадали.
    if (uiState.logoutBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutBlocked() },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            title = { Text("Данные не отправлены") },
            text = {
                Text(
                    "Не удалось синхронизировать данные с сервером — скорее всего, нет интернета.\n\n" +
                        "Выход удаляет все данные с этого устройства. Если выйти сейчас, " +
                        "несохранённые записи будут потеряны безвозвратно."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissLogoutBlocked()
                    showLogoutDialog = false
                }) { Text("Остаться") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissLogoutBlocked()
                    showLogoutDialog = false
                    viewModel.signOut(navController, force = true)
                }) {
                    Text("Выйти и потерять", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showAppearanceDialog) {
        AppearanceDialog(
            currentTheme = currentTheme,
            currentAccent = currentAccent,
            onDismiss = { showAppearanceDialog = false },
            onThemeSelected = { viewModel.setTheme(it) },
            onAccentSelected = { viewModel.setAccent(it) }
        )
    }

}

@Composable
fun ProfileHeader(
    displayName: String,
    email: String,
    photoUrl: String?,
    isUploading: Boolean,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Фото профиля или иконка по умолчанию
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,  // Это будет путь типа "/data/data/.../photo.jpg"
                    contentDescription = "Фото профиля",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPhotoClick),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPhotoClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Индикатор загрузки
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Иконка камеры для редактирования
            if (!isUploading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clickable(onClick = onPhotoClick),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Изменить фото",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PhotoOptionsDialog(
    onDismiss: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveClick: () -> Unit,
    hasPhoto: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фото профиля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoOptionItem(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Выбрать из галереи",
                    onClick = onGalleryClick
                )
                PhotoOptionItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Сделать фото",
                    onClick = onCameraClick
                )
                if (hasPhoto) {
                    PhotoOptionItem(
                        icon = Icons.Default.Delete,
                        title = "Удалить фото",
                        onClick = onRemoveClick,
                        isDestructive = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun PhotoOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DriverScoreCard(score: DriverScore) {
    val scoreColor = when {
        score.total >= 80 -> MaterialTheme.colorScheme.primary
        score.total >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val levelLabel = when {
        score.total >= 80 -> "Отличный водитель"
        score.total >= 60 -> "Хороший водитель"
        score.total >= 40 -> "Средний уровень"
        else -> "Требует внимания"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    androidx.compose.material3.Text(
                        text = "Рейтинг водителя",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.Text(
                        text = levelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { score.total / 100f },
                        modifier = Modifier.size(64.dp),
                        color = scoreColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        strokeWidth = 6.dp
                    )
                    androidx.compose.material3.Text(
                        text = "${score.total}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScorePillar("ТО", score.maintenanceScore)
                ScorePillar("Бюджет", score.budgetScore)
                ScorePillar("Топливо", score.fuelScore)
                ScorePillar("Регуляр.", score.regularityScore)
            }
        }
    }
}

@Composable
private fun ScorePillar(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(
            text = "$value",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        androidx.compose.material3.Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun StatisticsSection(
    carsCount: Int,
    totalExpenses: Double,
    totalOdometer: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticItem(
                title = "Автомобилей",
                value = carsCount.toString(),
                icon = Icons.Default.DirectionsCar
            )
            StatisticItem(
                title = "Потрачено",
                value = String.format("%.2f ₽", totalExpenses),
                icon = Icons.Default.Payments
            )
            StatisticItem(
                title = "Пробег",
                value = "$totalOdometer км",
                icon = Icons.Default.Speed
            )
        }
    }
}

@Composable
fun StatisticItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Действия профиля, разложенные по смыслу.
 *
 * Раньше это был столбик из восьми одинаковых карточек подряд: «Редактировать
 * профиль», «Чаты», «Внешний вид», «Достижения», «Выйти» — всё одного веса, и
 * глазу не за что зацепиться. Теперь связанное лежит в одной карточке, между
 * разделами есть заголовки, а необратимое отделено от повседневного.
 */
@Composable
fun ActionsSection(
    navController: NavController,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onChangeAppearance: () -> Unit,
    onJoinByCode: () -> Unit,
    onBackup: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit,
    showChangePassword: Boolean = true,
    isBackupInProgress: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsGroup("Профиль") {
            SettingsRow(Icons.Default.Edit, "Редактировать профиль", onClick = onEditProfile)
            SettingsRow(Icons.Default.Palette, "Внешний вид", onClick = onChangeAppearance)
            if (showChangePassword) {
                SettingsRow(Icons.Default.Lock, "Сменить пароль", onClick = onChangePassword)
            }
        }

        // Обязательная точка входа, а не удобство: присоединяются к ЧУЖОЙ машине,
        // а экран «Участники» открывается только изнутри машины, где ты уже
        // состоишь. У человека без автомобилей другого пути принять приглашение нет.
        SettingsGroup("Совместный доступ") {
            SettingsRow(
                Icons.Default.VpnKey,
                "Присоединиться по коду",
                subtitle = "Код присылает владелец автомобиля",
                onClick = onJoinByCode
            )
        }

        SettingsGroup("Приложение") {
            SettingsRow(
                Icons.AutoMirrored.Filled.Chat, "Чаты",
                onClick = { navController.navigateOnce(Screen.ChatsList.route) }
            )
            SettingsRow(
                Icons.Default.Category, "Категории и теги",
                onClick = { navController.navigateOnce(Screen.CategoryManagement.route) }
            )
            SettingsRow(
                Icons.Default.EmojiEvents, "Достижения",
                onClick = { navController.navigateOnce(Screen.Achievements.route) }
            )
            SettingsRow(
                Icons.Default.BugReport, "Сообщить об ошибке",
                onClick = { navController.navigateOnce(Screen.BugReport.route) }
            )
        }

        SettingsGroup("Мои данные") {
            SettingsRow(
                Icons.Default.Backup,
                "Резервная копия",
                subtitle = if (isBackupInProgress) "Собираем файл…" else "Файл со всеми автомобилями и расходами",
                enabled = !isBackupInProgress,
                onClick = onBackup
            )
            SettingsRow(
                Icons.Default.DeleteForever,
                "Удалить аккаунт",
                subtitle = "Вместе со всеми данными, без возможности вернуть",
                isDestructive = true,
                onClick = onDeleteAccount
            )
        }

        SettingsGroup(null) {
            SettingsRow(
                Icons.Default.Logout,
                "Выйти из аккаунта",
                isDestructive = true,
                showChevron = false,
                onClick = onLogout
            )
        }
    }
}

/**
 * Удаление аккаунта. Три экрана подряд, и это не перестраховка.
 *
 * Операция необратима, а при общих машинах задевает посторонних, которые о ней
 * даже не узнают. Поэтому сначала называем цифры, потом даём забрать данные, и
 * только потом просим набрать слово руками — чтобы «Удалить» нельзя было нажать
 * по инерции, как «ОК».
 */
@Composable
fun AccountDeletionDialogs(
    state: AccountDeletionState,
    onCancel: () -> Unit,
    onProceedToBackup: () -> Unit,
    onCreateBackup: () -> Unit,
    onShareBackup: (java.io.File) -> Unit,
    onProceedToConfirm: () -> Unit,
    onTypedChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    when (state) {
        is AccountDeletionState.Idle -> Unit

        is AccountDeletionState.LoadingSummary -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Удаление аккаунта") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Считаем, что будет удалено…")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
        )

        // ── Шаг 1: что именно исчезнет ────────────────────────────────────────
        is AccountDeletionState.Summary -> AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Удалить аккаунт?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Будет удалено безвозвратно:")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DeletionFact("Автомобилей", state.summary.ownedCars)
                        DeletionFact("Записей о расходах", state.summary.expenses)
                    }
                    Text(
                        "Вместе с ними — история обслуживания, документы, страховки, " +
                            "фотографии, чеки и переписка.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Самое важное предупреждение: человек распоряжается не только
                    // своими данными. Без этой строки он нажимает вслепую.
                    if (state.summary.touchesOtherPeople) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                otherPeopleWarning(
                                    people = state.summary.otherParticipants,
                                    cars = state.summary.sharedCars
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onProceedToBackup) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
        )

        // ── Шаг 2: забрать данные с собой ─────────────────────────────────────
        is AccountDeletionState.OfferBackup -> AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Default.Backup, null) },
            title = { Text("Сохранить копию данных?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Файл со всеми автомобилями и расходами. Его можно будет " +
                            "восстановить в новый аккаунт — это единственный способ " +
                            "не потерять историю."
                    )
                    when {
                        state.backupFile != null -> Text(
                            "Копия готова: ${state.backupFile.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.backupError != null -> Text(
                            state.backupError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    state.isCreating -> TextButton(onClick = {}, enabled = false) {
                        Text("Собираем…")
                    }
                    // Файл готов — предлагаем именно отправить его, иначе он
                    // останется во временной папке и исчезнет вместе с кэшем
                    state.backupFile != null -> TextButton(
                        onClick = { onShareBackup(state.backupFile) }
                    ) { Text("Сохранить файл") }

                    else -> TextButton(onClick = onCreateBackup) { Text("Создать копию") }
                }
            },
            dismissButton = {
                TextButton(onClick = onProceedToConfirm) {
                    Text(if (state.backupFile != null) "Дальше" else "Пропустить")
                }
            }
        )

        // ── Шаг 3: подтверждение вводом ───────────────────────────────────────
        is AccountDeletionState.Confirm -> AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Последний шаг") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.backupSaved) {
                        Text(
                            "Копия данных не сохранена — восстановить будет нечем.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text("Наберите «${AccountDeletionState.CONFIRM_WORD}», чтобы подтвердить.")
                    OutlinedTextField(
                        value = state.typed,
                        onValueChange = onTypedChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDelete,
                    enabled = state.canDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить навсегда") }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
        )

        is AccountDeletionState.InProgress -> AlertDialog(
            onDismissRequest = {},   // прерывать посреди удаления нечем и незачем
            title = { Text("Удаляем аккаунт") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Не закрывайте приложение")
                }
            },
            confirmButton = {}
        )

        is AccountDeletionState.Failed -> AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Не получилось") },
            // Ничего не удалено — важно сказать прямо, иначе человек решит,
            // что аккаунт стёрт наполовину
            text = { Text("${state.message}.\n\nДанные на месте, ничего не удалено.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun DeletionFact(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Собирается целой фразой, а не из кусков: при склейке «$n человек потеряют»
 * на одном участнике получается «1 человек потеряют». Здесь заодно согласуется
 * глагол.
 */
private fun otherPeopleWarning(people: Int, cars: Int): String {
    val peoplePart = when {
        people % 10 == 1 && people % 100 != 11 -> "$people человек потеряет"
        people % 10 in 2..4 && people % 100 !in 12..14 -> "$people человека потеряют"
        else -> "$people человек потеряют"
    }
    val carsPart = when {
        cars % 10 == 1 && cars % 100 != 11 -> "$cars машине"
        else -> "$cars машинам"
    }
    return "Ещё $peoplePart доступ к $carsPart, которыми вы пользуетесь вместе, " +
        "— вместе со всей историей. Предупредите их заранее."
}

/** Заголовок + одна карточка на группу: связанное держится вместе */
@Composable
fun SettingsGroup(
    title: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Стрелка отличает переход на другой экран от действия на месте
        if (showChevron) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun NotificationSection(
    notifMaintenance: Boolean,
    notifInsurance: Boolean,
    notifDigest: Boolean,
    notifFuel: Boolean,
    notifBudgetAlert: Boolean,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    onToggleMaintenance: (Boolean) -> Unit,
    onToggleInsurance: (Boolean) -> Unit,
    onToggleDigest: (Boolean) -> Unit,
    onToggleFuel: (Boolean) -> Unit,
    onToggleBudgetAlert: (Boolean) -> Unit,
    onToggleQuietHours: (Boolean) -> Unit,
    onQuietHoursStartChange: (Int) -> Unit,
    onQuietHoursEndChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Уведомления",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Пока уведомления запрещены системой, все переключатели ниже —
            // ложь: они сохраняются, но ни одно напоминание не придёт
            com.aggin.carcost.presentation.components.NotificationsDisabledWarning(
                modifier = Modifier.padding(bottom = 12.dp)
            )

            NotifToggleRow("Напоминания о ТО", notifMaintenance, onToggleMaintenance)
            NotifToggleRow("Страховка", notifInsurance, onToggleInsurance)
            NotifToggleRow("Еженедельный дайджест", notifDigest, onToggleDigest)
            NotifToggleRow("Низкий уровень топлива", notifFuel, onToggleFuel)
            NotifToggleRow("Превышение 80% бюджета", notifBudgetAlert, onToggleBudgetAlert)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── Тихие часы ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Тихие часы", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Не беспокоить с ${quietHoursStart}:00 до ${quietHoursEnd}:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = quietHoursEnabled, onCheckedChange = onToggleQuietHours)
            }

            if (quietHoursEnabled) {
                Spacer(Modifier.height(12.dp))
                // Слайдер начала (0–23)
                Text(
                    "Начало: ${quietHoursStart}:00",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Slider(
                    value = quietHoursStart.toFloat(),
                    onValueChange = { onQuietHoursStartChange(it.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                // Слайдер конца (0–23)
                Text(
                    "Конец: ${quietHoursEnd}:00",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Slider(
                    value = quietHoursEnd.toFloat(),
                    onValueChange = { onQuietHoursEndChange(it.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NotifToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current

    // Момент, когда человек включает напоминание, — единственный, когда просить
    // разрешение уместно: он только что сказал, что хочет его получать.
    // Раньше спрашивали один раз в онбординге и больше никогда.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            com.aggin.carcost.presentation.components.openNotificationSettings(context)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = { value ->
                // Настройку сохраняем в любом случае: она про желание человека,
                // а не про состояние системного разрешения
                onCheckedChange(value)

                if (value && !com.aggin.carcost.presentation.components.notificationsEnabled(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        com.aggin.carcost.presentation.components.openNotificationSettings(context)
                    }
                }
            }
        )
    }
}

@Composable
fun AppInfoSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CarCost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Версия 1.0.8",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать профиль") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordDialog(
    state: PasswordChangeState,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val inProgress = state is PasswordChangeState.InProgress
    // Ошибка с сервера (неверный текущий пароль) важнее локальной валидации
    val error = (state as? PasswordChangeState.Error)?.message ?: localError

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Сменить пароль") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; localError = null },
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    enabled = !inProgress,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    enabled = !inProgress,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text("Подтвердите пароль") },
                    singleLine = true,
                    enabled = !inProgress,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !inProgress,
                onClick = {
                    when {
                        oldPassword.isBlank() || newPassword.isBlank() -> localError = "Заполните все поля"
                        newPassword.length < 6 -> localError = "Пароль должен быть не менее 6 символов"
                        newPassword != confirmPassword -> localError = "Пароли не совпадают"
                        else -> { localError = null; onConfirm(oldPassword, newPassword) }
                    }
                }
            ) { Text(if (inProgress) "Проверяем…" else "Сохранить") }
        },
        dismissButton = {
            TextButton(enabled = !inProgress, onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun AccentColorDialog(
    currentAccent: String,
    onDismiss: () -> Unit,
    onAccentSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Акцентный цвет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Выберите цветовую схему приложения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Color swatch grid — 2 rows of ~3
                val chunks = AccentScheme.entries.chunked(3)
                chunks.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { scheme ->
                            val selected = currentAccent == scheme.key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAccentSelected(scheme.key) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(scheme.previewColor)
                                        .then(
                                            if (selected) Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    scheme.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Filler for incomplete row
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun AppearanceDialog(
    currentTheme: String,
    currentAccent: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onAccentSelected: (String) -> Unit
) {
    val themeOptions = mapOf("Light" to "Светлая", "Dark" to "Тёмная", "System" to "Системная")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внешний вид") },
        text = {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
            ) {
                    // ── Тема ──
                    Text(
                        "Тема",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    themeOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentTheme == key,
                                    onClick = { onThemeSelected(key) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentTheme == key, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // ── Акцентный цвет ──
                    Text(
                        "Акцентный цвет",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    val chunks = AccentScheme.entries.chunked(3)
                    chunks.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { scheme ->
                                val selected = currentAccent == scheme.key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f).clickable { onAccentSelected(scheme.key) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(scheme.previewColor)
                                            .then(
                                                if (selected) Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                ) else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        scheme.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themeOptions = mapOf("Light" to "Светлая", "Dark" to "Темная", "System" to "Системная")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тему") },
        text = {
            Column {
                themeOptions.forEach { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (currentTheme == key),
                                onClick = { onThemeSelected(key) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentTheme == key),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = value)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}