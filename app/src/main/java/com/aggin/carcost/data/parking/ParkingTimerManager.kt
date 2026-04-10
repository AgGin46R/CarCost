package com.aggin.carcost.data.parking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ParkingTimerManager {

    private const val PREFS_NAME = "parking_timer"
    private const val KEY_END_TIME = "end_time"

    fun startTimer(context: Context, durationMinutes: Int) {
        val endTime = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_END_TIME, endTime).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context, 7000,
            Intent(context, ParkingTimerReceiver::class.java).apply {
                action = ParkingTimerReceiver.ACTION_PARKING_EXPIRED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, endTime, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, intent)
        }
    }

    fun cancelTimer(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context, 7000,
            Intent(context, ParkingTimerReceiver::class.java).apply {
                action = ParkingTimerReceiver.ACTION_PARKING_EXPIRED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(intent)
        clearState(context)
    }

    fun getEndTime(context: Context): Long? {
        val endTime = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_END_TIME, 0L)
        return if (endTime > System.currentTimeMillis()) endTime else null
    }

    fun clearState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_END_TIME).apply()
    }
}
