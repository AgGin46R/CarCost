package com.aggin.carcost.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aggin.carcost.MainActivity
import com.aggin.carcost.R

object NotificationHelper {

    // Navigation extra keys — used by MainActivity to deep-link after tap
    const val EXTRA_NAV_TYPE = "nav_type"
    const val EXTRA_NAV_CAR_ID = "nav_car_id"
    const val NAV_TYPE_CHAT = "chat"
    const val NAV_TYPE_CAR = "car"

    // ── Channels ────────────────────────────────────────────────────────────────
    const val CHANNEL_ID = "maintenance_reminders"
    private const val CHANNEL_NAME = "Напоминания о ТО"
    private const val CHANNEL_DESCRIPTION = "Уведомления о предстоящем техническом обслуживании"

    const val CHANNEL_SOCIAL_ID = "shared_activity"
    private const val CHANNEL_SOCIAL_NAME = "Активность участников"
    private const val CHANNEL_SOCIAL_DESCRIPTION =
        "Уведомления о расходах и ТО, добавленных другими участниками автомобиля"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = CHANNEL_DESCRIPTION }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOCIAL_ID, CHANNEL_SOCIAL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = CHANNEL_SOCIAL_DESCRIPTION }
        )
    }

    // ── ТО / Топливо ─────────────────────────────────────────────────────────

    fun sendFuelNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        estimatedLiters: Double,
        tankCapacity: Double?
    ) {
        val body = if (tankCapacity != null) {
            val pct = (estimatedLiters / tankCapacity * 100).toInt()
            "Топливо на исходе — около $pct% бака (~${estimatedLiters.toInt()} л)"
        } else {
            "Топливо на исходе — около ${estimatedLiters.toInt()} л"
        }
        notify(context, CHANNEL_ID, notificationId, "Заправьте автомобиль: $carName", body)
    }

    fun sendMaintenanceNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        serviceType: String,
        kmLeft: Int
    ) {
        val body = when {
            kmLeft <= 0 -> "$serviceType — требуется сейчас!"
            kmLeft <= 100 -> "$serviceType — осталось $kmLeft км"
            else -> "$serviceType — через $kmLeft км"
        }
        notify(context, CHANNEL_ID, notificationId, "Техобслуживание: $carName", body)
    }

    // ── Чат ─────────────────────────────────────────────────────────────────

    fun sendChatNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        senderName: String,
        message: String,
        carId: String? = null
    ) {
        val intent = carId?.let { makeNavIntent(context, NAV_TYPE_CHAT, it, notificationId) }
        notify(context, CHANNEL_SOCIAL_ID, notificationId, "$senderName • $carName", message, intent)
    }

    // ── Расходы ────────────────────────────────────────────────────────────

    fun sendSharedExpenseNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        categoryName: String,
        amount: Double,
        actorEmail: String?,
        isUpdate: Boolean,
        carId: String? = null
    ) {
        val actor = actorEmail?.substringBefore("@") ?: "Участник"
        val action = if (isUpdate) "изменил(а) расход" else "добавил(а) расход"
        val title = "$actor $action • $carName"
        val body = "$categoryName — ${"%.0f".format(amount)} ₽"
        val intent = carId?.let { makeNavIntent(context, NAV_TYPE_CAR, it, notificationId) }
        notify(context, CHANNEL_SOCIAL_ID, notificationId, title, body, intent)
    }

    // ── Напоминания ТО ──────────────────────────────────────────────────────

    fun sendSharedReminderNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        reminderTypeName: String,
        actorEmail: String?,
        isUpdate: Boolean,
        carId: String? = null
    ) {
        val actor = actorEmail?.substringBefore("@") ?: "Участник"
        val action = if (isUpdate) "обновил(а) напоминание" else "добавил(а) напоминание ТО"
        val title = "$actor $action • $carName"
        val intent = carId?.let { makeNavIntent(context, NAV_TYPE_CAR, it, notificationId) }
        notify(context, CHANNEL_SOCIAL_ID, notificationId, title, reminderTypeName, intent)
    }

    // ── Приглашения ────────────────────────────────────────────────────────

    fun sendInvitationNotification(
        context: Context,
        notificationId: Int,
        carName: String
    ) {
        notify(
            context, CHANNEL_SOCIAL_ID, notificationId,
            "Вас пригласили в автомобиль",
            "Новое приглашение: $carName. Откройте приложение, чтобы принять."
        )
    }

    // ── FCM generic ─────────────────────────────────────────────────────────

    fun sendGenericNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        carId: String? = null,
        navType: String? = null
    ) {
        val intent = if (carId != null && navType != null)
            makeNavIntent(context, navType, carId, notificationId) else null
        notify(context, CHANNEL_SOCIAL_ID, notificationId, title, body, intent)
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun makeNavIntent(
        context: Context,
        navType: String,
        carId: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, navType)
            putExtra(EXTRA_NAV_CAR_ID, carId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        body: String,
        contentIntent: PendingIntent? = null
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        manager.notify(id, builder.build())
    }

    // ── Display name helpers ─────────────────────────────────────────────────

    fun categoryDisplayName(category: String): String = when (category.uppercase()) {
        "FUEL"         -> "Топливо"
        "MAINTENANCE"  -> "Обслуживание"
        "REPAIR"       -> "Ремонт"
        "INSURANCE"    -> "Страховка"
        "TAX"          -> "Налоги"
        "PARKING"      -> "Парковка"
        "TOLL"         -> "Платная дорога"
        "WASH"         -> "Мойка"
        "FINE"         -> "Штраф"
        "ACCESSORIES"  -> "Аксессуары"
        else           -> "Расход"
    }

    fun reminderTypeDisplayName(type: String): String = when (type.uppercase()) {
        "OIL_CHANGE"           -> "Замена масла"
        "OIL_FILTER"           -> "Масляный фильтр"
        "AIR_FILTER"           -> "Воздушный фильтр"
        "CABIN_FILTER"         -> "Салонный фильтр"
        "FUEL_FILTER"          -> "Топливный фильтр"
        "SPARK_PLUGS"          -> "Свечи зажигания"
        "BRAKE_PADS"           -> "Тормозные колодки"
        "TIMING_BELT"          -> "Ремень ГРМ"
        "TRANSMISSION_FLUID"   -> "Трансмиссионное масло"
        "COOLANT"              -> "Охлаждающая жидкость"
        "BRAKE_FLUID"          -> "Тормозная жидкость"
        else                   -> "Техобслуживание"
    }
}
