package com.aggin.carcost

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aggin.carcost.data.notifications.BackgroundSyncWorker
import com.aggin.carcost.data.notifications.BudgetAlertWorker
import com.aggin.carcost.data.notifications.MaintenanceNotificationWorker
import com.aggin.carcost.data.notifications.FuelReminderWorker
import com.aggin.carcost.data.notifications.DocumentExpiryWorker
import com.aggin.carcost.data.notifications.InsuranceExpiryWorker
import com.aggin.carcost.data.notifications.FluidCheckWorker
import com.aggin.carcost.data.notifications.WeeklySummaryWorker
import com.aggin.carcost.data.notifications.YearOwnerCheckWorker
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.fcm.FcmTokenManager
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.rustore.RuStorePushTokenManager
import ru.rustore.sdk.pushclient.RuStorePushClient
import com.aggin.carcost.data.sync.RealtimeSyncManager
import com.aggin.carcost.data.sync.SyncRepositoryFactory
import com.vk.id.VKID
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
import com.my.tracker.MyTracker
import ru.ok.tracer.CoreTracerConfiguration
import ru.ok.tracer.HasTracerConfiguration
import ru.ok.tracer.TracerConfiguration

/**
 * Tracer подключается через интерфейс, а не вызовом в onCreate: библиотека
 * поднимается собственным ContentProvider ещё до создания приложения. Это
 * сделано намеренно — вылет на старте случится раньше любого нашего кода,
 * и перехватить его иначе нельзя.
 *
 * Поэтому же здесь нет ветки «включать только в релизе»: смысл в том, чтобы
 * видеть падения у людей, а не у себя.
 */
class App : Application(), HasTracerConfiguration {

    override val tracerConfiguration: List<TracerConfiguration>
        get() = listOf(
            CoreTracerConfiguration.build { }
        )

