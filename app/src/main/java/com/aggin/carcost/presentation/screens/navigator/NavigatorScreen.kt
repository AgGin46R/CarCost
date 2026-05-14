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
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatorScreen(
    navController: NavController,
    viewModel: NavigatorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // MapKit lifecycle
    DisposableEffect(Unit) {
        MapKitFactory.getInstance().onStart()
        viewModel.retryLocationTracking()
        onDispose { MapKitFactory.getInstance().onStop() }
    }

    // Init TTS speaker
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.initSpeaker(context) }

    // Map refs — must be declared before LaunchedEffects that reference them
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    var routePolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    var traveledPolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    var remainingPolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    val altRoutePolylines = remember { mutableListOf<com.yandex.mapkit.map.PolylineMapObject>() }
    var destMarker by remember { mutableStateOf<com.yandex.mapkit.map.PlacemarkMapObject?>(null) }
    val poiMarkers = remember { mutableListOf<com.yandex.mapkit.map.PlacemarkMapObject>() }

    // Save-favorites dialog
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveType by remember { mutableStateOf(FavoritePlaceType.OTHER) }

    // First GPS fix → pan to user (IDLE only, once)
    var didInitialMove by remember { mutableStateOf(false) }
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
        if (routes.isEmpty()) return@LaunchedEffect
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }

        // Remove old polylines
        routePolyline?.let { objects.remove(it) }
        routePolyline = null
        altRoutePolylines.forEach { objects.remove(it) }
        altRoutePolylines.clear()

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
            destMarker?.let { mapObjects?.remove(it) }
            destMarker = null
            // Also clear route polylines
            routePolyline?.let { mapObjects?.remove(it) }
            routePolyline = null
            altRoutePolylines.forEach { mapObjects?.remove(it) }
            altRoutePolylines.clear()
            return@LaunchedEffect
        }
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }
        destMarker?.let { objects.remove(it) }
        destMarker = objects.addPlacemark(point)
    }

    // POI markers
    LaunchedEffect(uiState.poiItems) {
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }
        poiMarkers.forEach { objects.remove(it) }
        poiMarkers.clear()
        uiState.poiItems.forEach { poi ->
            poiMarkers.add(objects.addPlacemark(poi.point))
        }
    }

    // Camera follows user during navigation (only when locked)
    LaunchedEffect(uiState.currentLat, uiState.currentLon, uiState.currentBearing) {
        if (uiState.mode != NavigatorMode.NAVIGATING || !uiState.isCameraLocked) return@LaunchedEffect
        val lat = uiState.currentLat ?: return@LaunchedEffect
        val lon = uiState.currentLon ?: return@LaunchedEffect
        mapView?.mapWindow?.map?.move(
            CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, 20f),
            Animation(Animation.Type.SMOOTH, 0.4f), null
        )
    }

    // Snap camera when navigation starts
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.NAVIGATING) {
            val lat = uiState.currentLat ?: return@LaunchedEffect
            val lon = uiState.currentLon ?: return@LaunchedEffect
            mapView?.mapWindow?.map?.move(
                CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, 20f),
                Animation(Animation.Type.SMOOTH, 1f), null
            )
        }
    }

    // Save-to-favorites dialog
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
    var showArrivalDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.ARRIVED) showArrivalDialog = true
    }

    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false; viewModel.clearDestination() },
            title = { Text("Вы прибыли! 🎉") },
            text = { Text("Что делаем дальше?") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        showArrivalDialog = false
                        val carId = uiState.selectedCarId.ifBlank { return@TextButton }
                        navController.navigate(Screen.AddExpense.createRoute(carId))
                    }) { Text("Записать расход") }
                    TextButton(onClick = {
                        showArrivalDialog = false
                        navController.navigate(Screen.ParkingTimer.route)
                    }) { Text("Таймер парковки") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showArrivalDialog = false
                    viewModel.clearDestination()
                }) { Text("Закрыть") }
            }
        )
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

            traveledPolyline?.let { objects.remove(it) }
            remainingPolyline?.let { objects.remove(it) }
            routePolyline?.let { objects.remove(it) }
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
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Direction icon (simplified — straight ahead)
                    Icon(
                        Icons.Default.Navigation, contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Следуйте по маршруту",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.destinationName.take(40),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
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
                .padding(start = 16.dp, bottom = 96.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${uiState.currentSpeedKmh}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                        CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, 20f),
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Time remaining
            Column {
                Text(
                    text = if (timeMin != null) formatDuration(timeMin) else "--",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "время",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ETA clock
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = etaString.ifBlank { "--:--" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "прибытие",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Distance remaining
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (distanceKm != null) "%.1f".format(distanceKm) else "--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "км",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stop button
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Завершить", style = MaterialTheme.typography.labelLarge)
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
                        val distKm = route.metadata.weight.distance.value / 1000.0
                        val timeMin2 = (route.metadata.weight.timeWithTraffic.value / 60).toInt()
                        FilterChip(
                            selected = idx == selectedRouteIndex,
                            onClick = { onSelectRoute(idx) },
                            label = {
                                Text(
                                    "%.0f км · %s".format(distKm, formatDuration(timeMin2)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Text(
                                    when (idx) { 0 -> "🚀"; 1 -> "⬡"; else -> "↩" },
                                    fontSize = 10.sp
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
