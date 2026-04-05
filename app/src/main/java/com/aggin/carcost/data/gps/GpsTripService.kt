package com.aggin.carcost.data.gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aggin.carcost.MainActivity
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.GpsTrip
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

        // Broadcast to update UI
        const val BROADCAST_DISTANCE = "com.aggin.carcost.GPS_DISTANCE_UPDATE"
        const val EXTRA_DISTANCE = "extra_distance_km"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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

        startForeground(NOTIFICATION_ID, buildNotification("Поездка: 0.0 км"))
        requestLocationUpdates()
    }

    private fun stopTrip() {
        val tripId = currentTripId ?: return
        val carIdVal = carId ?: return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)

        val distanceKm = totalDistanceMeters / 1000.0
        val endTime = System.currentTimeMillis()
        val durationHours = (endTime - startTime) / 3_600_000.0
        val avgSpeed = if (durationHours > 0) distanceKm / durationHours else null

        val routeJson = buildRouteJson(routePoints)

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

            // Update odometer if distance meaningful
            if (distanceKm >= 0.1) {
                val car = db.carDao().getCarById(carIdVal)
                if (car != null) {
                    val newOdometer = car.currentOdometer + distanceKm.roundToInt()
                    db.carDao().updateOdometer(carIdVal, newOdometer)
                }
            }
        }

        currentTripId = null
        stopSelf()
    }

    private fun onNewLocation(location: Location) {
        routePoints += Pair(location.latitude, location.longitude)

        lastLocation?.let { prev ->
            totalDistanceMeters += prev.distanceTo(location)
        }
        lastLocation = location

        val distanceKm = totalDistanceMeters / 1000.0

        // Update notification
        val notification = buildNotification("Поездка: %.1f км".format(distanceKm))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        // Broadcast to UI
        sendBroadcast(Intent(BROADCAST_DISTANCE).apply {
            putExtra(EXTRA_DISTANCE, distanceKm)
        })
    }

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5_000L  // 5 seconds
        ).setMinUpdateDistanceMeters(10f).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Поездки",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Отслеживание пробега по GPS" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CarCost")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildRouteJson(points: List<Pair<Double, Double>>): String? {
        if (points.isEmpty()) return null
        // Sample every 5th point to keep size manageable
        val sampled = points.filterIndexed { i, _ -> i % 5 == 0 }
        return "[" + sampled.joinToString(",") { (lat, lng) -> """{"lat":$lat,"lng":$lng}""" } + "]"
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
