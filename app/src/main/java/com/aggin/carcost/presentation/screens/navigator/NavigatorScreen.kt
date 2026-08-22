package com.aggin.carcost.presentation.screens.navigator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.FavoritePlaceType
import com.aggin.carcost.presentation.navigation.Screen
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView
import kotlin.math.roundToInt
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Убрать объект с карты, не рискуя вылетом.
 *
 * Объекты карты живут в нативной части MapKit, а в Kotlin лежит лишь слабая
 * ссылка. Она умирает вместе с самим объектом: после удаления, при пересоздании
 * карты, при повороте экрана. Обращение к такой ссылке — не исключение, которое
 * можно поймать по месту, а падение всего приложения с текстом
 * «Native object's weak_ptr has expired».
 *
 * Ровно это и происходило в навигации: полилинии пройденного и оставшегося пути
 * пересчитываются на каждой точке GPS, но переприсваиваются лишь по условию, и
 * в начале маршрута в переменной оставался уже удалённый объект.
 *
 * Проверка [MapObject.isValid] — то, о чём просит сам MapKit в тексте ошибки.
 * try/catch рядом — на случай, если объект умрёт между проверкой и удалением.
 */
/**
 * Наклон камеры в движении.
 *
 * Было 20 градусов — почти вид сверху, из-за чего дорога впереди сжималась в
 * полоску и понять, что за поворотом, было нельзя. Пятьдесят дают перспективу,
 * при которой видно продолжение пути; на этом наклоне и высоком зуме MapKit
 * начинает рисовать объёмную застройку, и карта перестаёт быть плоской схемой.
 *
 * Выше шестидесяти горизонт занимает половину экрана, а полезной карты
 * остаётся мало — проверено подбором.
 */
private const val NAVIGATION_TILT = 50f

