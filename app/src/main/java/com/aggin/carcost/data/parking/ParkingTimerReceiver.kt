package com.aggin.carcost.data.parking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aggin.carcost.data.notifications.NotificationHelper

class ParkingTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PARKING_EXPIRED) {
            NotificationHelper.sendGenericNotification(
                context = context,
                notificationId = 7000,
                title = "Время парковки истекло!",
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
