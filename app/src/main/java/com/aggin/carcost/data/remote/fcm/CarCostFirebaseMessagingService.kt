package com.aggin.carcost.data.remote.fcm

import android.util.Log
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.notifications.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "CarCostFCM"

/**
 * Получает FCM data-сообщения даже когда приложение убито.
 *
 * Edge Function отправляет data-only сообщения (без поля notification) с высоким приоритетом.
 * Это гарантирует что onMessageReceived() вызывается независимо от состояния приложения.
 *
 * Поля data:
 *   title      — заголовок уведомления
 *   body       — текст уведомления
 *   car_id     — ID авто
 *   table      — имя таблицы (expenses / chat_messages / maintenance_reminders / car_members)
 *   event_type — INSERT / UPDATE / DELETE
 */
class CarCostFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        scope.launch {
            FcmTokenManager.registerCurrentToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // ── Обновление приложения ─────────────────────────────────────────────
        if (data["type"] == "new_version") {
            val versionName = data["version_name"] ?: ""
            val releaseNotes = data["release_notes"] ?: ""
            Log.d(TAG, "FCM update notification: version=$versionName")
            NotificationHelper.sendUpdateNotification(applicationContext, versionName, releaseNotes)
            return
        }

        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val carId = data["car_id"] ?: ""
        val table = data["table"] ?: ""

        Log.d(TAG, "FCM received: table=$table carId=$carId")

        // Подавляем уведомления чата если пользователь сейчас в этом чате
        if (table == "chat_messages" && ActiveChatTracker.activeCarId == carId) {
            Log.d(TAG, "Suppressing chat notification — user is in this chat")
            return
        }

        val notifId = when (table) {
            "chat_messages"         -> 70_000 + Math.abs(carId.hashCode() % 9_000)
            "expenses"              -> 71_000 + Math.abs(carId.hashCode() % 9_000)
            "maintenance_reminders" -> 72_000 + Math.abs(carId.hashCode() % 9_000)
            "car_members"           -> 73_000 + Math.abs(carId.hashCode() % 9_000)
            else                    -> 74_000 + Math.abs(carId.hashCode() % 9_000)
        }

        val navType = when (table) {
            "chat_messages" -> NotificationHelper.NAV_TYPE_CHAT
            else            -> NotificationHelper.NAV_TYPE_CAR
        }

        NotificationHelper.sendGenericNotification(
            context = applicationContext,
            notificationId = notifId,
            title = title,
            body = body,
            carId = carId.ifBlank { null },
            navType = navType.takeIf { carId.isNotBlank() }
        )
    }
}