/** Одна величина в итогах поездки: число крупно, подпись мелко */
@Composable
private fun ArrivalStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun MapObjectCollection?.removeSafely(obj: MapObject?) {
    val collection = this ?: return
    if (obj == null || !obj.isValid || !collection.isValid) return
    try {
        collection.remove(obj)
    } catch (e: RuntimeException) {
        android.util.Log.w("Navigator", "Объект карты уже уничтожен: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatorScreen(
    navController: NavController,
    viewModel: NavigatorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // MapKit поднимается лениво: он больше не инициализируется при каждом
    // запуске приложения, а только когда впервые понадобилась карта.
    // getInstance() без initialize() бросает исключение, поэтому вызов
    // обязан быть ДО него.
    com.aggin.carcost.App.ensureMapKit(androidx.compose.ui.platform.LocalContext.current)

    DisposableEffect(Unit) {
        MapKitFactory.getInstance().onStart()
        viewModel.retryLocationTracking()
        onDispose {
            MapKitFactory.getInstance().onStop()
            // Гасился только MapKit, а GPS оставался включённым
            viewModel.stopLocationTracking()
        }
    }

    // Init TTS speaker
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initSpeaker(context)
        // Unit как ключ: считаем открытие экрана, а не каждую перерисовку
        com.aggin.carcost.data.analytics.Analytics.navigatorOpened()
    }

    // Map refs — must be declared before LaunchedEffects that reference them
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    var routePolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    var traveledPolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    var remainingPolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    val altRoutePolylines = remember { mutableListOf<com.yandex.mapkit.map.PolylineMapObject>() }
    var destMarker by remember { mutableStateOf<com.yandex.mapkit.map.PlacemarkMapObject?>(null) }
    val poiMarkers = remember { mutableListOf<com.yandex.mapkit.map.PlacemarkMapObject>() }

    /**
     * Убрать с карты всё, что относится к маршруту.
     *
     * Раньше очистка была расписана по месту в двух эффектах, и оба забывали про
     * линии пройденного и оставшегося пути: их рисует отдельный эффект во время
     * движения, а убирать было некому. После отмены маршрут исчезал наполовину —
     * основная линия пропадала, а подсветка пути оставалась на карте навсегда.
     *
     * Одна функция вместо двух списков: забыть здесь новый объект можно только
     * вместе с его созданием.
     */
    fun clearRouteOverlays() {
        mapObjects.removeSafely(routePolyline)
        routePolyline = null
        mapObjects.removeSafely(traveledPolyline)
        traveledPolyline = null
        mapObjects.removeSafely(remainingPolyline)
        remainingPolyline = null
        altRoutePolylines.forEach { mapObjects.removeSafely(it) }
        altRoutePolylines.clear()
    }

    /** Место, по которому нажали: показываем карточку снизу */
    var selectedPoi by remember { mutableStateOf<PoiItem?>(null) }

    /**
     * Слушатели нажатий по меткам приходится держать самим.
     *
     * MapKit хранит на них только слабые ссылки: созданный на месте объект
     * собирается сборщиком мусора, и метки молча перестают откликаться. Список
     * живёт столько же, сколько экран, и этого достаточно.
     */
    val poiTapListeners = remember { mutableListOf<com.yandex.mapkit.map.MapObjectTapListener>() }

    // Save-favorites dialog
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var saveType by remember { mutableStateOf(FavoritePlaceType.OTHER) }

    // First GPS fix → pan to user (IDLE only, once)
    var didInitialMove by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.currentLat, uiState.currentLon) {
        val lat = uiState.currentLat
        val lon = uiState.currentLon
        if (!didInitialMove && lat != null && lon != null && uiState.mode == NavigatorMode.IDLE) {
            mapView?.mapWindow?.map?.move(
                CameraPosition(Point(lat, lon), 14f, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 1f), null
            )
            didInitialMove = true
        }
    }

    // Draw all route polylines (alternatives + primary)
    LaunchedEffect(uiState.allRoutes, uiState.selectedRouteIndex) {
        val routes = uiState.allRoutes
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }

        // Сначала убираем прежнее — и когда рисуем заново, и когда рисовать
        // больше нечего. Раньше при опустевшем списке эффект выходил сразу,
        // и старые линии оставались на карте.
        clearRouteOverlays()
        if (routes.isEmpty()) return@LaunchedEffect

        // Draw alternative routes first (behind primary)
        routes.forEachIndexed { idx, altRoute ->
            if (idx != uiState.selectedRouteIndex) {
                altRoutePolylines.add(
                    objects.addPolyline(altRoute.geometry).apply {
                        setStrokeColor(android.graphics.Color.argb(160, 120, 120, 120))
                        strokeWidth = 5f
                        setOutlineColor(android.graphics.Color.argb(60, 0, 0, 0))
                        outlineWidth = 1f
                    }
                )
            }
        }

        // Draw primary route on top in blue
        val primary = routes.getOrNull(uiState.selectedRouteIndex) ?: routes[0]
        routePolyline = objects.addPolyline(primary.geometry).apply {
            setStrokeColor(android.graphics.Color.argb(230, 26, 115, 232))  // Google Maps blue
            strokeWidth = 8f
            setOutlineColor(android.graphics.Color.argb(80, 255, 255, 255))
            outlineWidth = 3f
        }

        // Fit camera to primary route bounding box
        val pts = primary.geometry.points
        if (pts.isNotEmpty()) {
            val minLat = pts.minOf { it.latitude }
            val maxLat = pts.maxOf { it.latitude }
            val minLon = pts.minOf { it.longitude }
            val maxLon = pts.maxOf { it.longitude }
            val center = Point((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
            val span = maxOf(maxLat - minLat, maxLon - minLon)
            val zoom = when {
                span < 0.02 -> 14f
                span < 0.1  -> 12f
                span < 0.5  -> 10f
                span < 2.0  -> 8f
                else        -> 6f
            }
            map.move(CameraPosition(center, zoom, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 0.8f), null)
        }
    }

    // Destination pin
    LaunchedEffect(uiState.destinationPoint) {
        val point = uiState.destinationPoint ?: run {
            mapObjects.removeSafely(destMarker)
            destMarker = null
            clearRouteOverlays()
            return@LaunchedEffect
        }
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }
        objects.removeSafely(destMarker)
        destMarker = objects.addPlacemark(point).apply {
            setIcon(
                com.yandex.runtime.image.ImageProvider.fromBitmap(MapMarkers.destinationBitmap()),
                com.yandex.mapkit.map.IconStyle().setZIndex(20f)
            )
        }
    }

    // POI markers
    LaunchedEffect(uiState.poiItems) {
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }
        poiMarkers.forEach { objects.removeSafely(it) }
        poiMarkers.clear()
        poiTapListeners.clear()
        uiState.poiItems.forEach { poi ->
            val placemark = objects.addPlacemark(poi.point).apply {
                setIcon(
                    com.yandex.runtime.image.ImageProvider.fromBitmap(
                        MapMarkers.poiBitmap(poi.category)
                    ),
                    com.yandex.mapkit.map.IconStyle().setZIndex(10f)
                )
            }
            val listener = com.yandex.mapkit.map.MapObjectTapListener { _, _ ->
                selectedPoi = poi
                true   // событие обработано, карта не должна двигаться дальше
            }
            poiTapListeners.add(listener)
            placemark.addTapListener(listener)
            poiMarkers.add(placemark)
        }
    }

    // Camera follows user during navigation (only when locked)
    LaunchedEffect(uiState.currentLat, uiState.currentLon, uiState.currentBearing) {
        if (uiState.mode != NavigatorMode.NAVIGATING || !uiState.isCameraLocked) return@LaunchedEffect
        val lat = uiState.currentLat ?: return@LaunchedEffect
        val lon = uiState.currentLon ?: return@LaunchedEffect
        mapView?.mapWindow?.map?.move(
            CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, NAVIGATION_TILT),
            Animation(Animation.Type.SMOOTH, 0.4f), null
        )
    }

    // Snap camera when navigation starts
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.NAVIGATING) {
            val lat = uiState.currentLat ?: return@LaunchedEffect
            val lon = uiState.currentLon ?: return@LaunchedEffect
            mapView?.mapWindow?.map?.move(
                CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, NAVIGATION_TILT),
                Animation(Animation.Type.SMOOTH, 1f), null
            )
        }
    }

    // Save-to-favorites dialog
    // Карточка места.
    //
    // Раньше нажатие по метке не делало ничего: увидеть название, адрес или
    // проложить туда маршрут было нельзя — только разглядывать точку. Показываем
    // снизу, чтобы карта оставалась видна и человек понимал, о каком месте речь.
    selectedPoi?.let { poi ->
        ModalBottomSheet(onDismissRequest = { selectedPoi = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (poi.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = poi.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(poi.category.label) },
                    enabled = false
                )

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.setDestination(poi.point, poi.name)
                            selectedPoi = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Маршрут")
                    }
                    OutlinedButton(
                        onClick = {
                            // Место сохраняется в избранное тем же путём, что и
                            // выбранная на карте точка — отдельного хранилища для
                            // мест из поиска заводить незачем
                            viewModel.setDestination(poi.point, poi.name)
                            selectedPoi = null
                            saveName = poi.name
                            showSaveDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.StarBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("В избранное")
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить место") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Тип:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            FavoritePlaceType.HOME  to "🏠 Дом",
                            FavoritePlaceType.WORK  to "💼 Работа",
                            FavoritePlaceType.OTHER to "⭐ Другое"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = saveType == type,
                                onClick = { saveType = type },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dest = uiState.destinationPoint
                        if (dest != null && saveName.isNotBlank()) {
                            viewModel.saveFavoritePlace(
                                name = saveName,
                                lat = dest.latitude,
                                lon = dest.longitude,
                                type = saveType,
                                address = uiState.destinationName
                            )
                        }
                        showSaveDialog = false
                    },
                    enabled = saveName.isNotBlank()
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Arrival dialog
    var showArrivalDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.ARRIVED) showArrivalDialog = true
    }

    if (showArrivalDialog) {
        // Итог поездки, а не голый вопрос «что делаем дальше».
        //
        // Раньше здесь стоял обычный диалог с тремя ссылками. Он ничего не
        // сообщал: человек только что доехал, и первое, что ему интересно —
        // сколько это заняло и во что обошлось. Заодно это единственное место,
        // где навигатор сам связывается с учётом расходов.
        Dialog(onDismissRequest = { showArrivalDialog = false; viewModel.clearDestination() }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Вы на месте",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.destinationName.isNotBlank()) {
                        Text(
                            text = uiState.destinationName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ArrivalStat(
                            value = uiState.routeDistanceKm?.let { "%.1f".format(it) } ?: "—",
                            label = "км пути",
                            modifier = Modifier.weight(1f)
                        )
                        ArrivalStat(
                            value = uiState.routeTimeMin?.let { formatDuration(it) } ?: "—",
                            label = "в дороге",
                            modifier = Modifier.weight(1f)
                        )
                        ArrivalStat(
                            value = uiState.fuelCostEstimate?.let { "%.0f".format(it) } ?: "—",
                            label = "на топливо",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showArrivalDialog = false
                            val carId = uiState.selectedCarId
                            if (carId.isNotBlank()) {
                                navController.navigateOnce(Screen.AddExpense.createRoute(carId))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Записать расход")
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showArrivalDialog = false
                            navController.navigateOnce(Screen.ParkingTimer.route)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Таймер парковки")
                    }

                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        showArrivalDialog = false
                        viewModel.clearDestination()
                    }) { Text("Закрыть") }
                }
            }
        }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissError()
    }

    // ── Full-screen layout (no Scaffold top bar) ─────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // ── MAP ──────────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mapView = mv
                    mv.onStart()

                    val map = mv.mapWindow.map
                    mapObjects = map.mapObjects

                    // User location dot
                    try {
                        val userLayer = MapKitFactory.getInstance()
                            .createUserLocationLayer(mv.mapWindow)
                        userLayer.isVisible = true
                    } catch (_: Exception) {}

                    // Initial camera position
                    val lat = uiState.currentLat ?: 56.0097
                    val lon = uiState.currentLon ?: 92.8664
                    map.move(CameraPosition(Point(lat, lon), 13f, 0f, 0f))

                    // Long-press → set destination
                    map.addInputListener(object : InputListener {
                        override fun onMapTap(p0: com.yandex.mapkit.map.Map, p1: Point) {}
                        override fun onMapLongTap(p0: com.yandex.mapkit.map.Map, point: Point) {
                            viewModel.setDestinationFromMap(point)
                        }
                    })

                    // Detect manual pan → unlock camera tracking
                    map.addCameraListener(object : CameraListener {
                        override fun onCameraPositionChanged(
                            p0: com.yandex.mapkit.map.Map,
                            p1: CameraPosition,
                            reason: CameraUpdateReason,
                            finished: Boolean
                        ) {
                            if (reason == CameraUpdateReason.GESTURES) {
                                viewModel.unlockCamera()
                            }
                        }
                    })
                }
            },
            update = { mv -> mapView = mv },
            modifier = Modifier.fillMaxSize()
        )

        // Dark theme for map
        val isDarkTheme = isSystemInDarkTheme()
        LaunchedEffect(isDarkTheme, mapView) {
            mapView?.mapWindow?.map?.isNightModeEnabled = isDarkTheme
        }

        // Traveled / remaining path split during navigation
        LaunchedEffect(uiState.currentLat, uiState.currentLon, uiState.mode) {
            if (uiState.mode != NavigatorMode.NAVIGATING) return@LaunchedEffect
            val route = uiState.allRoutes.getOrNull(uiState.selectedRouteIndex) ?: return@LaunchedEffect
            val lat = uiState.currentLat ?: return@LaunchedEffect
            val lon = uiState.currentLon ?: return@LaunchedEffect
            val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
            val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }

            val points = route.geometry.points
            if (points.size < 2) return@LaunchedEffect

            val closestIdx = points.indices.minByOrNull { i ->
                val dLat = lat - points[i].latitude
                val dLon = lon - points[i].longitude
                dLat * dLat + dLon * dLon
            } ?: 0

            // Обнуляем сразу же: ниже они присваиваются только по условию, и без
            // этого в начале и в конце маршрута в переменной остался бы объект,
            // которого на карте уже нет
            objects.removeSafely(traveledPolyline)
            traveledPolyline = null
            objects.removeSafely(remainingPolyline)
            remainingPolyline = null
            objects.removeSafely(routePolyline)
            routePolyline = null

            if (closestIdx > 0) {
                traveledPolyline = objects.addPolyline(
                    com.yandex.mapkit.geometry.Polyline(points.subList(0, closestIdx + 1))
                ).apply {
                    setStrokeColor(android.graphics.Color.argb(120, 150, 150, 150))
                    strokeWidth = 8f
                }
            }

            if (closestIdx < points.size - 1) {
                remainingPolyline = objects.addPolyline(
                    com.yandex.mapkit.geometry.Polyline(points.subList(closestIdx, points.size))
                ).apply {
                    setStrokeColor(android.graphics.Color.argb(230, 26, 115, 232))
                    strokeWidth = 8f
                    setOutlineColor(android.graphics.Color.argb(80, 255, 255, 255))
                    outlineWidth = 3f
                }
            }
        }

        // ── BACK BUTTON (non-navigation modes) ───────────────────────────────
        if (uiState.mode != NavigatorMode.NAVIGATING) {
            MapFab(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
                onClick = { navController.popBackStack() }
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад",
                    modifier = Modifier.size(20.dp))
            }
        }

        // ── SEARCH BAR (non-navigation) ───────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode != NavigatorMode.NAVIGATING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 56.dp, end = 12.dp, top = 12.dp)
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (uiState.query.isEmpty()) {
                                Text(
                                    "Куда едем?",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = uiState.query,
                                onValueChange = viewModel::onQueryChanged,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (uiState.query.isNotBlank()) {
                            IconButton(
                                onClick = { viewModel.clearDestination() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить",
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Suggestions dropdown
                AnimatedVisibility(visible = uiState.suggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(uiState.suggestions) { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onSuggestionSelected(s) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Place, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            s.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        if (s.address.isNotBlank()) {
                                            Text(
                                                s.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── NAVIGATION: top direction bar ────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.NAVIGATING,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    // Панель плавает над картой, а не приклеена к краю: под ней
                    // видно дорогу, и экран не делится пополам глухой полосой
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val maneuver = uiState.nextManeuver
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                maneuverIcon(maneuver?.action),
                                contentDescription = maneuver?.action?.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = maneuver?.distanceLabel ?: "—",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                // Табличные цифры: без них число дёргается по
                                // ширине на каждом обновлении расстояния
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFeatureSettings = "tnum"
                                )
                            )
                            Text(
                                text = maneuver?.let { m ->
                                    m.street?.let { "${m.action.label.lowercase()} на $it" }
                                        ?: m.action.label
                                } ?: uiState.destinationName.take(40),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2
                            )
                        }
                    }

                    // Следующий манёвр — отдельной строкой на подложке.
                    // Один поворот без продолжения оставляет в неведении:
                    // перестраиваться сейчас или можно подождать.
                    uiState.nextManeuver?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Straight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = "затем прямо",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // ── NAVIGATION: speed badge (bottom-left) ────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.NAVIGATING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 104.dp)
        ) {
            // Круг, а не скруглённый прямоугольник: спидометр так и выглядит,
            // и круглая форма отличает скорость от прочих панелей на экране
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.size(64.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "${uiState.currentSpeedKmh}",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFeatureSettings = "tnum"
                        )
                    )
                    Text(
                        text = "км/ч",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── RECENTER FAB (when camera unlocked during navigation) ────────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.NAVIGATING && !uiState.isCameraLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    viewModel.lockCamera()
                    val lat = uiState.currentLat ?: return@FloatingActionButton
                    val lon = uiState.currentLon ?: return@FloatingActionButton
                    mapView?.mapWindow?.map?.move(
                        CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, NAVIGATION_TILT),
                        Animation(Animation.Type.SMOOTH, 0.5f), null
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation, contentDescription = "Вернуться к позиции",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── MY LOCATION button (non-navigation) ──────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode != NavigatorMode.NAVIGATING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = when (uiState.mode) {
                        NavigatorMode.ROUTE_READY -> 240.dp
                        NavigatorMode.IDLE -> 216.dp
                        else -> 80.dp
                    }
                )
        ) {
            MapFab(onClick = {
                val lat = uiState.currentLat ?: return@MapFab
                val lon = uiState.currentLon ?: return@MapFab
                mapView?.mapWindow?.map?.move(
                    CameraPosition(Point(lat, lon), 15f, 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 0.5f), null
                )
            }) {
                Icon(Icons.Default.MyLocation, contentDescription = "Моё местоположение",
                    modifier = Modifier.size(20.dp))
            }
        }

        // ── POI FILTER CHIPS ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode in listOf(NavigatorMode.IDLE, NavigatorMode.ROUTE_READY),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = when (uiState.mode) {
                        NavigatorMode.ROUTE_READY -> 240.dp
                        else -> 168.dp
                    }
                )
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(PoiCategory.values()) { cat ->
                    FilterChip(
                        selected = uiState.activePoiCategory == cat,
                        onClick = { viewModel.searchPoi(cat) },
                        label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Text(when (cat) {
                                PoiCategory.GAS_STATION  -> "⛽"
                                PoiCategory.SERVICE      -> "🔧"
                                PoiCategory.PARKING      -> "🅿️"
                                PoiCategory.CAFE         -> "🍴"
                                PoiCategory.BANK         -> "🏦"
                                PoiCategory.SUPERMARKET  -> "🛒"
                            }, fontSize = 12.sp)
                        }
                    )
                }
            }
        }

        // ── FAVORITES CARD (IDLE) ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.IDLE,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
                    // Drag handle
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Избранные места",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hasHome = uiState.favorites.any { it.type == FavoritePlaceType.HOME }
                        val hasWork = uiState.favorites.any { it.type == FavoritePlaceType.WORK }
                        if (!hasHome) item {
                            FavoriteChip(icon = "🏠", label = "Дом", onClick = {})
                        }
                        if (!hasWork) item {
                            FavoriteChip(icon = "💼", label = "Работа", onClick = {})
                        }
                        items(uiState.favorites) { place ->
                            FavoriteChip(
                                icon = when (place.type) {
                                    FavoritePlaceType.HOME  -> "🏠"
                                    FavoritePlaceType.WORK  -> "💼"
                                    FavoritePlaceType.OTHER -> "⭐"
                                },
                                label = place.name,
                                onClick = {
                                    viewModel.setDestination(
                                        Point(place.latitude, place.longitude), place.name
                                    )
                                }
                            )
                        }
                    }

                    // Trip stats section
                    val stats = uiState.tripStats
                    if (stats != null && (stats.todayKm > 0 || stats.weekKm > 0 || stats.monthKm > 0)) {
                        HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
                        Text(
                            "Мои поездки",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TripStatChip(
                                label = "Сегодня",
                                km = stats.todayKm,
                                count = stats.todayCount
                            )
                            TripStatChip(
                                label = "7 дней",
                                km = stats.weekKm,
                                count = stats.weekCount
                            )
                            TripStatChip(
                                label = "30 дней",
                                km = stats.monthKm,
                                count = null
                            )
                        }
                    }
                }
            }
        }

        // ── ROUTE INFO CARD (ROUTE_READY) ─────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.ROUTE_READY,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            RouteInfoCard(
                destName = uiState.destinationName,
                distanceKm = uiState.routeDistanceKm,
                timeMin = uiState.routeTimeMin,
                etaString = uiState.etaString,
                fuelCost = uiState.fuelCostEstimate,
                fuelConsumptionDisplay = uiState.fuelConsumptionDisplay,
                isLoading = uiState.isLoadingRoute,
                allRoutes = uiState.allRoutes,
                selectedRouteIndex = uiState.selectedRouteIndex,
                onSelectRoute = viewModel::selectRoute,
                onSave = {
                    saveName = uiState.destinationName
                    saveType = FavoritePlaceType.OTHER
                    showSaveDialog = true
                },
                onStart = { viewModel.startNavigation() },
                onDismiss = { viewModel.clearDestination() }
            )
        }

        // ── NAVIGATION BOTTOM BAR (NAVIGATING) — Google Maps style ───────────
        AnimatedVisibility(
            visible = uiState.mode == NavigatorMode.NAVIGATING,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NavigationBottomBar(
                timeMin = uiState.routeTimeMin,
                etaString = uiState.etaString,
                distanceKm = uiState.routeDistanceKm,
                onStop = { viewModel.stopNavigation() }
            )
        }

        // Loading indicator
        if (uiState.isLoadingRoute) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 80.dp)
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        )
    }
}

