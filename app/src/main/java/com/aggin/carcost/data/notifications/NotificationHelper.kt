package com.aggin.carcost.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.aggin.carcost.presentation.common.displayName
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aggin.carcost.MainActivity
import com.aggin.carcost.R
import com.aggin.carcost.data.local.settings.SettingsManager

object NotificationHelper {

    // Navigation extra keys — used by MainActivity to deep-link after tap
    const val EXTRA_NAV_TYPE = "nav_type"
    const val EXTRA_NAV_CAR_ID = "nav_car_id"
    const val NAV_TYPE_CHAT = "chat"
    const val NAV_TYPE_CAR = "car"
    const val NAV_TYPE_ADD_EXPENSE = "add_expense"
    /** Форма расхода с уже выбранной заправкой — самый частый ввод, с виджета */
    const val NAV_TYPE_ADD_FUEL = "add_fuel"
    const val NAV_TYPE_GPS_TRIP = "gps_trip"
    const val NAV_TYPE_NAVIGATOR = "navigator"
    const val NAV_TYPE_YEAR_REVIEW = "year_review"

    /** Название заправки для подстановки в форму расхода */
    const val EXTRA_NAV_EXTRA = "nav_extra"
    const val NAV_TYPE_UPDATE = "update"

    // ── Channels ────────────────────────────────────────────────────────────────
    // Только идентификаторы каналов остаются константами: они уходят в систему
    // и меняться не должны никогда. Названия и описания — ресурсы, они читаются
    // при создании канала, где контекст уже есть.
    const val CHANNEL_ID = "maintenance_reminders"
    const val CHANNEL_SOCIAL_ID = "shared_activity"
    const val CHANNEL_UPDATE_ID = "app_updates"
    const val NOTIF_ID_UPDATE = 99_000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.profile_napominaniya_o_to), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_uvedomleniya_o_predstoyaschem) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOCIAL_ID, context.getString(R.string.notify_aktivnost_uchastnikov), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_uvedomleniya_o_rashodah_i_to_dobavlennyh) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE_ID, context.getString(R.string.notify_obnovleniya_prilozheniya), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notify_uvedomleniya_o_novyh_versiyah_carcost) }
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
            context.getString(R.string.notify_toplivo_na_ishode_okolo_baka_l, pct, estimatedLiters.toInt())
        } else {
            context.getString(R.string.notify_toplivo_na_ishode_okolo_l, estimatedLiters.toInt())
        }
        notify(
            context, CHANNEL_ID, notificationId,
            context.getString(R.string.notify_zapravte_avtomobil, carName), body,
            kind = SettingsManager.NotifKind.FUEL
        )
    }

    fun sendMaintenanceNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        serviceType: String,
        kmLeft: Int
    ) {
        val body = when {
            kmLeft <= 0 -> context.getString(R.string.notify_pora_delat, serviceType)
            kmLeft <= 100 -> context.getString(R.string.notify_ostalos_km, serviceType, kmLeft)
            else -> context.getString(R.string.notify_cherez_km, serviceType, kmLeft)
        }
        notify(
            context, CHANNEL_ID, notificationId,
            context.getString(R.string.notify_tehobsluzhivanie, carName), body,
            kind = SettingsManager.NotifKind.MAINTENANCE
        )
    }

    // ── Бюджет ────────────────────────────────────────────────────────────────

    fun sendBudgetAlertNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        categoryName: String,
        usedPercent: Int
    ) {
        val title = context.getString(R.string.notify_byudzhet_pochti_ischerpan, carName)
        val body = context.getString(R.string.notify_ispolzovano_mesyachnogo_limita, categoryName, usedPercent)
        notify(context, CHANNEL_ID, notificationId, title, body, kind = SettingsManager.NotifKind.BUDGET)
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
        val actor = actorEmail?.substringBefore("@") ?: context.getString(R.string.analytics_uchastnik)
        val action = if (isUpdate) context.getString(R.string.notify_izmenil_a_rashod) else context.getString(R.string.notify_dobavil_a_rashod)
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
        val actor = actorEmail?.substringBefore("@") ?: context.getString(R.string.analytics_uchastnik)
        val action = if (isUpdate) context.getString(R.string.notify_obnovil_a_napominanie) else context.getString(R.string.notify_dobavil_a_napominanie_to)
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
            context.getString(R.string.notify_vas_priglasili_v_avtomobil),
            context.getString(R.string.notify_novoe_priglashenie_otkroyte_prilozhenie, carName)
        )
    }

    // ── Обновление приложения ────────────────────────────────────────────────

    fun sendUpdateNotification(
        context: Context,
        versionName: String,
        releaseNotes: String = ""
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_UPDATE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID_UPDATE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (releaseNotes.isNotBlank())
            releaseNotes.take(120)
        else
            context.getString(R.string.notify_nazhmite_chtoby_ustanovit_obnovlenie)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_ID)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle(context.getString(R.string.notify_dostupno_obnovlenie_carcost, versionName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIF_ID_UPDATE, notification)
    }

    // ── FCM generic ─────────────────────────────────────────────────────────

    /**
     * @param navExtra дополнительное значение для перехода — например,
     *   название заправки, которое подставится в форму расхода. Что с ним
     *   делать, решает [com.aggin.carcost.MainActivity] по [navType]
     */
    fun sendGenericNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        carId: String? = null,
        navType: String? = null,
        navExtra: String? = null,
        kind: SettingsManager.NotifKind = SettingsManager.NotifKind.ALWAYS,
        ignoreQuietHours: Boolean = false
    ) {
        val intent = if (carId != null && navType != null)
            makeNavIntent(context, navType, carId, notificationId, navExtra) else null
        notify(
            context, CHANNEL_SOCIAL_ID, notificationId, title, body, intent,
            kind = kind, ignoreQuietHours = ignoreQuietHours
        )
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun makeNavIntent(
        context: Context,
        navType: String,
        carId: String,
        requestCode: Int,
        navExtra: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, navType)
            putExtra(EXTRA_NAV_CAR_ID, carId)
            navExtra?.let { putExtra(EXTRA_NAV_EXTRA, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Единственное место, где уведомление действительно показывается.
     *
     * Проверка переключателя стоит именно здесь, а не в каждом воркере.
     * Раньше её не было нигде: три переключателя в профиле сохранялись и не
     * значили ничего — человек выключал напоминания о ТО и продолжал их
     * получать. Настройка, которая не работает, хуже отсутствующей: с ней
     * человек считает, что уже всё выключил, и идёт отключать уведомления
     * приложения целиком.
     *
     * @param kind чем управляется этот вид уведомлений
     * @param ignoreQuietHours для того, что человек назначил сам на конкретное
     *   время. Таймер парковки на три часа ночи — не беспокойство, а то, о чём
     *   попросили; проглотить его значит сломать функцию
     */
    private fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        body: String,
        contentIntent: PendingIntent? = null,
        kind: SettingsManager.NotifKind = SettingsManager.NotifKind.ALWAYS,
        ignoreQuietHours: Boolean = false
    ) {
        val settings = SettingsManager(context)
        if (!settings.isNotifEnabled(kind)) return
        if (!ignoreQuietHours && settings.isCurrentlyQuietHours()) return

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

    fun categoryDisplayName(context: Context, category: String): String =
        com.aggin.carcost.presentation.common.categoryDisplayName(context, category)

    /**
     * Название работы по строковому коду из уведомления.
     *
     * Своей таблицы больше нет: она дублировала справочник в Labels.kt, и
     * двенадцать одинаковых подписей пришлось бы переводить дважды, а потом
     * следить, чтобы они не разошлись.
     */
    fun reminderTypeDisplayName(context: Context, type: String): String {
        val serviceType = runCatching {
            com.aggin.carcost.data.local.database.entities.ServiceType.valueOf(type.uppercase())
        }.getOrNull()
        return if (serviceType != null) {
            serviceType.displayName(context)
        } else {
            context.getString(R.string.notify_tehobsluzhivanie_2)
        }
    }
}
