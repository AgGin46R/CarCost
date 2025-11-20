package com.aggin.carcost.presentation.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.google.android.gms.location.LocationServices
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // Мы проверяем разрешения вручную
@Composable
fun MapScreen(
    carId: Long,
    navController: NavController,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // НОВОЕ: Клиент для получения геолокации
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // НОВОЕ: Состояние для хранения текущего местоположения
    var currentLocation by remember {
        mutableStateOf<Point?>(null)
    }

    // Устанавливаем ID автомобиля
    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // Инициализируем MapKit
    DisposableEffect(Unit) {
        MapKitFactory.initialize(context)
        onDispose {
            MapKitFactory.getInstance().onStop()
        }
    }

    // Запрос разрешений на геолокацию
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // НОВОЕ: Эффект для получения геолокации после получения разрешения
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val location = fusedLocationClient.lastLocation.await()
                location?.let {
                    currentLocation = Point(it.latitude, it.longitude)
                }
            } catch (e: Exception) {
                // Обработка ошибок
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта расходов") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasLocationPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Требуется доступ к геолокации",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }) {
                        Text("Предоставить доступ")
                    }
                }
            } else {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    // ИЗМЕНЕНО: Передаем текущее местоположение в YandexMapView
                    YandexMapView(
                        expenses = uiState.expenses,
                        currentLocation = currentLocation, // Передаем сюда
                        modifier = Modifier.fillMaxSize()
                    )

                    // Информация о количестве точек
                    if (uiState.expenses.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Найдено мест: ${uiState.expenses.size}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YandexMapView(
    expenses: List<Expense>,
    // ИЗМЕНЕНО: Добавляем новый параметр
    currentLocation: Point?,
    modifier: Modifier = Modifier
) {
    // НОВОЕ: Флаг, чтобы переместить камеру только один раз
    var isInitialCameraMoveDone by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                // Начальная позиция (центр России) - она будет быстро заменена
                val startPoint = Point(55.751244, 37.618423)
                map.move(
                    CameraPosition(startPoint, 5.0f, 0.0f, 0.0f)
                )

                onStart()
                MapKitFactory.getInstance().onStart()
            }
        },
        modifier = modifier,
        update = { mapView ->
            // Удаляем старые метки
            mapView.map.mapObjects.clear()

            // Добавляем метки для каждого расхода
            expenses.forEach { expense ->
                if (expense.latitude != null && expense.longitude != null) {
                    val point = Point(expense.latitude, expense.longitude)
                    mapView.map.mapObjects.addPlacemark(point).apply {
                        setText(getCategoryShortName(expense.category))
                    }
                }
            }

            // ИЗМЕНЕНО: Логика перемещения камеры
            // Если мы получили геолокацию и еще не перемещали камеру
            if (currentLocation != null && !isInitialCameraMoveDone) {
                mapView.map.move(
                    CameraPosition(currentLocation, 15.0f, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 1f),
                    null
                )
                // Устанавливаем флаг, чтобы больше не перемещать камеру автоматически
                isInitialCameraMoveDone = true
            }
        }
    )
}

fun getCategoryShortName(category: ExpenseCategory): String {
    return when (category) {
        ExpenseCategory.FUEL -> "⛽"
        ExpenseCategory.MAINTENANCE -> "🔧"
        ExpenseCategory.REPAIR -> "🛠️"
        ExpenseCategory.PARKING -> "🅿️"
        ExpenseCategory.WASH -> "💧"
        else -> "📍"
    }
}