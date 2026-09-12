package com.aggin.carcost.data.parking

import com.aggin.carcost.data.notifications.NotificationIds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aggin.carcost.data.notifications.NotificationHelper

class ParkingTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PARKING_EXPIRED) {
            NotificationHelper.sendGenericNotification(
                kind = com.aggin.carcost.data.local.settings.SettingsManager.NotifKind.ALWAYS,
                ignoreQuietHours = true,
                context = context,
                notificationId = NotificationIds.PARKING_TIMER,
                title = "Время парковки истекло",
                body = "Не забудьте передвинуть или оплатить парковку."
            )
            // Clear stored state
            ParkingTimerManager.clearState(context)
        }
    }

    companion object {
        const val ACTION_PARKING_EXPIRED = "com.aggin.carcost.PARKING_EXPIRED"
    }
}
