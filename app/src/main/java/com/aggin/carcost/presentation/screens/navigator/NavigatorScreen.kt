package com.aggin.carcost.presentation.screens.navigator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yandex.mapkit.map.CameraPosition
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
        onDispose { MapKitFactory.getInstance().onStop() }
    }

    // First GPS fix → pan map to user automatically (only when not yet navigating)
    var didInitialCameraMove by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.currentLat, uiState.currentLon) {
        if (!didInitialCameraMove && uiState.currentLat != null && uiState.currentLon != null
            && uiState.mode == NavigatorMode.IDLE) {
            mapView?.mapWindow?.map?.move(
                CameraPosition(Point(uiState.currentLat, uiState.currentLon), 14f, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 1f),
                null
            )
            didInitialCameraMove = true
        }
    }

    // Map objects reference for route drawing
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    var routePolyline by remember { mutableStateOf<com.yandex.mapkit.map.PolylineMapObject?>(null) }
    var destMarker by remember { mutableStateOf<com.yandex.mapkit.map.PlacemarkMapObject?>(null) }
    val poiMarkers = remember { mutableListOf<com.yandex.mapkit.map.PlacemarkMapObject>() }

    // Draw route on map when route changes
    LaunchedEffect(uiState.currentRoute) {
        val route = uiState.currentRoute ?: return@LaunchedEffect
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }

        routePolyline?.let { objects.remove(it) }
        routePolyline = objects.addPolyline(route.geometry).apply {
            setStrokeColor(android.graphics.Color.argb(220, 21, 101, 192))
            strokeWidth = 6f
            setOutlineColor(android.graphics.Color.WHITE)
            outlineWidth = 2f
        }

        // Fit camera to route bounding box
        val bbox = route.geometry.points
        if (bbox.isNotEmpty()) {
            val minLat = bbox.minOf { it.latitude }
            val maxLat = bbox.maxOf { it.latitude }
            val minLon = bbox.minOf { it.longitude }
            val maxLon = bbox.maxOf { it.longitude }
            val center = Point((minLat + maxLat) / 2, (minLon + maxLon) / 2)
            map.move(CameraPosition(center, 11f, 0f, 0f))
        }
    }

    // Draw destination marker
    LaunchedEffect(uiState.destinationPoint) {
        val point = uiState.destinationPoint ?: return@LaunchedEffect
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }

        destMarker?.let { objects.remove(it) }
        destMarker = objects.addPlacemark(point)
    }

    // Draw POI markers
    LaunchedEffect(uiState.poiItems) {
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        val objects = mapObjects ?: map.mapObjects.also { mapObjects = it }
        poiMarkers.forEach { objects.remove(it) }
        poiMarkers.clear()
        uiState.poiItems.forEach { poi ->
            val marker = objects.addPlacemark(poi.point)
            poiMarkers.add(marker)
        }
    }

    // Camera follows user in real time during navigation.
    // Azimuth = GPS bearing so the map rotates with direction of travel.
    LaunchedEffect(uiState.currentLat, uiState.currentLon, uiState.currentBearing) {
        val lat = uiState.currentLat ?: return@LaunchedEffect
        val lon = uiState.currentLon ?: return@LaunchedEffect
        val map = mapView?.mapWindow?.map ?: return@LaunchedEffect
        if (uiState.mode == NavigatorMode.NAVIGATING) {
            map.move(
                CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, 20f),
                Animation(Animation.Type.SMOOTH, 0.4f),
                null
            )
        }
    }

    // When navigation starts — snap camera to user immediately, then continuous follow takes over
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.NAVIGATING) {
            val lat = uiState.currentLat ?: return@LaunchedEffect
            val lon = uiState.currentLon ?: return@LaunchedEffect
            mapView?.mapWindow?.map?.move(
                CameraPosition(Point(lat, lon), 17f, uiState.currentBearing, 20f),
                Animation(Animation.Type.SMOOTH, 1f),
                null
            )
        }
    }

    // Show arrival dialog
    var showArrivalDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == NavigatorMode.ARRIVED) {
            showArrivalDialog = true
        }
    }

    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Вы прибыли! 🎉") },
            text = { Text("Добавить расход за поездку?") },
            confirmButton = {
                TextButton(onClick = {
                    showArrivalDialog = false
                    val carId = uiState.selectedCarId.ifBlank { return@TextButton }
                    navController.navigate(Screen.AddExpense.createRoute(carId))
                }) { Text("Добавить расход") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showArrivalDialog = false
                    viewModel.clearDestination()
                }) { Text("Нет, спасибо") }
            }
        )
    }

    // Error snackbar
    uiState.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            if (uiState.mode != NavigatorMode.NAVIGATING) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // ── MAP ──────────────────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).also { mv ->
                        mapView = mv
                        mv.onStart()
                        MapKitFactory.getInstance().onStart()

                        val map = mv.mapWindow.map

                        // User location layer — isVisible shows the dot;
                        // setHeadingEnabled turns on the direction chevron/arrow
                        val userLocationLayer = MapKitFactory.getInstance()
                            .createUserLocationLayer(mv.mapWindow)
                        userLocationLayer.isVisible = true
                        userLocationLayer.setHeadingEnabled(true)

                        // Default camera: user's last known position, or Moscow as fallback
                        val startLat = uiState.currentLat ?: 55.7558
                        val startLon = uiState.currentLon ?: 37.6173
                        map.move(CameraPosition(Point(startLat, startLon), 13f, 0f, 0f))

                        mapObjects = map.mapObjects
                    }
                },
                update = { mv ->
                    mapView = mv
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── SEARCH BAR (IDLE / SEARCHING) ────────────────────────────────
            AnimatedVisibility(
                visible = uiState.mode != NavigatorMode.NAVIGATING,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 16.dp, end = 16.dp)
            ) {
                Column {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text("Куда едем?") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.query.isNotBlank()) {
                                IconButton(onClick = { viewModel.clearDestination() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Suggestions dropdown
                    if (uiState.suggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(uiState.suggestions) { suggestion ->
                                    ListItem(
                                        headlineContent = { Text(suggestion.name, maxLines = 1) },
                                        leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                                        modifier = Modifier.clickable { viewModel.onSuggestionSelected(suggestion) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // ── NAVIGATION MODE PANEL (top) ──────────────────────────────────
            AnimatedVisibility(
                visible = uiState.mode == NavigatorMode.NAVIGATING,
                enter = slideInVertically(),
                exit = slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                NavigatingPanel(
                    speedKmh = uiState.currentSpeedKmh,
                    routeDistanceKm = uiState.routeDistanceKm,
                    routeTimeMin = uiState.routeTimeMin,
                    destName = uiState.destinationName,
                    onStop = { viewModel.stopNavigation() }
                )
            }

            // ── POI FILTER CHIPS ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.mode in listOf(NavigatorMode.IDLE, NavigatorMode.ROUTE_READY),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (uiState.mode == NavigatorMode.ROUTE_READY) 220.dp else 160.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(PoiCategory.values()) { category ->
                        FilterChip(
                            selected = uiState.activePoiCategory == category,
                            onClick = { viewModel.searchPoi(category) },
                            label = { Text(category.label) },
                            leadingIcon = {
                                Text(when (category) {
                                    PoiCategory.GAS_STATION -> "⛽"
                                    PoiCategory.SERVICE -> "🔧"
                                    PoiCategory.PARKING -> "🅿️"
                                })
                            }
                        )
                    }
                }
            }

            // ── FAVORITES ROW (IDLE) ─────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.mode == NavigatorMode.IDLE,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Избранные места", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Quick-add Home / Work buttons if not saved
                            val hasHome = uiState.favorites.any { it.type == FavoritePlaceType.HOME }
                            val hasWork = uiState.favorites.any { it.type == FavoritePlaceType.WORK }
                            if (!hasHome) {
                                item {
                                    FavoriteChip(icon = "🏠", label = "Дом", onClick = {
                                        // Navigate to save home screen (shows a "tap on map" flow)
                                        // For now, placeholder
                                    })
                                }
                            }
                            if (!hasWork) {
                                item {
                                    FavoriteChip(icon = "💼", label = "Работа", onClick = {})
                                }
                            }
                            items(uiState.favorites) { place ->
                                FavoriteChip(
                                    icon = when (place.type) {
                                        FavoritePlaceType.HOME -> "🏠"
                                        FavoritePlaceType.WORK -> "💼"
                                        FavoritePlaceType.OTHER -> "⭐"
                                    },
                                    label = place.name,
                                    onClick = {
                                        viewModel.setDestination(
                                            Point(place.latitude, place.longitude),
                                            place.name
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── ROUTE INFO CARD (ROUTE_READY) ────────────────────────────────
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
                    fuelCost = uiState.fuelCostEstimate,
                    isLoading = uiState.isLoadingRoute,
                    onStart = { viewModel.startNavigation() },
                    onDismiss = { viewModel.clearDestination() }
                )
            }

            // Loading indicator
            if (uiState.isLoadingRoute) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }
        }
    }
}

// ── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun NavigatingPanel(
    speedKmh: Int,
    routeDistanceKm: Double?,
    routeTimeMin: Int?,
    destName: String,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$speedKmh км/ч",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = destName.take(30) + if (destName.length > 30) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    routeDistanceKm?.let { dist ->
                        Text(
                            text = "%.1f км".format(dist),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    routeTimeMin?.let { time ->
                        Text(
                            text = "~$time мин",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Завершить навигацию")
            }
        }
    }
}

@Composable
private fun RouteInfoCard(
    destName: String,
    distanceKm: Double?,
    timeMin: Int?,
    fuelCost: Double?,
    isLoading: Boolean,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = destName.take(40) + if (destName.length > 40) "…" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    distanceKm?.let { dist ->
                        RouteStatItem(icon = "📍", value = "%.1f км".format(dist), label = "расстояние")
                    }
                    timeMin?.let { time ->
                        RouteStatItem(icon = "🕐", value = "~$time мин", label = "время")
                    }
                    fuelCost?.let { cost ->
                        RouteStatItem(icon = "⛽", value = "~${cost.roundToInt()} ₽", label = "топливо")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Поехали!", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun RouteStatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FavoriteChip(icon: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
