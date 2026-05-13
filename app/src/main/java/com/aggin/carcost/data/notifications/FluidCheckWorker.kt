package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.FluidType

class FluidCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "fluid_levels_check"
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val fluidLevelDao = db.fluidLevelDao()

        val cars = carDao.getAllActiveCarsSync()
        val now = System.currentTimeMillis()
        var notifId = 5000 // базовый id чтобы не конфликтовать с другими уведомлениями

        cars.forEach { car ->
            val latestLevels = fluidLevelDao.getLatestFluidLevelsSync(car.id)
            val carName = "${car.brand} ${car.model}"

            FluidType.entries.forEach { type ->
                val record = latestLevels.firstOrNull { it.type == type }
                val isOverdue = record == null ||
                    (now - record.checkedAt) > type.checkIntervalDays * 86_400_000L

                if (isOverdue) {
                    val daysAgo = if (record != null) {
                        ((now - record.checkedAt) / 86_400_000L).toInt()
                    } else null

                    val body = if (daysAgo == null) {
                        "${type.emoji} ${type.labelRu} — ещё не проверялась"
                    } else {
                        "${type.emoji} ${type.labelRu} — проверялась $daysAgo дн. назад (интервал: ${type.checkIntervalDays} дн.)"
                    }

                    NotificationHelper.sendGenericNotification(
                        context = applicationContext,
                        notificationId = notifId++,
                        title = "Проверьте жидкости: $carName",
                        body = body
                    )
                }
            }
        }

        return Result.success()
    }
}
