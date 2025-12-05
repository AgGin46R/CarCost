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
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    carId: String,
    navController: NavController,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var currentLocation by remember {
        mutableStateOf<Point?>(null)
    }

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // ✅ УДАЛЕНО: Инициализация MapKit теперь в App.kt
    // Оставляем только управление жизненным циклом
    DisposableEffect(Unit) {
        MapKitFactory.getInstance().onStart()
        onDispose {
            MapKitFactory.getInstance().onStop()
        }
    }

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

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val location = fusedLocationClient.lastLocation.await()
                location?.let {
                    currentLocation = Point(it.latitude, it.longitude)
                }
            } catch (e: Exception) {
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
                    YandexMapView(
                        expenses = uiState.expenses,
                        currentLocation = currentLocation,
                        modifier = Modifier.fillMaxSize()
                    )

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
    currentLocation: Point?,
    modifier: Modifier = Modifier
) {
    var isInitialCameraMoveDone by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
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
            mapView.map.mapObjects.clear()

            expenses.forEach { expense ->
                if (expense.latitude != null && expense.longitude != null) {
                    val point = Point(expense.latitude, expense.longitude)
                    mapView.map.mapObjects.addPlacemark(point).apply {
                        setText(getCategoryShortName(expense.category))
                    }
                }
            }

            if (currentLocation != null && !isInitialCameraMoveDone) {
                mapView.map.move(
                    CameraPosition(currentLocation, 15.0f, 0.0f, 0.0f),
                    Animation(Animation.Type.SMOOTH, 1f),
                    null
                )
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