    companion object {
        lateinit var supabase: SupabaseClient
            private set

        var realtimeSync: RealtimeSyncManager? = null
            private set

        private const val TAG = "CarCostApp"

        /** Минимальный интервал между синхронизациями при разворачивании приложения */
        private const val FOREGROUND_SYNC_INTERVAL_MS = 60 * 60 * 1000L

        @Volatile
        private var mapKitReady = false

        /**
         * Инициализирует MapKit при первом обращении к карте.
         *
         * Раньше это делалось в onCreate на главном потоке при каждом запуске —
         * нативная библиотека поднималась даже у тех, кто карту не открывает
         * вовсе. Вызывать обязательно перед созданием MapView.
         */
        @Synchronized
        fun ensureMapKit(context: android.content.Context) {
            if (mapKitReady) return
            try {
                MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_KEY)
                MapKitFactory.initialize(context.applicationContext)
                mapKitReady = true
                Log.d(TAG, "Yandex MapKit initialized (lazily)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MapKit", e)
            }
        }
    }

    /** Область для инициализации, не нужной первому кадру */
    private val backgroundInit = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Продуктовая аналитика MyTracker.
     *
     * Без ключа в local.properties молча не включается — это нормальное
     * состояние для сборки у того, кто кабинет MyTracker не заводил.
     *
     * Что уходит наружу: факт запуска, версия приложения, модель устройства и
     * события, которые мы отправим явно. Содержимое чатов, файлы, данные машин
     * и расходы к аналитике отношения не имеют и в неё не попадают — SDK сам по
     * себе ничего из приложения не читает, он получает только то, что ему дали.
     */
    private fun initMyTracker() {
        if (BuildConfig.MYTRACKER_SDK_KEY.isBlank()) {
            Log.d(TAG, "MyTracker не настроен — ключ не задан")
            return
        }
        try {
            MyTracker.setDebugMode(BuildConfig.DEBUG)
            MyTracker.initTracker(BuildConfig.MYTRACKER_SDK_KEY, this)
            Log.d(TAG, "MyTracker инициализирован")
        } catch (e: Exception) {
            // Аналитика не тот повод, чтобы не запустить приложение
            Log.e(TAG, "Не удалось инициализировать MyTracker", e)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // MyTracker обязан подняться именно здесь, а не в фоне: он засчитывает
        // сам факт запуска, и запоздалая инициализация теряет часть сеансов.
        // Вызов дешёвый — SDK лишь ставит счётчики, сеть трогает позже и сам.
        initMyTracker()

        // ── Только то, без чего не нарисовать первый экран ────────────────────
        // Supabase нужен сразу: восстановление сессии решает, какой экран открыть
        try {
            initializeSupabase()
            Log.d(TAG, "Supabase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase", e)
        }

        // ── Остальное — в фон ─────────────────────────────────────────────────
        // Раньше здесь подряд, на главном потоке и до первого кадра, выполнялись:
        // инициализация нативного MapKit, инициализация VK ID и десять постановок
        // периодических задач WorkManager. Ни одно из этого не нужно, чтобы
        // показать список автомобилей, но всё это стояло между запуском и
        // первым кадром.
        //
        // MapKit инициализируется лениво, при первом открытии карты
        // (см. ensureMapKit) — большинство сеансов до карты вообще не доходит.
        backgroundInit.launch {
            try {
                VKID.init(this@App)
                Log.d(TAG, "VK ID initialized successfully")
            } catch (e: Exception) {
                // Если приложение не заведено в кабинете VK, init падает.
                // Ловим, иначе перестанет работать не только вход через VK.
                Log.e(TAG, "Failed to initialize VK ID", e)
            }

            NotificationHelper.createChannel(this@App)

            scheduleMaintenanceCheck()
            scheduleFuelReminder()
            scheduleInsuranceCheck()
            scheduleDocumentExpiryCheck()
            scheduleWeeklySummary()
            scheduleBudgetAlert()
            scheduleFluidCheck()
            scheduleYearOwnerCheck()
            BackgroundSyncWorker.schedule(this@App)
            Log.d(TAG, "Фоновая инициализация завершена")
        }

        // Глобальная страховка: SocketException из любой корутины не должна крашить приложение.
        // RealtimeSyncManager имеет свой CoroutineExceptionHandler, но на случай если где-то
        // ещё остался незащищённый scope — ловим здесь и только логируем.
        val originalUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is java.net.SocketException || throwable is java.io.IOException) {
                Log.e(TAG, "Global safety net: network exception on ${thread.name} — suppressed crash", throwable)
                // Не крашим — это сетевые ошибки от потери соединения, не баги в коде
            } else {
                // Всё остальное (NPE, OOM, IllegalState...) — крашим как обычно
                originalUncaughtHandler?.uncaughtException(thread, throwable)
            }
        }

        // Start real-time sync after Supabase is ready
        try {
            realtimeSync = RealtimeSyncManager(this).also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RealtimeSyncManager", e)
        }

        initRuStorePush()

        // Регистрируем пуш-токены в Supabase (нужны для push когда приложение закрыто).
        // Токенов может быть два: Firebase и RuStore — какой сработает, зависит от
        // устройства, поэтому регистрируем оба и решение оставляем серверу.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            FcmTokenManager.registerCurrentToken()
            RuStorePushTokenManager.registerCurrentToken()
        }

        // Reconnect Realtime and refresh push tokens every time app comes to foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                realtimeSync?.reconnectIfNeeded()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    FcmTokenManager.registerCurrentToken()
                    RuStorePushTokenManager.registerCurrentToken()
                }
                syncIfStale()
            }
        })
    }

    /**
     * Поднимает пуши RuStore — для устройств без сервисов Google.
     *
     * Падать здесь нельзя ни при каких обстоятельствах: приложение RuStore может
     * быть не установлено, устареть или быть лишено фоновой работы. Любая такая
     * ситуация — не ошибка, а обычное состояние телефона, на котором доставку
     * возьмёт на себя Firebase. Поэтому всё завёрнуто в catch и только логируется.
     *
     * Без идентификатора проекта инициализация пропускается: приложение
     * собирается и работает, просто уведомления идут одним транспортом.
     */
    private fun initRuStorePush() {
        if (BuildConfig.RUSTORE_PUSH_PROJECT_ID.isBlank()) {
            Log.d(TAG, "RuStore Push не настроен — идентификатор проекта пуст")
            return
        }
        try {
            RuStorePushClient.init(
                application = this,
                projectId = BuildConfig.RUSTORE_PUSH_PROJECT_ID
            )
            Log.d(TAG, "RuStore Push инициализирован")
        } catch (e: Exception) {
            Log.w(TAG, "RuStore Push недоступен: ${e.message}")
        }
    }

    /**
     * Отправляет локальные данные на сервер при выходе приложения на передний план,
     * но не чаще раза в час.
     *
     * До этого fullSync() вызывался только при входе и выходе из аккаунта: тот, кто
     * не выходит, копил несинхронизированные записи неограниченно долго — совладелец
     * их не видел, а потеря телефона означала потерю всего.
     *
     * Троттлинг нужен, потому что один fullSync — это порядка 10 запросов на каждый
     * автомобиль, и гонять его на каждое разворачивание приложения слишком дорого.
     */
    private fun syncIfStale() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val auth = SupabaseAuthRepository()
                if (!auth.isUserLoggedIn()) return@launch

                val settings = SettingsManager(this@App)
                val elapsed = System.currentTimeMillis() - settings.getLastForegroundSync()
                if (elapsed < FOREGROUND_SYNC_INTERVAL_MS) {
                    Log.d(TAG, "Foreground sync skipped: ${elapsed / 60_000} min since last")
                    return@launch
                }

                val result = SyncRepositoryFactory.create(this@App, auth = auth).fullSync()
                if (result.isSuccess) {
                    // Метку двигаем только при успехе: иначе неудачная попытка
                    // заблокировала бы следующую на целый час
                    settings.setLastForegroundSync()
                    Log.d(TAG, "Foreground sync completed")
                } else {
                    Log.w(TAG, "Foreground sync failed", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Foreground sync crashed", e)
            }
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

    private fun scheduleWeeklySummary() {
        // Calculate delay until next Sunday at 09:00
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            val daysUntilSunday = (java.util.Calendar.SUNDAY - get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
            add(java.util.Calendar.DAY_OF_YEAR, if (daysUntilSunday == 0) 7 else daysUntilSunday)
        }
        val initialDelay = (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val workRequest = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeeklySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleInsuranceCheck() {
        val workRequest = PeriodicWorkRequestBuilder<InsuranceExpiryWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            InsuranceExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleDocumentExpiryCheck() {
        val workRequest = PeriodicWorkRequestBuilder<DocumentExpiryWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DocumentExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleBudgetAlert() {
        val workRequest = PeriodicWorkRequestBuilder<BudgetAlertWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BudgetAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleYearOwnerCheck() {
        val workRequest = PeriodicWorkRequestBuilder<YearOwnerCheckWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            YearOwnerCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleFluidCheck() {
        val workRequest = PeriodicWorkRequestBuilder<FluidCheckWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FluidCheckWorker.WORK_NAME,
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
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
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