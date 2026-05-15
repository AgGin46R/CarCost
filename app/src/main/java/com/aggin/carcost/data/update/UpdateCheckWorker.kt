package com.aggin.carcost.data.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aggin.carcost.MainActivity
import com.aggin.carcost.R
import com.aggin.carcost.data.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Периодически (каждые 12 часов) проверяет наличие обновлений через Supabase.
 * Если найдена новая версия — показывает локальное уведомление.
 * Не требует Firebase.
 */
class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "carcost_update_check"
        private const val NOTIF_ID = 9001
        private const val PREF_NAME = "update_prefs"
        private const val KEY_LAST_NOTIFIED_CODE = "last_notified_version_code"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val info = AppUpdateManager(context).checkForUpdate() ?: return Result.success()

        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_CODE, 0)

        // Не спамим — уведомляем о каждой новой версии максимум один раз
        if (info.versionCode <= lastNotified) return Result.success()
        prefs.edit().putInt(KEY_LAST_NOTIFIED_CODE, info.versionCode).apply()

        showNotification(info)
        return Result.success()
    }

    private fun showNotification(info: VersionInfo) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(NotificationHelper.EXTRA_NAV_TYPE, NotificationHelper.NAV_TYPE_UPDATE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            append("Доступна версия ${info.versionName}")
            if (info.releaseNotes.isNotBlank()) {
                append("\n")
                append(info.releaseNotes.lines().take(2).joinToString("\n"))
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Обновление CarCost")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }
}
