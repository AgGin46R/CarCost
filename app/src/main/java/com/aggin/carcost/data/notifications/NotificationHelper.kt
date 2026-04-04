package com.aggin.carcost.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.aggin.carcost.R

object NotificationHelper {

    const val CHANNEL_ID = "maintenance_reminders"
    private const val CHANNEL_NAME = "Напоминания о ТО"
    private const val CHANNEL_DESCRIPTION = "Уведомления о предстоящем техническом обслуживании"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun sendMaintenanceNotification(
        context: Context,
        notificationId: Int,
        carName: String,
        serviceType: String,
        kmLeft: Int
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = when {
            kmLeft <= 0 -> "$serviceType — требуется сейчас!"
            kmLeft <= 100 -> "$serviceType — осталось $kmLeft км"
            else -> "$serviceType — через $kmLeft км"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle("Техобслуживание: $carName")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }
}