// ── Google Maps style navigation bottom bar ──────────────────────────────────

@Composable
private fun NavigationBottomBar(
    timeMin: Int?,
    etaString: String,
    distanceKm: Double?,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Плавает над картой, как и панель манёвра: карта дороже
            // сплошной полосы внизу экрана
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Время в пути — главное число: им человек и меряет поездку.
            // Раньше время, прибытие и расстояние были равнозначны, и взгляду
            // не за что было зацепиться.
            Column {
                Text(
                    text = if (timeMin != null) formatDuration(timeMin) else "--",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
                Text(
                    text = "в пути",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = if (distanceKm != null) "%.1f км".format(distanceKm) else "--",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
                Text(
                    text = if (etaString.isNotBlank()) "прибытие $etaString" else "прибытие --:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            // Завершение — красный круг, а не текстовая кнопка: за рулём в него
            // проще попасть, и перепутать его ни с чем нельзя
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Завершить поездку",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h} ч" else "${h} ч ${m} мин"
    } else {
        "$minutes мин"
    }
}

// ── Route Info Card ──────────────────────────────────────────────────────────

@Composable
private fun RouteInfoCard(
    destName: String,
    distanceKm: Double?,
    timeMin: Int?,
    etaString: String,
    fuelCost: Double?,
    fuelConsumptionDisplay: String? = null,
    isLoading: Boolean,
    allRoutes: List<com.yandex.mapkit.directions.driving.DrivingRoute> = emptyList(),
    selectedRouteIndex: Int = 0,
    onSelectRoute: (Int) -> Unit = {},
    onSave: () -> Unit = {},
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Destination row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Place, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = destName.take(36) + if (destName.length > 36) "…" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Save to favorites
                IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = "Сохранить",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть",
                        modifier = Modifier.size(18.dp))
                }
            }

            // Alternative route selector (shown when multiple routes available)
            if (allRoutes.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(allRoutes.size) { idx ->
                        val route = allRoutes[idx]
                        val timeMin2 = (route.metadata.weight.timeWithTraffic.value / 60).toInt()

                        // Подписываем улицей, а не временем и длиной.
                        //
                        // Яндекс часто возвращает варианты, одинаковые по времени
                        // и километрам, но идущие разными улицами. Три чипа
                        // «4 км · 8 мин» подряд говорят правду и не дают выбрать
                        // ничего: они показывают то, что у маршрутов общее.
                        val street = distinguishingStreet(allRoutes, idx)
                        val bestTime = allRoutes.minOf {
                            (it.metadata.weight.timeWithTraffic.value / 60).toInt()
                        }
                        val diff = timeMin2 - bestTime

                        FilterChip(
                            selected = idx == selectedRouteIndex,
                            onClick = { onSelectRoute(idx) },
                            label = {
                                Text(
                                    text = when {
                                        street != null && diff > 0 ->
                                            "через $street · +${formatDuration(diff)}"
                                        street != null -> "через $street"
                                        // Улицу определить не удалось — остаётся
                                        // хотя бы разница во времени
                                        diff > 0 -> "дольше на ${formatDuration(diff)}"
                                        else -> "быстрый"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            } else {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    timeMin?.let { t ->
                        RouteStatItem(
                            value = formatDuration(t),
                            label = "время",
                            highlight = true
                        )
                    }
                    if (etaString.isNotBlank()) {
                        RouteStatItem(value = etaString, label = "прибытие")
                    }
                    distanceKm?.let { d ->
                        RouteStatItem(value = "%.1f км".format(d), label = "расстояние")
                    }
                    fuelCost?.let { c ->
                        RouteStatItem(
                            value = "~${c.roundToInt()} ₽",
                            label = fuelConsumptionDisplay ?: "топливо"
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Start button
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Поехали!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteStatItem(value: String, label: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Reusable map FAB ─────────────────────────────────────────────────────────

@Composable
private fun MapFab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

// ── Favorite chip ────────────────────────────────────────────────────────────

@Composable
private fun FavoriteChip(icon: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 16.sp)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TripStatChip(label: String, km: Double, count: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (km >= 100) "%.0f км".format(km) else "%.1f км".format(km),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (count != null) "$label · $count поезд." else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/**
 * Стрелка манёвра.
 *
 * Берём готовые системные значки поворотов: рисовать свои ради четырёх стрелок
 * незачем, а системные уже узнаваемы и одинаково читаются на любой плотности.
 */
private fun maneuverIcon(action: ManeuverAction?): androidx.compose.ui.graphics.vector.ImageVector = when (action) {
    ManeuverAction.LEFT, ManeuverAction.HARD_LEFT -> Icons.Default.TurnLeft
    ManeuverAction.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    ManeuverAction.RIGHT, ManeuverAction.HARD_RIGHT -> Icons.Default.TurnRight
    ManeuverAction.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    ManeuverAction.U_TURN -> Icons.Default.UTurnLeft
    ManeuverAction.FINISH -> Icons.Default.Flag
    else -> Icons.Default.Straight
}
