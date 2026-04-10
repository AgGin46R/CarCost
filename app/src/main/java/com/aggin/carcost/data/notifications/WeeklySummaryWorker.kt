package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class WeeklySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_summary"
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val gpsTripDao = db.gpsTripDao()

        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        val cars = carDao.getAllActiveCars().firstOrNull() ?: emptyList()

        var totalSpent = 0.0
        var totalKm = 0.0
        var fuelCount = 0

        for (car in cars) {
            val expenses = expenseDao.getExpensesByCar(car.id).firstOrNull() ?: emptyList()
            val weekExpenses = expenses.filter { it.date >= weekAgo }
            totalSpent += weekExpenses.sumOf { it.amount }
            fuelCount += weekExpenses.count {
                it.category == com.aggin.carcost.data.local.database.entities.ExpenseCategory.FUEL
            }
            val trips = gpsTripDao.getTripsSince(car.id, weekAgo).firstOrNull() ?: emptyList()
            totalKm += trips.sumOf { it.distanceKm }
        }

        val body = buildString {
            append("Потрачено: %.0f ₽".format(totalSpent))
            if (totalKm > 0) append(" • Пройдено: %.0f км".format(totalKm))
            if (fuelCount > 0) append(" • Заправок: $fuelCount")
        }

        NotificationHelper.sendGenericNotification(
            context = applicationContext,
            notificationId = 9000,
            title = "Итоги недели",
            body = body
        )

        return Result.success()
    }
}
