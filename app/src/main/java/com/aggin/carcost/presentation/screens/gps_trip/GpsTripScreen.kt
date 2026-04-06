package com.aggin.carcost.presentation.screens.gps_trip

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.gps.GpsTripService
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.GpsTrip
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class GpsTripUiState(
    val isRecording: Boolean = false,
    val currentDistance: Double = 0.0,
    val trips: List<GpsTrip> = emptyList(),
    val isLoading: Boolean = true
)

class GpsTripViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).gpsTripDao()

    private val _isRecording = MutableStateFlow(false)
    private val _currentDistance = MutableStateFlow(0.0)

    val uiState: StateFlow<GpsTripUiState> = combine(
        dao.getTripsByCarId(carId),
        _isRecording,
        _currentDistance
    ) { trips, recording, dist ->
        GpsTripUiState(
            trips = trips,
            isRecording = recording,
            currentDistance = dist,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpsTripUiState())

    fun startTrip(context: Context) {
        _isRecording.value = true
        _currentDistance.value = 0.0
        val intent = Intent(context, GpsTripService::class.java).apply {
            action = GpsTripService.ACTION_START
            putExtra(GpsTripService.EXTRA_CAR_ID, carId)
        }
        context.startForegroundService(intent)
    }

    fun stopTrip(context: Context) {
        _isRecording.value = false
        val intent = Intent(context, GpsTripService::class.java).apply {
            action = GpsTripService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateDistance(km: Double) {
        _currentDistance.value = km
    }

    fun deleteTrip(trip: GpsTrip) {
        viewModelScope.launch { dao.delete(trip) }
    }
}

class GpsTripViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GpsTripViewModel(app, carId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsTripScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: GpsTripViewModel = viewModel(
        factory = GpsTripViewModelFactory(context.applicationContext as Application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    // Location permission
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Listen for distance broadcasts from GpsTripService
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val km = intent?.getDoubleExtra(GpsTripService.EXTRA_DISTANCE, 0.0) ?: 0.0
                viewModel.updateDistance(km)
            }
        }
        val filter = IntentFilter(GpsTripService.BROADCAST_DISTANCE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Поездки") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TripControlCard(
                    isRecording = uiState.isRecording,
                    currentDistance = uiState.currentDistance,
                    onStart = {
                        if (hasLocationPermission) {
                            viewModel.startTrip(context)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onStop = { viewModel.stopTrip(context) }
                )
            }
            if (!hasLocationPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOff, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Нет доступа к геолокации",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Нажмите Старт, чтобы разрешить доступ",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.trips.isNotEmpty()) {
                item {
                    Text(
                        "История поездок",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.trips, key = { it.id }) { trip ->
                    TripCard(trip, dateFmt, onDelete = { viewModel.deleteTrip(trip) })
                }
            }
        }
    }
}

@Composable
private fun TripControlCard(
    isRecording: Boolean,
    currentDistance: Double,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300), label = "buttonColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .background(Color(0xFFEF5350).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.RadioButtonChecked, null,
                        Modifier.size(40.dp), tint = Color(0xFFEF5350))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "%.2f км".format(currentDistance),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("Запись идёт...", color = Color(0xFFEF5350), fontSize = 14.sp)
            } else {
                Icon(Icons.Default.Map, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Запись пробега по GPS",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Нажмите Старт для начала поездки",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = if (isRecording) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null, Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRecording) "Остановить" else "Старт",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: GpsTrip,
    dateFmt: SimpleDateFormat,
    onDelete: () -> Unit
) {
    val durationMin = if (trip.endTime != null)
        ((trip.endTime - trip.startTime) / 60_000).toInt() else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Map, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    "%.1f км".format(trip.distanceKm),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Text(
                    dateFmt.format(Date(trip.startTime)),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (durationMin != null) {
                    Text(
                        buildString {
                            val h = durationMin / 60
                            val m = durationMin % 60
                            if (h > 0) append("${h}ч ")
                            append("${m}мин")
                            trip.avgSpeedKmh?.let { append(" • %.0f км/ч".format(it)) }
                        },
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}
