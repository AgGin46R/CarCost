package com.aggin.carcost.presentation.screens.map

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
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
import com.aggin.carcost.presentation.navigation.navigateOnce
import com.aggin.carcost.presentation.common.color
import com.aggin.carcost.presentation.common.emoji
import androidx.compose.ui.graphics.toArgb
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.saveable.rememberSaveable

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

    /** Расход, по метке которого нажали: показываем ту же карточку, что в списке */
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var currentLocation by remember {
        mutableStateOf<Point?>(null)
    }

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // MapKit поднимается лениво: он больше не инициализируется при каждом
    // запуске приложения, а только когда впервые понадобилась карта.
    // getInstance() без initialize() бросает исключение, поэтому вызов
    // обязан быть ДО него.
    com.aggin.carcost.App.ensureMapKit(androidx.compose.ui.platform.LocalContext.current)

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
                title = { Text(stringResource(R.string.map_karta_rashodov)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
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
                        text = stringResource(R.string.map_trebuetsya_dostup_k_geolokatsii),
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
                        Text(stringResource(R.string.map_predostavit_dostup))
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
                        modifier = Modifier.fillMaxSize(),
                        onExpenseClick = { selectedExpense = it }
                    )

                    // Category filter chips — top overlay
                    if (uiState.availableCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // "All" chip
                            FilterChip(
                                selected = uiState.selectedCategories.isEmpty(),
                                onClick = { viewModel.clearFilter() },
                                label = { Text(stringResource(R.string.map_vse)) },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, null, Modifier.size(14.dp)) }
                            )
                            uiState.availableCategories.sortedBy { it.name }.forEach { cat ->
                                FilterChip(
                                    selected = cat in uiState.selectedCategories,
                                    onClick = { viewModel.toggleCategory(cat) },
                                    label = { Text(getCategoryShortName(cat)) }
                                )
                            }
                        }
                    }

                    // Bottom count badge
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (uiState.selectedCategories.isEmpty())
                                stringResource(R.string.map_tochek_na_karte, uiState.expenses.size)
                            else
                                stringResource(R.string.map_otfiltrovano_iz, uiState.expenses.size, uiState.allExpenses.size),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Карточка расхода по нажатию на метку — та же, что в списке расходов.
    //
    // Валюта берётся из самой записи, а не из автомобиля: экран карты машину не
    // загружает, а в записи валюта теперь проставляется по автомобилю при
    // сохранении, так что значение верное.
    selectedExpense?.let { expense ->
        com.aggin.carcost.presentation.screens.car_detail.ExpenseDetailSheet(
            expense = expense,
            tags = emptyList(),
            currency = expense.currency,
            fuelConsumptionL100km = null,
            onEdit = {
                val target = expense
                selectedExpense = null
                navController.navigateOnce(
                    com.aggin.carcost.presentation.navigation.Screen.EditExpense
                        .createRoute(target.carId, target.id)
                )
            },
            onDismiss = { selectedExpense = null }
        )
    }

}

@Composable
fun YandexMapView(
    expenses: List<Expense>,
    currentLocation: Point?,
    modifier: Modifier = Modifier,
    /** Нажатие по метке — открыть карточку этого расхода */
    onExpenseClick: (Expense) -> Unit = {}
) {
    var isInitialCameraMoveDone by rememberSaveable { mutableStateOf(false) }

    /**
     * Слушатели нажатий держим сами.
     *
     * MapKit хранит на них только слабые ссылки: созданный на месте объект
     * собирается сборщиком мусора, и метки через некоторое время молча
     * перестают откликаться. Ровно та же оговорка, что и в навигаторе.
     */
    val tapListeners = remember { mutableListOf<com.yandex.mapkit.map.MapObjectTapListener>() }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                val startPoint = Point(55.751244, 37.618423)
                map.move(
                    CameraPosition(startPoint, 5.0f, 0.0f, 0.0f)
                )

                // Только MapView. MapKitFactory.onStart() уже вызван в
                // DisposableEffect выше, и парный onStop там же — второй вызов
                // здесь сдвигал счётчик на +1 при каждом открытии карты, из-за
                // чего MapKit не останавливался никогда: фоновые потоки, докачка
                // тайлов и сеть работали до убийства процесса
                onStart()
            }
        },
        onRelease = { mapView ->
            // Без парного onStop карта продолжает жить после ухода с экрана
            mapView.onStop()
        },
        modifier = modifier,
        update = { mapView ->
            mapView.map.mapObjects.clear()
            // Метки пересоздаются — старые слушатели больше не на что вешать
            tapListeners.clear()

            expenses.forEach { expense ->
                if (expense.latitude != null && expense.longitude != null) {
                    val point = Point(expense.latitude, expense.longitude)
                    // Была стандартная точка с текстовой подписью — на карте
                    // города такие точки теряются, а подписи наезжают друг на
                    // друга. Рисуем ту же каплю, что и в навигаторе: цвет
                    // категории берётся из общего справочника Labels.
                    mapView.map.mapObjects.addPlacemark(point).apply {
                        setIcon(
                            com.yandex.runtime.image.ImageProvider.fromBitmap(
                                com.aggin.carcost.presentation.screens.navigator.MapMarkers.customBitmap(
                                    color = expense.category.color().toArgb(),
                                    emoji = expense.category.emoji()
                                )
                            ),
                            com.yandex.mapkit.map.IconStyle().setZIndex(10f)
                        )
                        val listener = com.yandex.mapkit.map.MapObjectTapListener { _, _ ->
                            onExpenseClick(expense)
                            true   // событие обработано
                        }
                        tapListeners.add(listener)
                        addTapListener(listener)
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

/**
 * Метка маркера на карте. Намеренно НЕ Labels.emoji(): здесь урезанный набор
 * и общий пин 📍 для всего остального — на карте важна различимость, а не полнота.
 */
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