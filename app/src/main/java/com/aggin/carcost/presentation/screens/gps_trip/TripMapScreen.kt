package com.aggin.carcost.presentation.screens.gps_trip

import android.app.Application
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class TripMapUiState(
    val trip: GpsTrip? = null,
    val routePoints: List<Point> = emptyList(),
    val isLoading: Boolean = true
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class TripMapViewModel(
    application: Application,
    private val tripId: String
) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).gpsTripDao()
    private val _uiState = MutableStateFlow(TripMapUiState())
    val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = dao.getById(tripId)
            val points = parseRouteJson(trip?.routeJson)
            _uiState.value = TripMapUiState(
                trip = trip,
                routePoints = points,
                isLoading = false
            )
        }
    }

    private fun parseRouteJson(json: String?): List<Point> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    Point(obj.getDouble("lat"), obj.getDouble("lng"))
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class TripMapViewModelFactory(
    private val app: Application,
    private val tripId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TripMapViewModel(app, tripId) as T
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(tripId: String, navController: NavController) {
    val context = LocalContext.current
    val viewModel: TripMapViewModel = viewModel(
        factory = TripMapViewModelFactory(context.applicationContext as Application, tripId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault()) }

    DisposableEffect(Unit) {
        MapKitFactory.getInstance().onStart()
        onDispose { MapKitFactory.getInstance().onStop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Маршрут поездки") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                uiState.trip == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Поездка не найдена", style = MaterialTheme.typography.titleMedium)
                    }
                }

                uiState.routePoints.size < 2 -> {
                    // No GPS points — show stats-only card
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Map, null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Маршрут не записан",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "GPS-точки отсутствуют для этой поездки",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        uiState.trip?.let { TripStatsCard(it) }
                    }
                }

                else -> {
                    val trip = uiState.trip!!
                    val points = uiState.routePoints

                    // --- Full-screen map ---
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).also { mapView ->
                                val map = mapView.mapWindow.map

                                // Polyline for the route
                                val polyline = map.mapObjects.addPolyline(Polyline(points))
                                polyline.setStrokeColor(AndroidColor.argb(220, 33, 150, 243)) // blue
                                polyline.strokeWidth = 6f
                                polyline.setOutlineColor(AndroidColor.WHITE)
                                polyline.outlineWidth = 2f

                                // Start placemark (A)
                                map.mapObjects.addPlacemark(points.first())
                                // End placemark (B)
                                map.mapObjects.addPlacemark(points.last())

                                // Fit camera to bounding box
                                val lats = points.map { it.latitude }
                                val lngs = points.map { it.longitude }
                                val cLat = (lats.min() + lats.max()) / 2.0
                                val cLng = (lngs.min() + lngs.max()) / 2.0
                                val span = maxOf(
                                    lats.max() - lats.min(),
                                    lngs.max() - lngs.min()
                                ).coerceAtLeast(0.001)

                                val zoom = when {
                                    span < 0.002 -> 16f
                                    span < 0.006 -> 15f
                                    span < 0.015 -> 14f
                                    span < 0.04  -> 13f
                                    span < 0.12  -> 11f
                                    span < 0.4   -> 10f
                                    span < 1.2   -> 8f
                                    else         -> 6f
                                }

                                map.move(
                                    CameraPosition(Point(cLat, cLng), zoom, 0f, 0f),
                                    Animation(Animation.Type.LINEAR, 0.8f),
                                    null
                                )
                            }
                        }
                    )

                    // --- Stats overlay (bottom card) ---
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                dateFmt.format(Date(trip.startTime)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            TripStatsRow(trip)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable composables
// ---------------------------------------------------------------------------

@Composable
fun TripStatsCard(trip: GpsTrip) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Статистика поездки",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            TripStatsRow(trip)
        }
    }
}

@Composable
fun TripStatsRow(trip: GpsTrip) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        TripStatItem(
            icon = Icons.Default.Straighten,
            label = "Дистанция",
            value = "%.1f км".format(trip.distanceKm)
        )
        if (trip.endTime != null) {
            val durationMin = ((trip.endTime - trip.startTime) / 60_000).toInt()
            val h = durationMin / 60; val m = durationMin % 60
            TripStatItem(
                icon = Icons.Default.Timer,
                label = "Время",
                value = if (h > 0) "${h}ч ${m}м" else "${m} мин"
            )
        }
        trip.avgSpeedKmh?.let { speed ->
            TripStatItem(
                icon = Icons.Default.Speed,
                label = "Ср. скорость",
                value = "%.0f км/ч".format(speed)
            )
        }
    }
}

@Composable
private fun TripStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
