package com.aggin.carcost.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.aggin.carcost.R

object NotificationHelper {

    // ── Existing channel (ТО + топливо) ────────────────────────────────────
    const val CHANNEL_ID = "maintenance_reminders"
    private const val CHANNEL_NAME = "Напоминания о ТО"
    private const val CHANNEL_DESCRIPTION = "Уведомления о предстоящем техническом обслуживании"

    // ── New channel (совместный доступ) ─────────────────────────────────────
    const val CHANNEL_SOCIAL_ID = "shared_activity"
    private const val CHANNEL_SOCIAL_NAME = "Активность участников"
    private const val CHANNEL_SOCIAL_DESCRIPTION =
        "Уведомления о расходах и ТО, добавленных другими участниками автомобиля"

    /** Creates ALL notification channels. Call once from App.onCreate(). */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = CHANNEL_DESCRIPTION }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOCIAL_ID,
                CHANNEL_SOCIAL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = CHANNEL_SOCIAL_DESCRIPTION }
        )
    }

    // ── ТО / Топливо уведомления ────────────────────────────────────────────

    fun sendFuelNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        estimatedLiters: Double,
        tankCapacity: Double?
    ) {
        val contentText = if (tankCapacity != null) {
            val pct = (estimatedLiters / tankCapacity * 100).toInt()
            "Топливо на исходе — около $pct% бака (~${estimatedLiters.toInt()} л)"
        } else {
            "Топливо на исходе — около ${estimatedLiters.toInt()} л"
        }
        notify(context, CHANNEL_ID, notificationId, "Заправьте автомобиль: $carName", contentText)
    }

    fun sendMaintenanceNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        serviceType: String,
        kmLeft: Int
    ) {
        val contentText = when {
            kmLeft <= 0 -> "$serviceType — требуется сейчас!"
            kmLeft <= 100 -> "$serviceType — осталось $kmLeft км"
            else -> "$serviceType — через $kmLeft км"
        }
        notify(context, CHANNEL_ID, notificationId, "Техобслуживание: $carName", contentText)
    }

    // ── Совместный доступ: расходы ──────────────────────────────────────────

    /**
     * Shown when another car member adds or edits an expense.
     * @param actorEmail  Email участника (e.g. "wife@mail.com") or null → "Участник"
     * @param isUpdate    true = редактирование, false = добавление
     */
    fun sendSharedExpenseNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        categoryName: String,
        amount: Double,
        actorEmail: String?,
        isUpdate: Boolean
    ) {
        val actor = actorEmail?.substringBefore("@") ?: "Участник"
        val action = if (isUpdate) "изменил(а) расход" else "добавил(а) расход"
        val title = "$actor $action • $carName"
        val body = "$categoryName — ${"%.0f".format(amount)} ₽"
        notify(context, CHANNEL_SOCIAL_ID, notificationId, title, body)
    }

    // ── Совместный доступ: напоминания ТО ───────────────────────────────────

    /**
     * Shown when another car member adds or updates a maintenance reminder.
     */
    fun sendSharedReminderNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        reminderTypeName: String,
        actorEmail: String?,
        isUpdate: Boolean
    ) {
        val actor = actorEmail?.substringBefore("@") ?: "Участник"
        val action = if (isUpdate) "обновил(а) напоминание" else "добавил(а) напоминание ТО"
        val title = "$actor $action • $carName"
        val body = reminderTypeName
        notify(context, CHANNEL_SOCIAL_ID, notificationId, title, body)
    }

    // ── Чат ─────────────────────────────────────────────────────────────────

    fun sendChatNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        senderName: String,
        message: String
    ) {
        notify(context, CHANNEL_SOCIAL_ID, notificationId, "$senderName • $carName", message)
    }

    // ── Internal helper ─────────────────────────────────────────────────────

    private fun notify(context: Context, channelId: String, id: Int, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    // ── Category / type name helpers ────────────────────────────────────────

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
