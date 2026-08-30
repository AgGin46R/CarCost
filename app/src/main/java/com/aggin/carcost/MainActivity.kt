package com.aggin.carcost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.presentation.navigation.AppNavigation
import com.aggin.carcost.ui.theme.CarCostTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            com.aggin.carcost.data.local.settings.LocaleManager.wrap(newBase)
        )
    }

    /** Token extracted from carcost://invite?token=... deep link */
    var pendingInviteToken: String? = null
        private set

    /** Navigation route to open when activity starts from a notification tap */
    var pendingNavRoute by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ставится ДО super.onCreate — иначе окно успеет отрисоваться пустым
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Заставка висит, пока не станет ясно, какой экран показывать первым:
        // прочитаны настройки и восстановлена сессия. Внутри AppStartup стоит
        // предел ожидания, поэтому «навсегда» здесь невозможно
        splash.setKeepOnScreenCondition { !AppStartup.isReady.value }
        lifecycleScope.launch { AppStartup.prepare(applicationContext) }

        pendingInviteToken = intent?.data
            ?.takeIf { it.scheme == "carcost" && it.host == "invite" }
            ?.getQueryParameter("token")
        pendingNavRoute = extractNavRoute(intent)

        setContent {
            val settingsManager = SettingsManager(LocalContext.current)
            val theme by settingsManager.themeFlow.collectAsState(initial = "System")
            val accent by settingsManager.accentFlow.collectAsState(initial = "Blue")
            // Самообновление убрано: обновления идут через магазин.
            //
            // Приложение само скачивало APK и просило его установить — для этого
            // нужно разрешение REQUEST_INSTALL_PACKAGES, которое RuStore относит
            // к чувствительным и требует обосновывать. Обосновывать нечем:
            // магазин обновляет приложения сам, а второй механизм рядом с ним
            // означал бы, что у людей стоят версии, которых в магазине нет.
            //
            // Серверная часть (таблица app_config, триггер, send-version-push)
            // намеренно оставлена нетронутой: если механизм понадобится вне
            // магазина, возвращается откатом одного коммита.

            CarCostTheme(themeSetting = theme, accentSetting = accent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        pendingInviteToken = pendingInviteToken,
                        pendingNavRoute = pendingNavRoute,
                        onNavRouteConsumed = { pendingNavRoute = null }
                    )

                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingInviteToken = intent.data
            ?.takeIf { it.scheme == "carcost" && it.host == "invite" }
            ?.getQueryParameter("token")
        pendingNavRoute = extractNavRoute(intent)
    }

    companion object {
        /** Пометка, что приложение открыто с виджета, а не из уведомления */
        const val EXTRA_FROM_WIDGET = "from_widget"
    }

    private fun extractNavRoute(intent: android.content.Intent?): String? {
        val navType = intent?.getStringExtra(NotificationHelper.EXTRA_NAV_TYPE)
            ?: return null
        val carId = intent.getStringExtra(NotificationHelper.EXTRA_NAV_CAR_ID)
            ?: return null

        // Виджет и уведомления открывают приложение одними и теми же ключами,
        // поэтому отличить их можно только по отдельной пометке. Считаем именно
        // запуск с виджета: сохранится запись или нет — вопрос отдельный, а
        // ценность виджета в том, что им пользуются.
        if (intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)) {
            com.aggin.carcost.data.analytics.Analytics.expenseFromWidget()
        }

        return when (navType) {
            NotificationHelper.NAV_TYPE_CHAT        -> "chat/$carId"
            NotificationHelper.NAV_TYPE_ADD_EXPENSE -> "add_expense/$carId"
            // Категория задаётся тем же параметром, что и у чипов быстрого ввода
            NotificationHelper.NAV_TYPE_ADD_FUEL    -> "add_expense/$carId?category=FUEL"
            NotificationHelper.NAV_TYPE_GPS_TRIP    -> "gps_trip/$carId"
            NotificationHelper.NAV_TYPE_NAVIGATOR   -> "navigator"
            else -> "car_detail/$carId"
        }
    }
}
