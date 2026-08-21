package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.util.CurrencyUtils
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Итоги недели.
 *
 * ## Что здесь было не так
 *
 * Уведомление отправлялось **безусловно**. Человек, ничего за неделю не
 * записавший, каждую неделю получал «Потрачено: 0 ₽» — и приучался смахивать
 * всё от приложения не глядя. Вместе с полезным: напоминаниями о страховке,
 * техосмотре и плановом обслуживании, ради которых приложение и ставили.
 *
 * Одно бессмысленное уведомление обесценивает все остальные, потому что человек
 * перестаёт их читать раньше, чем узнаёт, о чём они.
 *
 * ## Что изменилось
 *
 * Молчим, когда сказать нечего. И говорим содержательно: сумма без точки
 * отсчёта ничего не сообщает — «потрачено 4 500» это много или мало? Поэтому
 * сравниваем с предыдущей неделей.
 */
class WeeklySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_summary"

        /**
         * Насколько должна отличаться неделя, чтобы об этом стоило говорить.
         *
         * Колебания в пределах десятой части — обычный разброс между неделями,
         * и сообщать о них значит выдавать шум за наблюдение.
         */
        private const val NOTABLE_CHANGE = 0.10
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val gpsTripDao = db.gpsTripDao()

        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val twoWeeksAgo = now - TimeUnit.DAYS.toMillis(14)

        val cars = carDao.getAllActiveCars().firstOrNull() ?: emptyList()
        if (cars.isEmpty()) return Result.success()

        var totalSpent = 0.0
        var previousSpent = 0.0
        var totalKm = 0.0
        var fuelCount = 0

        for (car in cars) {
            val expenses = expenseDao.getExpensesByCar(car.id).firstOrNull() ?: emptyList()

            val thisWeek = expenses.filter { it.date >= weekAgo }
            totalSpent += thisWeek.sumOf { it.amount }
            fuelCount += thisWeek.count {
                it.category == ExpenseCategory.FUEL || it.category == ExpenseCategory.CHARGING
            }

            // Предыдущая неделя — точка отсчёта для сравнения
            previousSpent += expenses
                .filter { it.date in twoWeeksAgo until weekAgo }
                .sumOf { it.amount }

            val trips = gpsTripDao.getTripsSince(car.id, weekAgo).firstOrNull() ?: emptyList()
            totalKm += trips.sumOf { it.distanceKm }
        }

        // Молчим, когда говорить не о чем. Это главная правка: раньше здесь
        // уходило «Потрачено: 0 ₽» — уведомление, которое можно только смахнуть.
        if (totalSpent <= 0.0 && totalKm <= 0.0 && fuelCount == 0) {
            return Result.success()
        }

        // Валюта берётся у автомобиля. Раньше символ рубля стоял в тексте
        // жёстко, и у машины в евро уведомление врало.
        val currency = cars.first().currency

        val body = buildString {
            append("Потрачено: ${CurrencyUtils.format(totalSpent, currency)}")
            comparisonWithPreviousWeek(totalSpent, previousSpent)?.let { append(" ($it)") }
            if (totalKm > 0) append(" • Пройдено: %.0f км".format(totalKm))
            if (fuelCount > 0) append(" • Заправок: $fuelCount")

            // Наблюдение о расходе — то, ради чего человек и ведёт учёт.
            //
            // Добавляется сюда, а не отдельным уведомлением: смысл всей работы в
            // том, чтобы уведомлений стало меньше и они стали содержательнее.
            // Ещё один поток сообщений сработал бы против этой же цели.
            consumptionNote(cars, expenseDao)?.let { append("\n\n$it") }
        }

        NotificationHelper.sendGenericNotification(
            context = applicationContext,
            notificationId = 9000,
            title = "Итоги недели",
            body = body
        )

        return Result.success()
    }

    /**
     * Замечание о расходе топлива, если он заметно изменился.
     *
     * Сравниваются последние отрезки «от полного до полного» со средним за всю
     * историю. Рост расхода — не приговор, но повод посмотреть на давление в
     * шинах или стиль езды, и человек об этом сам не догадается: цифра лежит на
     * экране аналитики, куда он заходит редко.
     *
     * Возвращает null, когда данных мало или изменение в пределах обычного
     * разброса. Молчание здесь лучше выдуманного наблюдения.
     */
    private suspend fun consumptionNote(
        cars: List<com.aggin.carcost.data.local.database.entities.Car>,
        expenseDao: com.aggin.carcost.data.local.database.dao.ExpenseDao
    ): String? {
        val car = cars.firstOrNull() ?: return null
        val expenses = expenseDao.getExpensesByCar(car.id).firstOrNull() ?: return null

        val segments = com.aggin.carcost.domain.fuel.FuelConsumptionCalculator.segments(expenses)
        // Меньше четырёх отрезков — сравнивать последние со средним рано:
        // любой единичный выброс перевесит
        if (segments.size < 4) return null

        val overall = segments.sumOf { it.liters } * 100.0 / segments.sumOf { it.km }
        val recent = segments.takeLast(2)
        val recentAvg = recent.sumOf { it.liters } * 100.0 / recent.sumOf { it.km }
        if (overall <= 0.0) return null

        val change = (recentAvg - overall) / overall
        if (abs(change) < NOTABLE_CHANGE) return null

        val percent = abs(change * 100).roundToInt()
        return if (change > 0) {
            "Расход вырос на $percent%: %.1f против %.1f л/100 км в среднем. "
                .format(recentAvg, overall) + "Стоит проверить давление в шинах."
        } else {
            "Расход снизился на $percent%: %.1f против %.1f л/100 км в среднем."
                .format(recentAvg, overall)
        }
    }

    /**
     * «на 18% больше» или «на 12% меньше» — либо ничего.
     *
     * Возвращает null, когда сравнивать не с чем (первая неделя учёта) или
     * когда разница слишком мала, чтобы называться изменением.
     */
    private fun comparisonWithPreviousWeek(current: Double, previous: Double): String? {
        if (previous <= 0.0) return null

        val change = (current - previous) / previous
        if (abs(change) < NOTABLE_CHANGE) return null

        val percent = abs(change * 100).roundToInt()
        return if (change > 0) "на $percent% больше" else "на $percent% меньше"
    }
}
