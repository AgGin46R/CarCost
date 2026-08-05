package com.aggin.carcost.data.remote.push

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.notifications.NotificationHelper

private const val TAG = "PushHandler"

/**
 * Разбор пуш-сообщения и показ уведомления.
 *
 * Вынесено из сервиса Firebase, потому что транспортов теперь два: Firebase и
 * RuStore. Формат сообщения у них общий — это наши собственные поля, которые
 * складывает Edge Function, — поэтому и обработка обязана быть одна. Держи её
 * в двух местах, и однажды уведомление о новой версии починят в одном приёмнике,
 * а во втором забудут.
 *
 * Поля data:
 *   title      — заголовок уведомления
 *   body       — текст уведомления
 *   car_id     — идентификатор автомобиля
 *   table      — expenses / chat_messages / maintenance_reminders / car_members
 *   event_type — INSERT / UPDATE / DELETE
 *   type       — new_version для уведомления об обновлении приложения
 */
object PushMessageHandler {

    fun handle(context: Context, data: Map<String, String>, source: String) {
        // ── Обновление приложения ─────────────────────────────────────────────
        if (data["type"] == "new_version") {
            val versionName = data["version_name"] ?: ""
            val releaseNotes = data["release_notes"] ?: ""
            Log.d(TAG, "[$source] уведомление об обновлении: версия $versionName")
            NotificationHelper.sendUpdateNotification(context, versionName, releaseNotes)
            return
        }

        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val carId = data["car_id"] ?: ""
        val table = data["table"] ?: ""

        Log.d(TAG, "[$source] получено: table=$table carId=$carId")

        // Подавляем уведомления чата, если пользователь сейчас в этом чате
        if (table == "chat_messages" && ActiveChatTracker.activeCarId == carId) {
            Log.d(TAG, "[$source] чат открыт — уведомление подавлено")
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
            context = context,
            notificationId = notifId,
            title = title,
            body = body,
            carId = carId.ifBlank { null },
            navType = navType.takeIf { carId.isNotBlank() }
        )
    }
}
