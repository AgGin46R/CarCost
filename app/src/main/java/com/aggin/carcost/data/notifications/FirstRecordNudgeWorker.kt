package com.aggin.carcost.data.notifications

import com.aggin.carcost.R
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Приглашение внести первую запись.
 *
 * ## Зачем это отдельно от остальных напоминаний
 *
 * Все прочие уведомления работают с данными: напоминают о ТО, страховке,
 * скорой заправке, подводят итоги недели. Человеку, который зарегистрировался
 * и ничего не записал, они не скажут ничего — напоминать не о чем.
 *
 * А таких — половина. На сервере из двадцати шести учётных записей тринадцать
 * не имеют ни одного расхода: люди дошли до регистрации и не попробовали.
 * Обычные механизмы возвращения проходят мимо них целиком.
 *
 * ## Почему один раз и почему про пользу
 *
 * Отправляется **ровно одно** уведомление. Не откликнувшийся на первое не
 * откликнется и на пятое, а настойчивость превращает приложение в источник
 * раздражения — и заодно обесценивает полезные напоминания, которые человек
 * начинает смахивать не читая.
 *
 * Текст говорит о том, что человек получит, а не о том, что он давно не
 * заходил. Упрёк не мотивирует, а обещание конкретной пользы — иногда да.
 */
class FirstRecordNudgeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "first_record_nudge"

        /** Идентификатор уведомления. Не пересекается с остальными в этом пакете */
        private const val NOTIFICATION_ID = 9100
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)

        // Уже приглашали — второго раза не будет
        if (settings.firstRecordNudgeSentFlow.first()) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val cars = db.carDao().getAllActiveCars().firstOrNull() ?: emptyList()

        // Машины нет — приглашать записывать расход рано и бессмысленно.
        // Молчим: человек ещё даже не начал заводить данные, и подталкивать
        // его к шагу, которого он не может сделать, только запутает.
        if (cars.isEmpty()) return Result.success()

        val hasAnyExpense = cars.any { car ->
            (db.expenseDao().getExpensesByCar(car.id).firstOrNull() ?: emptyList()).isNotEmpty()
        }

        // Записи есть — человек начал, приглашение ему не нужно. Помечаем как
        // отправленное, чтобы задача больше не возвращалась к этому вопросу.
        if (hasAnyExpense) {
            settings.setFirstRecordNudgeSent()
            return Result.success()
        }

        NotificationHelper.sendGenericNotification(
            context = applicationContext,
            notificationId = NOTIFICATION_ID,
            title = applicationContext.getString(R.string.notify_zapishite_pervuyu_zapravku),
            body = applicationContext.getString(R.string.notify_prilozhenie_poschitaet_rashod_na_sotnyu_i) +
                applicationContext.getString(R.string.notify_dlya_etogo_hvatit_dvuh_zapravok_do),
            carId = cars.first().id,
            navType = NotificationHelper.NAV_TYPE_ADD_FUEL
        )

        settings.setFirstRecordNudgeSent()
        return Result.success()
    }
}
