package com.aggin.carcost.data.notifications

import com.aggin.carcost.R
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import java.util.concurrent.TimeUnit

class MaintenanceNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "maintenance_check"
        // Уведомлять если до ТО осталось менее 500 км
        private const val NOTIFICATION_THRESHOLD_KM = 500
        // ...или менее 7 дней, если у напоминания задана дата
        private const val NOTIFICATION_THRESHOLD_DAYS = 7
        // Своя область id, чтобы не пересекаться с остальными уведомлениями
        // (5000 — жидкости, 6000 — документы, 9001 — обновление, 50000+ — чат и расходы)
        private const val DATE_NOTIFICATION_ID_BASE = 7000
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val reminderDao = db.maintenanceReminderDao()
        val carDao = db.carDao()

        val reminders = reminderDao.getAllActiveReminders()
        val now = System.currentTimeMillis()

        reminders.forEachIndexed { index, reminder ->
            val car = carDao.getCarById(reminder.carId) ?: return@forEachIndexed
            val carName = "${car.brand} ${car.model}"
            val kmLeft = reminder.nextChangeOdometer - car.currentOdometer

            if (kmLeft <= NOTIFICATION_THRESHOLD_KM) {
                NotificationHelper.sendMaintenanceNotification(
                    context = applicationContext,
                    notificationId = index + 1,
                    carName = carName,
                    serviceType = applicationContext.getString(reminder.type.displayNameRes),
                    kmLeft = kmLeft
                )
                return@forEachIndexed
            }

            // Напоминание по дате. Поля intervalDays/nextChangeDate давно есть
            // в схеме и в UI, но до сих пор их не читал ни один воркер —
            // напоминание по дате молча не срабатывало.
            val dueDate = reminder.nextChangeDate ?: return@forEachIndexed
            val daysLeft = TimeUnit.MILLISECONDS.toDays(dueDate - now)

            if (daysLeft <= NOTIFICATION_THRESHOLD_DAYS) {
                val body = when {
                    daysLeft < 0 -> applicationContext.getString(R.string.notify_prosrocheno_na, applicationContext.getString(reminder.type.displayNameRes), -daysLeft, dayWord(-daysLeft))
                    daysLeft == 0L -> applicationContext.getString(R.string.notify_segodnya, applicationContext.getString(reminder.type.displayNameRes))
                    else -> applicationContext.getString(R.string.notify_cherez, applicationContext.getString(reminder.type.displayNameRes), daysLeft, dayWord(daysLeft))
                }
                NotificationHelper.sendGenericNotification(
                    context = applicationContext,
                    notificationId = DATE_NOTIFICATION_ID_BASE + index,
                    title = applicationContext.getString(R.string.notify_to_po_sroku, carName),
                    body = body,
                    carId = reminder.carId,
                    navType = NotificationHelper.NAV_TYPE_CAR
                )
            }
        }

        return Result.success()
    }

    private fun dayWord(days: Long): String = when {
        days % 10 == 1L && days % 100 != 11L -> applicationContext.getString(R.string.notify_den)
        days % 10 in 2..4 && days % 100 !in 12..14 -> applicationContext.getString(R.string.notify_dnya)
        else -> applicationContext.getString(R.string.notify_dney)
    }
}
