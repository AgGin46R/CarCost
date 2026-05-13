package com.aggin.carcost.data.gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aggin.carcost.MainActivity
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.domain.gamification.AchievementChecker
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class GpsTripService : LifecycleService() {

    companion object {
        const val ACTION_START = "action_start_trip"
        const val ACTION_STOP = "action_stop_trip"
        const val EXTRA_CAR_ID = "extra_car_id"
        const val CHANNEL_ID = "gps_trip_channel"
        const val NOTIFICATION_ID = 2001

        const val BROADCAST_DISTANCE = "com.aggin.carcost.GPS_DISTANCE_UPDATE"
        const val EXTRA_DISTANCE = "extra_distance_km"

        // Filters: skip GPS fixes worse than 50m accuracy
        private const val MAX_ACCURACY_METERS = 50f
        // Min distance between two saved route points (reduces noise)
        private const val MIN_DISPLACEMENT_METERS = 10f
        // Location poll interval
        private const val LOCATION_INTERVAL_MS = 3_000L

        /** True while a trip is being recorded — survives ViewModel recreation on notification tap. */
        @Volatile var isRunning: Boolean = false
            private set

        /** Car ID of the ongoing trip, or null when idle. */
        @Volatile var activeCarId: String? = null
            private set
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationHandlerThread: HandlerThread
    private lateinit var notificationManager: NotificationManager

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location -> onNewLocation(location) }
        }
    }

    private var currentTripId: String? = null
    private var carId: String? = null
    private var startTime: Long = 0
    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private val routePoints = mutableListOf<Pair<Double, Double>>()
    // For real average speed: accumulate location.speed readings (m/s)
    private val speedSamples = mutableListOf<Float>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationHandlerThread = HandlerThread("GpsLocationThread").also { it.start() }
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                carId = intent.getStringExtra(EXTRA_CAR_ID) ?: return START_NOT_STICKY
                startTrip()
            }
            ACTION_STOP -> stopTrip()
        }
        return START_NOT_STICKY
    }

    private fun startTrip() {
        currentTripId = UUID.randomUUID().toString()
        startTime = System.currentTimeMillis()
        totalDistanceMeters = 0.0
        lastLocation = null
        routePoints.clear()
        speedSamples.clear()

        isRunning = true
        activeCarId = carId

        startForeground(NOTIFICATION_ID, buildNotification("Поездка: 0.0 км"))
        requestLocationUpdates()
    }

    private fun stopTrip() {
        val tripId = currentTripId ?: return
        val carIdVal = carId ?: return
        currentTripId = null
        isRunning = false
        activeCarId = null

        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)

        val distanceKm = totalDistanceMeters / 1000.0
        val endTime = System.currentTimeMillis()

        // Use actual GPS speed samples for avg speed (more accurate than dist/time)
        val avgSpeed: Double? = if (speedSamples.isNotEmpty()) {
            val avgMs = speedSamples.filter { it > 0f }.average()
            if (avgMs.isNaN()) null else avgMs * 3.6  // m/s → km/h
        } else {
            // Fallback: distance / time (excludes zero)
            val durationHours = (endTime - startTime) / 3_600_000.0
            if (durationHours > 0 && distanceKm > 0) distanceKm / durationHours else null
        }

        val routeJson = buildRouteJson(routePoints)

        // Launch coroutine, call stopSelf() AFTER DB write to avoid race condition
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val trip = GpsTrip(
                id = tripId,
                carId = carIdVal,
                startTime = startTime,
                endTime = endTime,
                distanceKm = distanceKm,
                routeJson = routeJson,
                avgSpeedKmh = avgSpeed
            )
            db.gpsTripDao().insert(trip)

            // Check TRIP_TRACKER achievement (best-effort)
            try {
                val userId = SupabaseAuthRepository().getUserId()
                if (userId != null) {
                    AchievementChecker(db.achievementDao(), db.expenseDao())
                        .checkAfterTripRecorded(userId)
                }
            } catch (e: Exception) {
                android.util.Log.e("GpsTripService", "Achievement check failed", e)
            }

            // Update car odometer if distance is meaningful (≥ 100m)
            if (distanceKm >= 0.1) {
                val car = db.carDao().getCarById(carIdVal)
                if (car != null) {
                    val newOdometer = car.currentOdometer + distanceKm.roundToInt()
                    db.carDao().updateOdometer(carIdVal, newOdometer)
                }
            }
            // Stop service AFTER the write completes — prevents data loss
            stopSelf()
        }
    }

    private fun onNewLocation(location: Location) {
        // Filter out inaccurate GPS fixes (e.g. indoor, just woken up)
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return

        routePoints += Pair(location.latitude, location.longitude)

        lastLocation?.let { prev ->
            val dist = prev.distanceTo(location)
            // Skip jitter: only accumulate meaningful displacement
            if (dist >= MIN_DISPLACEMENT_METERS) {
                totalDistanceMeters += dist
            }
        }
        lastLocation = location

        // Collect speed sample if device reports it (m/s)
        if (location.hasSpeed() && location.speed >= 0f) {
            speedSamples += location.speed
        }

        val distanceKm = totalDistanceMeters / 1000.0

        // Update foreground notification
        notificationManager.notify(NOTIFICATION_ID, buildNotification("Поездка: %.1f км".format(distanceKm)))

        // Broadcast live distance to GpsTripScreen UI
        sendBroadcast(Intent(BROADCAST_DISTANCE).apply {
            putExtra(EXTRA_DISTANCE, distanceKm)
        })
    }

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,   // GPS chip — best accuracy for trip recording
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        // Use background HandlerThread — keeps location callbacks off the main thread
        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            locationHandlerThread.looper
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Поездки",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Отслеживание пробега по GPS" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.aggin.carcost.data.notifications.NotificationHelper.EXTRA_NAV_TYPE,
                com.aggin.carcost.data.notifications.NotificationHelper.NAV_TYPE_GPS_TRIP)
            putExtra(com.aggin.carcost.data.notifications.NotificationHelper.EXTRA_NAV_CAR_ID,
                carId ?: "")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CarCost — GPS запись")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildRouteJson(points: List<Pair<Double, Double>>): String? {
        if (points.isEmpty()) return null
        // Adaptive sampling: for short trips keep all points, for long trips sample every 5th
        val sampled = if (points.size <= 100) points
        else points.filterIndexed { i, _ -> i % 5 == 0 || i == points.size - 1 }
        return "[" + sampled.joinToString(",") { (lat, lng) ->
            """{"lat":$lat,"lng":$lng}"""
        } + "]"
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (::locationHandlerThread.isInitialized) locationHandlerThread.quit()
    }
}
