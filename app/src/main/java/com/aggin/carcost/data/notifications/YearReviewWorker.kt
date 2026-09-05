package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.domain.year.YearSummaryCalculator
import java.util.Calendar

/**
 * Уведомление об итогах года.
 *
 * Пишет только в первых числах января и только тем, у кого за год набралось
 * достаточно записей. Итоги с тремя записями — не итоги, и уведомление о них
 * выглядело бы насмешкой над человеком, который приложением почти не
 * пользовался.
 */
class YearReviewWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "year_review_notification"

        /** Первые дни января: до третьего включительно */
        private const val LAST_DAY = 3
    }

    override suspend fun doWork(): Result {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MONTH) != Calendar.JANUARY) return Result.success()
        if (cal.get(Calendar.DAY_OF_MONTH) > LAST_DAY) return Result.success()

        val year = cal.get(Calendar.YEAR) - 1
        val db = AppDatabase.getDatabase(applicationContext)

        db.carDao().getAllActiveCarsSync().forEachIndexed { index, car ->
            val expenses = db.expenseDao().getExpensesByCarIdSync(car.id)
            val trips = db.gpsTripDao().getTripsByCarIdSync(car.id)
            val summary = YearSummaryCalculator.calculate(expenses, trips, year)
            if (!summary.hasEnoughData) return@forEachIndexed

            NotificationHelper.sendGenericNotification(
                context = applicationContext,
                notificationId = 7200 + index,
                title = applicationContext.getString(R.string.yearreview_notify_title, year),
                body = applicationContext.getString(R.string.yearreview_notify_body),
                carId = car.id,
                navType = NotificationHelper.NAV_TYPE_YEAR_REVIEW
            )
        }

        return Result.success()
    }
}
