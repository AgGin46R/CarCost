package com.aggin.carcost.presentation.screens.profile

import android.Manifest
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.presentation.navigation.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val currentTheme by settingsManager.themeFlow.collectAsState(initial = "System")
    var showThemeDialog by remember { mutableStateOf(false) }

    // Разрешение на камеру
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

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

    Scaffold(
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
            ProfileHeader(
                displayName = uiState.user?.displayName ?: "Пользователь",
                email = uiState.user?.email ?: "",
                photoUrl = uiState.user?.photoUrl,
                isUploading = uiState.isUploadingPhoto,
                onPhotoClick = { showPhotoOptionsDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatisticsSection(
                carsCount = uiState.statistics.carsCount,
                totalExpenses = uiState.statistics.totalExpenses,
                totalOdometer = uiState.statistics.totalOdometer
            )

            Spacer(modifier = Modifier.height(24.dp))

            ActionsSection(
                navController = navController,
                onEditProfile = { showEditDialog = true },
                onChangePassword = { showPasswordDialog = true },
                onChangeTheme = { showThemeDialog = true },
                onLogout = { showLogoutDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
            AppInfoSection()
        }
    }

    // Диалог выбора источника фото
    if (showPhotoOptionsDialog) {
        PhotoOptionsDialog(
            onDismiss = { showPhotoOptionsDialog = false },
            onGalleryClick = {
                galleryLauncher.launch("image/*")
                showPhotoOptionsDialog = false
            },
            onCameraClick = {
                // Проверяем разрешение на камеру
                if (cameraPermissionState.status.isGranted) {
                    val uri = viewModel.createTempImageUri(context)
                    if (uri != null) {
                        cameraLauncher.launch(uri)
                    }
                } else {
                    // Запрашиваем разрешение
                    cameraPermissionState.launchPermissionRequest()
                }
                showPhotoOptionsDialog = false
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
            onDismiss = { showPasswordDialog = false },
            onConfirm = { oldPassword, newPassword ->
                viewModel.changePassword(oldPassword, newPassword)
                showPasswordDialog = false
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выход из аккаунта") },
            text = { Text("Вы уверены, что хотите выйти?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut(navController)
                        showLogoutDialog = false
                    }
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                viewModel.setTheme(theme)
            }
        )
    }

    // Показываем ошибки
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Можно добавить Snackbar
        }
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
            fontWeight = FontWeight.Bold
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
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
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Статистика",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.DirectionsCar,
                    label = "Автомобилей",
                    value = carsCount.toString()
                )
                StatItem(
                    icon = Icons.Default.MonetizationOn,
                    label = "Расходы",
                    value = String.format("%.0f ₽", totalExpenses)
                )
                StatItem(
                    icon = Icons.Default.Speed,
                    label = "Пробег",
                    value = "$totalOdometer км"
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ActionsSection(
    navController: NavController,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onChangeTheme: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Действия",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ActionItem(icon = Icons.Default.Edit, title = "Редактировать профиль", onClick = onEditProfile)
        ActionItem(icon = Icons.Default.Palette, title = "Тема оформления", onClick = onChangeTheme)
        ActionItem(icon = Icons.Default.Lock, title = "Сменить пароль", onClick = onChangePassword)
        ActionItem(
            icon = Icons.Default.Category,
            title = "Категории и теги",
            onClick = { navController.navigate(Screen.CategoryManagement.route) }
        )
        ActionItem(
            icon = Icons.Default.Logout,
            title = "Выйти из аккаунта",
            onClick = onLogout,
            isDestructive = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
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
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Подтвердите пароль") },
                    singleLine = true,
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
                onClick = {
                    when {
                        oldPassword.isBlank() || newPassword.isBlank() -> error = "Заполните все поля"
                        newPassword.length < 6 -> error = "Пароль должен быть не менее 6 символов"
                        newPassword != confirmPassword -> error = "Пароли не совпадают"
                        else -> onConfirm(oldPassword, newPassword)
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
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