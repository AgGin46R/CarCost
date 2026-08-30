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
import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import com.aggin.carcost.data.local.settings.LocaleManager
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
                    text = stringResource(R.string.profile_adres_dlya_priglasheniy),
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
                    text = stringResource(R.string.profile_soobschite_ego_tomu_kto_dobavit_vas_v),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(address))
                android.widget.Toast
                    .makeText(context, context.getString(R.string.profile_adres_skopirovan), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.carmembers_skopirovat))
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
                text = stringResource(R.string.profile_sposoby_vhoda),
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
                    Text(stringResource(R.string.profile_vkontakte), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            vkLink != null && vkLink.displayName.isNotBlank() -> vkLink.displayName
                            vkLink != null -> stringResource(R.string.profile_privyazan)
                            notLinked -> stringResource(R.string.profile_ne_privyazan)
                            else -> stringResource(R.string.profile_proveryaem)
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
                    vkLink == null -> TextButton(onClick = onLink) { Text(stringResource(R.string.profile_privyazat)) }
                    canUnlink -> TextButton(onClick = { showUnlinkDialog = true }) {
                        Text(stringResource(R.string.profile_otvyazat), color = MaterialTheme.colorScheme.error)
                    }
                    // Иначе — привязан и отвязать нельзя: кнопки нет вовсе
                }
            }

            if (notLinked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profile_posle_privyazki_vhod_cherez_vkontakte) +
                        stringResource(R.string.profile_a_ne_sozdavat_novyy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text(stringResource(R.string.profile_otvyazat_vkontakte)) },
            text = { Text(stringResource(R.string.profile_vhod_cherez_vkontakte_perestanet_vesti_v)) },
            confirmButton = {
                TextButton(onClick = { showUnlinkDialog = false; onUnlink() }) {
                    Text(stringResource(R.string.profile_otvyazat), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
                title = { Text(stringResource(R.string.home_profil)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
                displayName = uiState.user?.displayName ?: stringResource(R.string.profile_polzovatel),
                email = if (isSyntheticEmail) stringResource(R.string.profile_vhod_cherez_vkontakte) else userEmail,
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
                .makeText(context, context.getString(R.string.profile_parol_izmenen), android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.resetPasswordChangeState()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isSigningOut) showLogoutDialog = false },
            title = { Text(stringResource(R.string.profile_vyhod_iz_akkaunta)) },
            text = {
                if (uiState.isSigningOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.profile_otpravlyaem_dannye_na_server))
                    }
                } else {
                    Text(stringResource(R.string.profile_vy_uvereny_chto_hotite_vyyti))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSigningOut,
                    onClick = { viewModel.signOut(navController) }
                ) { Text(stringResource(R.string.profile_vyyti)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSigningOut,
                    onClick = { showLogoutDialog = false }
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Выход остановлен: данные не уехали на сервер, а выход стирает локальную базу.
    // Раньше в этой ситуации записи молча пропадали.
    if (uiState.logoutBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutBlocked() },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            title = { Text(stringResource(R.string.profile_dannye_ne_otpravleny)) },
            text = {
                Text(
                    stringResource(R.string.profile_ne_udalos_sinhronizirovat_dannye_s) +
                        stringResource(R.string.profile_vyhod_udalyaet_vse_dannye_s_etogo) +
                        stringResource(R.string.profile_nesohranennye_zapisi_budut_poteryany)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissLogoutBlocked()
                    showLogoutDialog = false
                }) { Text(stringResource(R.string.profile_ostatsya)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissLogoutBlocked()
                    showLogoutDialog = false
                    viewModel.signOut(navController, force = true)
                }) {
                    Text(stringResource(R.string.profile_vyyti_i_poteryat), color = MaterialTheme.colorScheme.error)
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
                    contentDescription = stringResource(R.string.profile_foto_profilya),
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
                            contentDescription = stringResource(R.string.profile_izmenit_foto),
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
        title = { Text(stringResource(R.string.profile_foto_profilya)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoOptionItem(
                    icon = Icons.Default.PhotoLibrary,
                    title = stringResource(R.string.profile_vybrat_iz_galerei),
                    onClick = onGalleryClick
                )
                PhotoOptionItem(
                    icon = Icons.Default.CameraAlt,
                    title = stringResource(R.string.profile_sdelat_foto),
                    onClick = onCameraClick
                )
                if (hasPhoto) {
                    PhotoOptionItem(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.profile_udalit_foto),
                        onClick = onRemoveClick,
                        isDestructive = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        score.total >= 80 -> stringResource(R.string.profile_otlichnyy_voditel)
        score.total >= 60 -> stringResource(R.string.profile_horoshiy_voditel)
        score.total >= 40 -> stringResource(R.string.profile_sredniy_uroven)
        else -> stringResource(R.string.cardetail_trebuet_vnimaniya)
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
                        text = stringResource(R.string.profile_reyting_voditelya),
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
                ScorePillar(stringResource(R.string.profile_to), score.maintenanceScore)
                ScorePillar(stringResource(R.string.cardetail_byudzhet), score.budgetScore)
                ScorePillar(stringResource(R.string.home_toplivo), score.fuelScore)
                ScorePillar(stringResource(R.string.profile_regulyar), score.regularityScore)
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
                title = stringResource(R.string.profile_avtomobiley),
                value = carsCount.toString(),
                icon = Icons.Default.DirectionsCar
            )
            StatisticItem(
                title = stringResource(R.string.budget_potracheno),
                value = String.format("%.2f ₽", totalExpenses),
                icon = Icons.Default.Payments
            )
            StatisticItem(
                title = stringResource(R.string.home_probeg),
                value = stringResource(R.string.home_km, totalOdometer),
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
        SettingsGroup(stringResource(R.string.home_profil)) {
            SettingsRow(Icons.Default.Edit, stringResource(R.string.profile_redaktirovat_profil), onClick = onEditProfile)
            SettingsRow(Icons.Default.Palette, stringResource(R.string.profile_vneshniy_vid), onClick = onChangeAppearance)
            if (showChangePassword) {
                SettingsRow(Icons.Default.Lock, stringResource(R.string.profile_smenit_parol), onClick = onChangePassword)
            }
        }

        // Обязательная точка входа, а не удобство: присоединяются к ЧУЖОЙ машине,
        // а экран «Участники» открывается только изнутри машины, где ты уже
        // состоишь. У человека без автомобилей другого пути принять приглашение нет.
        SettingsGroup(stringResource(R.string.profile_sovmestnyy_dostup)) {
            SettingsRow(
                Icons.Default.VpnKey,
                stringResource(R.string.components_prisoedinitsya_po_kodu),
                subtitle = stringResource(R.string.profile_kod_prisylaet_vladelets_avtomobilya),
                onClick = onJoinByCode
            )
        }

        SettingsGroup(stringResource(R.string.profile_prilozhenie)) {
            // Язык первым в группе: человек, которому нужен другой язык, ищет
            // этот пункт по значку и не может прочитать подписи вокруг
            var showLanguages by remember { mutableStateOf(false) }
            val languageContext = LocalContext.current
            SettingsRow(
                Icons.Default.Language,
                stringResource(R.string.language_title),
                subtitle = LocaleManager.currentName(languageContext),
                onClick = { showLanguages = true }
            )
            if (showLanguages) {
                LanguageDialog(
                    current = LocaleManager.current(languageContext),
                    onDismiss = { showLanguages = false },
                    onPick = { tag ->
                        showLanguages = false
                        if (tag != LocaleManager.current(languageContext)) {
                            LocaleManager.setLanguage(languageContext, tag)
                            // Пересоздаём activity: подмена конфигурации до уже
                            // отрисованных экранов сама не доходит
                            (languageContext as? android.app.Activity)?.recreate()
                        }
                    }
                )
            }
            SettingsRow(
                Icons.AutoMirrored.Filled.Chat, stringResource(R.string.chat_chaty),
                onClick = { navController.navigateOnce(Screen.ChatsList.route) }
            )
            SettingsRow(
                Icons.Default.Category, stringResource(R.string.profile_kategorii_i_tegi),
                onClick = { navController.navigateOnce(Screen.CategoryManagement.route) }
            )
            SettingsRow(
                Icons.Default.EmojiEvents, stringResource(R.string.achievements_dostizheniya),
                onClick = { navController.navigateOnce(Screen.Achievements.route) }
            )
            SettingsRow(
                Icons.Default.BugReport, stringResource(R.string.bugreport_soobschit_ob_oshibke),
                onClick = { navController.navigateOnce(Screen.BugReport.route) }
            )
            // Согласие даётся при регистрации, но сам документ должен оставаться
            // доступным и после неё — этого требует RuStore, да и просто разумно
            SettingsRow(
                Icons.Default.PrivacyTip,
                stringResource(R.string.auth_politika_konfidentsialnosti),
                subtitle = stringResource(R.string.profile_kakie_dannye_sobirayutsya_i_zachem),
                onClick = { navController.navigateOnce(Screen.PrivacyPolicy.route) }
            )
            SettingsRow(
                Icons.Default.Description,
                stringResource(R.string.profile_polzovatelskoe_soglashenie),
                subtitle = stringResource(R.string.profile_usloviya_ispolzovaniya_prilozheniya),
                onClick = { navController.navigateOnce(Screen.TermsOfUse.route) }
            )
        }

        SettingsGroup(stringResource(R.string.profile_moi_dannye)) {
            SettingsRow(
                Icons.Default.Backup,
                stringResource(R.string.profile_rezervnaya_kopiya),
                subtitle = if (isBackupInProgress) stringResource(R.string.profile_sobiraem_fayl) else stringResource(R.string.profile_fayl_so_vsemi_avtomobilyami_i_rashodami),
                enabled = !isBackupInProgress,
                onClick = onBackup
            )
            SettingsRow(
                Icons.Default.DeleteForever,
                stringResource(R.string.profile_udalit_akkaunt),
                subtitle = stringResource(R.string.profile_vmeste_so_vsemi_dannymi_bez_vozmozhnosti),
                isDestructive = true,
                onClick = onDeleteAccount
            )
        }

        SettingsGroup(null) {
            SettingsRow(
                Icons.Default.Logout,
                stringResource(R.string.profile_vyyti_iz_akkaunta),
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
            title = { Text(stringResource(R.string.profile_udalenie_akkaunta)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.profile_schitaem_chto_budet_udaleno))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
        )

        // ── Шаг 1: что именно исчезнет ────────────────────────────────────────
        is AccountDeletionState.Summary -> AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.profile_udalit_akkaunt_2)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.profile_budet_udaleno_bezvozvratno))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DeletionFact(stringResource(R.string.profile_avtomobiley), state.summary.ownedCars)
                        DeletionFact(stringResource(R.string.profile_zapisey_o_rashodah), state.summary.expenses)
                    }
                    Text(
                        stringResource(R.string.profile_vmeste_s_nimi_istoriya_obsluzhivaniya) +
                            stringResource(R.string.profile_fotografii_cheki_i_perepiska),
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
                TextButton(onClick = onProceedToBackup) { Text(stringResource(R.string.auth_prodolzhit)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
        )

        // ── Шаг 2: забрать данные с собой ─────────────────────────────────────
        is AccountDeletionState.OfferBackup -> AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Default.Backup, null) },
            title = { Text(stringResource(R.string.profile_sohranit_kopiyu_dannyh)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.profile_fayl_so_vsemi_avtomobilyami_i_rashodami_2) +
                            stringResource(R.string.profile_vosstanovit_v_novyy_akkaunt_eto) +
                            stringResource(R.string.profile_ne_poteryat_istoriyu)
                    )
                    when {
                        state.backupFile != null -> Text(
                            stringResource(R.string.profile_kopiya_gotova, state.backupFile.name),
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
                        Text(stringResource(R.string.profile_sobiraem))
                    }
                    // Файл готов — предлагаем именно отправить его, иначе он
                    // останется во временной папке и исчезнет вместе с кэшем
                    state.backupFile != null -> TextButton(
                        onClick = { onShareBackup(state.backupFile) }
                    ) { Text(stringResource(R.string.chat_sohranit_fayl)) }

                    else -> TextButton(onClick = onCreateBackup) { Text(stringResource(R.string.profile_sozdat_kopiyu)) }
                }
            },
            dismissButton = {
                TextButton(onClick = onProceedToConfirm) {
                    Text(if (state.backupFile != null) stringResource(R.string.profile_dalshe) else stringResource(R.string.action_skip))
                }
            }
        )

        // ── Шаг 3: подтверждение вводом ───────────────────────────────────────
        is AccountDeletionState.Confirm -> AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.profile_posledniy_shag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.backupSaved) {
                        Text(
                            stringResource(R.string.profile_kopiya_dannyh_ne_sohranena_vosstanovit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(stringResource(R.string.profile_naberite_chtoby_podtverdit, stringResource(AccountDeletionState.CONFIRM_WORD_RES)))
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
                    enabled = state.canDelete(LocalContext.current),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.profile_udalit_navsegda)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
        )

        is AccountDeletionState.InProgress -> AlertDialog(
            onDismissRequest = {},   // прерывать посреди удаления нечем и незачем
            title = { Text(stringResource(R.string.profile_udalyaem_akkaunt)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.profile_ne_zakryvayte_prilozhenie))
                }
            },
            confirmButton = {}
        )

        is AccountDeletionState.Failed -> AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.profile_ne_poluchilos)) },
            // Ничего не удалено — важно сказать прямо, иначе человек решит,
            // что аккаунт стёрт наполовину
            text = { Text(stringResource(R.string.profile_n_ndannye_na_meste_nichego_ne_udaleno, state.message)) },
            confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_close)) } }
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
@Composable
private fun otherPeopleWarning(people: Int, cars: Int): String {
    val peoplePart = when {
        people % 10 == 1 && people % 100 != 11 -> stringResource(R.string.profile_chelovek_poteryaet, people)
        people % 10 in 2..4 && people % 100 !in 12..14 -> stringResource(R.string.profile_cheloveka_poteryayut, people)
        else -> stringResource(R.string.profile_chelovek_poteryayut, people)
    }
    val carsPart = when {
        cars % 10 == 1 && cars % 100 != 11 -> stringResource(R.string.profile_mashine, cars)
        else -> stringResource(R.string.profile_mashinam, cars)
    }
    return stringResource(R.string.profile_esche_dostup_k_kotorymi_vy_polzuetes, peoplePart, carsPart) +
        stringResource(R.string.profile_vmeste_so_vsey_istoriey_predupredite_ih)
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
                text = stringResource(R.string.onboarding_uvedomleniya),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Пока уведомления запрещены системой, все переключатели ниже —
            // ложь: они сохраняются, но ни одно напоминание не придёт
            com.aggin.carcost.presentation.components.NotificationsDisabledWarning(
                modifier = Modifier.padding(bottom = 12.dp)
            )

            NotifToggleRow(stringResource(R.string.profile_napominaniya_o_to), notifMaintenance, onToggleMaintenance)
            NotifToggleRow(stringResource(R.string.profile_strahovka), notifInsurance, onToggleInsurance)
            NotifToggleRow(stringResource(R.string.profile_ezhenedelnyy_daydzhest), notifDigest, onToggleDigest)
            NotifToggleRow(stringResource(R.string.profile_nizkiy_uroven_topliva), notifFuel, onToggleFuel)
            NotifToggleRow(stringResource(R.string.profile_prevyshenie_80_byudzheta), notifBudgetAlert, onToggleBudgetAlert)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── Тихие часы ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.profile_tihie_chasy), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.profile_ne_bespokoit_s_00_do_00, quietHoursStart, quietHoursEnd),
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
                    stringResource(R.string.profile_nachalo_00, quietHoursStart),
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
                    stringResource(R.string.profile_konets_00, quietHoursEnd),
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
            stringResource(R.string.profile_versiya, com.aggin.carcost.BuildConfig.VERSION_NAME),
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
        title = { Text(stringResource(R.string.profile_redaktirovat_profil)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.auth_imya)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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

    // Читаются здесь: ниже они нужны внутри onClick, а это уже не композиция
    val errEmptyFields = stringResource(R.string.profile_zapolnite_vse_polya)
    val errShortPassword = stringResource(R.string.profile_parol_dolzhen_byt_ne_menee_6_simvolov)
    val errMismatch = stringResource(R.string.profile_paroli_ne_sovpadayut)

    val inProgress = state is PasswordChangeState.InProgress
    // Ошибка с сервера (неверный текущий пароль) важнее локальной валидации
    val error = (state as? PasswordChangeState.Error)?.message ?: localError

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(stringResource(R.string.profile_smenit_parol)) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; localError = null },
                    label = { Text(stringResource(R.string.profile_tekuschiy_parol)) },
                    singleLine = true,
                    enabled = !inProgress,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null },
                    label = { Text(stringResource(R.string.auth_novyy_parol)) },
                    singleLine = true,
                    enabled = !inProgress,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text(stringResource(R.string.auth_podtverdite_parol)) },
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
                        oldPassword.isBlank() || newPassword.isBlank() -> localError = errEmptyFields
                        newPassword.length < 6 -> localError = errShortPassword
                        newPassword != confirmPassword -> localError = errMismatch
                        else -> { localError = null; onConfirm(oldPassword, newPassword) }
                    }
                }
            ) { Text(if (inProgress) stringResource(R.string.profile_proveryaem) else stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(enabled = !inProgress, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        title = { Text(stringResource(R.string.profile_aktsentnyy_tsvet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.profile_vyberite_tsvetovuyu_shemu_prilozheniya),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
    val themeOptions = mapOf("Light" to stringResource(R.string.profile_svetlaya), "Dark" to stringResource(R.string.profile_temnaya), "System" to stringResource(R.string.profile_sistemnaya))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_vneshniy_vid)) },
        text = {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
            ) {
                    // ── Тема ──
                    Text(
                        stringResource(R.string.profile_tema),
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
                        stringResource(R.string.profile_aktsentnyy_tsvet),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themeOptions = mapOf("Light" to stringResource(R.string.profile_svetlaya), "Dark" to stringResource(R.string.profile_temnaya_2), "System" to stringResource(R.string.profile_sistemnaya))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_vyberite_temu)) },
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
                Text(stringResource(R.string.action_close))
            }
        }
    )
}


/**
 * Выбор языка интерфейса.
 *
 * Названия языков написаны каждое на своём языке и потому не переводятся:
 * человек, которому нужен казахский, ищет в списке «Қазақша», а не «Казахский»
 * на языке, которого может не знать.
 */
@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                LocaleManager.supported.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(language.tag) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language.tag == current,
                            onClick = { onPick(language.tag) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = language.nativeName
                                ?: stringResource(R.string.language_system),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.language_restart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
