package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.settings.SettingsManager
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Worker для проверки бюджетных лимитов.
 * Запускается раз в сутки.
 * Если расходы по какой-либо категории превышают 80% месячного бюджета -
 * отправляет пуш-уведомление.
 */
class BudgetAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "budget_alert_check"
        const val BUDGET_ALERT_THRESHOLD = 0.80  // 80%
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)

        // Проверяем, включён ли алерт бюджета
        if (!settings.notifBudgetAlertFlow.first()) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val budgetDao = db.categoryBudgetDao()

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        // Начало текущего месяца и текущий момент
        val startOfMonth = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfMonth = System.currentTimeMillis()

        val cars = carDao.getAllActiveCars().first()
        var notifId = 2000

        cars.forEach { car ->
            val budgets = budgetDao.getBudgetsByCarIdAndPeriod(car.id, month, year).first()
            if (budgets.isEmpty()) return@forEach

            val carName = "${car.brand} ${car.model}"
            // Загружаем расходы за текущий месяц один раз для всей машины
            val monthExpenses = expenseDao.getExpensesInDateRangeSync(car.id, startOfMonth, endOfMonth)

            budgets.forEach { budget ->
                if (budget.monthlyLimit <= 0) return@forEach

                val spent = monthExpenses
                    .filter { it.category == budget.category }
                    .sumOf { it.amount }
                val usedPct = (spent / budget.monthlyLimit * 100).toInt()

                if (usedPct >= (BUDGET_ALERT_THRESHOLD * 100).toInt()) {
                    val categoryName = NotificationHelper.categoryDisplayName(budget.category.name)
                    NotificationHelper.sendBudgetAlertNotification(
                        context = applicationContext,
                        notificationId = notifId++,
                        carName = carName,
                        categoryName = categoryName,
                        usedPercent = usedPct
                    )
                }
            }
        }

        return Result.success()
    }
}
