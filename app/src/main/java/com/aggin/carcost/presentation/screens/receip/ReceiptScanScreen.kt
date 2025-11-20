package com.aggin.carcost.presentation.screens.receipt_scan

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReceiptScanScreen(
    navController: NavController,
    carId: Long,
    viewModel: ReceiptScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // --- ЛОГИКА ЗАПРОСА РАЗРЕШЕНИЯ НА КАМЕРУ ---
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )
    // --- КОНЕЦ БЛОКА ---

    // Launcher для выбора изображения из галереи
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Загружаем bitmap
            val inputStream: InputStream? = context.contentResolver.openInputStream(it)
            imageBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Запускаем сканирование
            viewModel.scanReceipt(it, context)
        }
    }

    // Launcher для съемки фото
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imageBitmap = it
            // Запускаем сканирование
            viewModel.scanReceiptFromBitmap(it, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканирование чека") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопки выбора источника
            if (imageBitmap == null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Отсканируйте чек",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Приложение автоматически распознает сумму и дату",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Кнопка камеры с проверкой разрешений
                        Button(
                            onClick = {
                                if (cameraPermissionState.status.isGranted) {
                                    // Если разрешение уже есть, запускаем камеру
                                    cameraLauncher.launch(null)
                                } else {
                                    // Если разрешения нет, запрашиваем его
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сфотографировать чек")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Кнопка галереи
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать из галереи")
                        }
                    }
                }
            }

            // Превью изображения
            imageBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Фото чека",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                imageBitmap = null
                                selectedImageUri = null
                                viewModel.reset()
                            }) {
                                Icon(Icons.Default.Delete, "Удалить")
                            }

                            IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Refresh, "Выбрать другое")
                            }
                        }
                    }
                }
            }

            // Результаты сканирования
            when {
                uiState.isScanning -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Сканирование чека...")
                }

                uiState.receiptData != null -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Результаты сканирования",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Сумма
                            uiState.receiptData?.amount?.let { amount ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Сумма:")
                                    Text(
                                        text = String.format("%.2f ₽", amount),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } ?: run {
                                Text(
                                    "Сумма не найдена",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Дата
                            uiState.receiptData?.date?.let { date ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Дата:")
                                    Text(
                                        text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(date))
                                    )
                                }
                            }

                            // Распознанный текст (свернуто)
                            if (uiState.receiptData?.text?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(12.dp))
                                var showFullText by remember { mutableStateOf(false) }

                                TextButton(
                                    onClick = { showFullText = !showFullText }
                                ) {
                                    Text(if (showFullText) "Скрыть текст" else "Показать распознанный текст")
                                }

                                if (showFullText) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = uiState.receiptData?.text ?: "",
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопки действий
                    Button(
                        onClick = {
                            uiState.receiptData?.let { data ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("scanned_amount", data.amount)
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("scanned_date", data.date)
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("receipt_photo_uri", selectedImageUri.toString())
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.receiptData?.amount != null
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Применить данные")
                    }
                }

                uiState.error != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                uiState.error ?: "Неизвестная ошибка",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}