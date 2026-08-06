package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.FluidType
import kotlin.math.abs

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
        // Базовый id, чтобы не конфликтовать с другими уведомлениями. К нему
        // прибавляется хеш автомобиля — так повторная проверка обновляет уже
        // показанное уведомление, а не добавляет ещё одно.
        val notifId = 5000

        // Одно уведомление на автомобиль, а не на каждую жидкость.
        //
        // Раньше уведомление отправлялось внутри цикла по типам жидкостей: у
        // человека с двумя автомобилями и пятью просроченными проверками
        // разом прилетало десять уведомлений. Ими забивало всю шторку, и
        // читать их переставали — то есть механизм работал против себя.
        //
        // Теперь просроченные собираются в список и уходят одним сообщением.
        // Идентификатор уведомления привязан к автомобилю, поэтому повторная
        // проверка обновляет уже показанное, а не плодит копии.
        cars.forEach { car ->
            val latestLevels = fluidLevelDao.getLatestFluidLevelsSync(car.id)
            val carName = "${car.brand} ${car.model}"

            val overdue = FluidType.entries.mapNotNull { type ->
                val record = latestLevels.firstOrNull { it.type == type }
                val isOverdue = record == null ||
                    (now - record.checkedAt) > type.checkIntervalDays * 86_400_000L

                if (!isOverdue) return@mapNotNull null

                val daysAgo = record?.let { ((now - it.checkedAt) / 86_400_000L).toInt() }
                if (daysAgo == null) {
                    "${type.emoji} ${type.labelRu} — ещё не проверялась"
                } else {
                    "${type.emoji} ${type.labelRu} — $daysAgo дн. назад"
                }
            }

            if (overdue.isEmpty()) return@forEach

            // Заголовок называет число, чтобы смысл был понятен ещё до раскрытия:
            // в свёрнутом виде система показывает только первую строку текста.
            val title = when (overdue.size) {
                1    -> "Проверьте жидкость: $carName"
                else -> "Проверьте жидкости (${overdue.size}): $carName"
            }

            NotificationHelper.sendGenericNotification(
                context = applicationContext,
                notificationId = notifId + abs(car.id.hashCode() % 900),
                title = title,
                body = overdue.joinToString("\n")
            )
        }

        return Result.success()
    }
}
