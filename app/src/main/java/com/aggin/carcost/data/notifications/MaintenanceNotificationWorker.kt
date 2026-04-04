package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase

class MaintenanceNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "maintenance_check"
        // Уведомлять если до ТО осталось менее 500 км
        private const val NOTIFICATION_THRESHOLD_KM = 500
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val reminderDao = db.maintenanceReminderDao()
        val carDao = db.carDao()

        val reminders = reminderDao.getAllActiveReminders()

        reminders.forEachIndexed { index, reminder ->
            val car = carDao.getCarById(reminder.carId) ?: return@forEachIndexed
            val kmLeft = reminder.nextChangeOdometer - car.currentOdometer

            if (kmLeft <= NOTIFICATION_THRESHOLD_KM) {
                NotificationHelper.sendMaintenanceNotification(
                    context = applicationContext,
                    notificationId = index + 1,
                    carName = "${car.brand} ${car.model}",
                    serviceType = reminder.type.displayName,
                    kmLeft = kmLeft
                )
            }
        }

        return Result.success()
    }
}
