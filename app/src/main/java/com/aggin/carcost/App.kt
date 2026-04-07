package com.aggin.carcost

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aggin.carcost.data.notifications.AiInsightsRefreshWorker
import com.aggin.carcost.data.notifications.BackgroundSyncWorker
import com.aggin.carcost.data.notifications.MaintenanceNotificationWorker
import com.aggin.carcost.data.notifications.FuelReminderWorker
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.remote.fcm.FcmTokenManager
import com.aggin.carcost.data.sync.RealtimeSyncManager
import com.yandex.mapkit.MapKitFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class App : Application() {

    companion object {
        lateinit var supabase: SupabaseClient
            private set

        lateinit var realtimeSync: RealtimeSyncManager
            private set

        private const val TAG = "CarCostApp"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // ✅ Инициализируем Yandex MapKit
            MapKitFactory.setApiKey("9f9cb0c7-777a-4085-b75f-20758abb5abf")
            MapKitFactory.initialize(this)
            Log.d(TAG, "Yandex MapKit initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MapKit", e)
        }

        try {
            initializeSupabase()
            Log.d(TAG, "Supabase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase", e)
        }

        NotificationHelper.createChannel(this)
        scheduleMaintenanceCheck()
        scheduleFuelReminder()
        scheduleAiInsightsRefresh()
        BackgroundSyncWorker.schedule(this)

        // Start real-time sync after Supabase is ready
        realtimeSync = RealtimeSyncManager(this)
        realtimeSync.start()

        // Регистрируем FCM токен в Supabase (нужен для push когда приложение закрыто)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            FcmTokenManager.registerCurrentToken()
        }
    }

    private fun scheduleFuelReminder() {
        val workRequest = PeriodicWorkRequestBuilder<FuelReminderWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FuelReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleAiInsightsRefresh() {
        val workRequest = PeriodicWorkRequestBuilder<AiInsightsRefreshWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AiInsightsRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleMaintenanceCheck() {
        val workRequest = PeriodicWorkRequestBuilder<MaintenanceNotificationWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MaintenanceNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun initializeSupabase() {
        supabase = createSupabaseClient(
            supabaseUrl = "https://mkwwidzaovxosnhsjomy.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1rd3dpZHphb3Z4b3NuaHNqb215Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM2NDgzNTEsImV4cCI6MjA3OTIyNDM1MX0.jycoe9IJe2xUv7QXP8aafubFBzebK6tsjKr0Ca4gh_M"
        ) {
            install(Auth) {
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }

            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}

// Глобальный доступ к Supabase
val supabase: SupabaseClient
    get() = App.supabase