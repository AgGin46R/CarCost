package com.aggin.carcost.data.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
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

/**
 * Foreground service that keeps navigation alive when the app is in the background.
 * It tracks the user's location via FusedLocationProvider and sends broadcasts with
 * current speed and position updates.
 */
class NavigationService : LifecycleService() {

    companion object {
        const val ACTION_START = "action_start_navigation"
        const val ACTION_STOP = "action_stop_navigation"
        const val EXTRA_CAR_ID = "extra_car_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LON = "extra_dest_lon"
        const val EXTRA_DEST_NAME = "extra_dest_name"

        const val CHANNEL_ID = "navigation_channel"
        const val NOTIFICATION_ID = 3001

        const val BROADCAST_LOCATION = "com.aggin.carcost.NAVIGATION_LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"

        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val MAX_ACCURACY_METERS = 30f
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onNewLocation(it) }
        }
    }

    private var carId: String? = null
    private var destName: String = "Пункт назначения"
    private var startTime: Long = 0L
    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private val routePoints = mutableListOf<Pair<Double, Double>>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                carId = intent.getStringExtra(EXTRA_CAR_ID)
                destName = intent.getStringExtra(EXTRA_DEST_NAME) ?: "Пункт назначения"
                startTime = System.currentTimeMillis()
                startForeground(NOTIFICATION_ID, buildNotification("Навигация запущена", destName))
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopNavigation()
            }
        }
        return START_NOT_STICKY
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(LOCATION_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(5f)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun onNewLocation(location: Location) {
        if (location.accuracy > MAX_ACCURACY_METERS) return

        lastLocation?.let { prev ->
            totalDistanceMeters += prev.distanceTo(location)
        }
        lastLocation = location
        routePoints.add(Pair(location.latitude, location.longitude))

        // Broadcast to UI
        val speedKmh = (location.speed * 3.6f).toInt()
        sendBroadcast(Intent(BROADCAST_LOCATION).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_SPEED_KMH, speedKmh)
            setPackage(packageName)
        })

        // Update notification with speed
        updateNotification("Скорость: $speedKmh км/ч", destName)
    }

    private fun stopNavigation() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        saveRouteAsGpsTrip()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveRouteAsGpsTrip() {
        val cid = carId ?: return
        if (routePoints.size < 2) return
        val distanceKm = totalDistanceMeters / 1000.0
        val routeJson = "[" + routePoints.joinToString(",") { """{"lat":${it.first},"lng":${it.second}}""" } + "]"
        val durationMs = System.currentTimeMillis() - startTime
        val durationHours = durationMs / 3_600_000.0
        val avgSpeed = if (durationHours > 0) distanceKm / durationHours else null
        val trip = GpsTrip(
            id = UUID.randomUUID().toString(),
            carId = cid,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            distanceKm = distanceKm,
            routeJson = routeJson,
            avgSpeedKmh = avgSpeed
        )
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext).gpsTripDao().insert(trip)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Навигация",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления о текущей навигации"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.aggin.carcost.data.notifications.NotificationHelper.EXTRA_NAV_TYPE,
                com.aggin.carcost.data.notifications.NotificationHelper.NAV_TYPE_NAVIGATOR)
            // NAV_TYPE_NAVIGATOR doesn't need a carId, but the field must be non-null
            putExtra(com.aggin.carcost.data.notifications.NotificationHelper.EXTRA_NAV_CAR_ID,
                carId ?: "")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, subtitle: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, subtitle))
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
