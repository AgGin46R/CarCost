package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.first

class FuelReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "fuel_reminder_check"
        // Уведомлять если расчётный остаток < этого порога (литры)
        private const val LOW_FUEL_THRESHOLD_LITERS = 10.0
        // Или если остаток < 20% ёмкости бака
        private const val LOW_FUEL_THRESHOLD_PCT = 0.20
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()

        val cars = carDao.getAllCars().first()

        cars.forEachIndexed { index, car ->
            // Берём последние топливные расходы
            val fuelExpenses = expenseDao.getExpensesByCategory(car.id, ExpenseCategory.FUEL)
                .first()
                .sortedByDescending { it.date }

            if (fuelExpenses.isEmpty()) return@forEachIndexed

            // Объём бака: указанный человеком либо оценка по истории.
            //
            // Раньше здесь стоял выход, если объём не задан. А задать его было
            // негде — поле не запрашивалось ни на одном экране, — и напоминание
            // не срабатывало вообще ни у кого. Теперь поле появилось в формах,
            // но у всех, кто завёл машину раньше, оно пустое, и оценка нужна.
            val tankCapacity = car.tankCapacity ?: estimateTankCapacity(fuelExpenses)
                ?: return@forEachIndexed

            // Находим последнюю полную заправку
            val lastFullTank = fuelExpenses.firstOrNull { it.isFullTank } ?: return@forEachIndexed
            val litersAddedAtLastFull = lastFullTank.fuelLiters ?: return@forEachIndexed

            // Суммируем все заправки ПОСЛЕ последней полной (частичные дозаливки)
            val partialAfterFull = fuelExpenses
                .filter { it.date > lastFullTank.date && !it.isFullTank }
                .sumOf { it.fuelLiters ?: 0.0 }

            // Пробег с последней полной заправки
            val kmSinceFull = car.currentOdometer - lastFullTank.odometer

            // Средний расход (л/100км) из истории
            val avgConsumption = calculateAvgConsumption(fuelExpenses) ?: 10.0

            // Расчётный остаток
            val litersConsumed = kmSinceFull * avgConsumption / 100.0
            val estimatedRemaining = (tankCapacity + partialAfterFull - litersConsumed)
                .coerceIn(0.0, tankCapacity)

            val threshold = maxOf(LOW_FUEL_THRESHOLD_LITERS, tankCapacity * LOW_FUEL_THRESHOLD_PCT)

            if (estimatedRemaining < threshold) {
                NotificationHelper.sendFuelNotification(
                    context = applicationContext,
                    notificationId = 1000 + index,
                    carName = "${car.brand} ${car.model}",
                    estimatedLiters = estimatedRemaining,
                    tankCapacity = tankCapacity
                )
            }
        }

        return Result.success()
    }

    /**
     * Оценка объёма бака по истории: самая большая заправка «до полного».
     *
     * Величина заведомо заниженная — человек редко приезжает на совсем пустом
     * баке. Для напоминания это приемлемо: порог сработает чуть раньше, чем
     * нужно, и это лучше, чем не сработать никогда.
     *
     * Меньше двух полных заправок — оценивать не на чем, и мы честно молчим.
     */
    private fun estimateTankCapacity(fuelExpenses: List<com.aggin.carcost.data.local.database.entities.Expense>): Double? {
        val fullTanks = fuelExpenses.filter { it.isFullTank && (it.fuelLiters ?: 0.0) > 0.0 }
        if (fullTanks.size < 2) return null
        return fullTanks.mapNotNull { it.fuelLiters }.maxOrNull()
    }

    private fun calculateAvgConsumption(expenses: List<com.aggin.carcost.data.local.database.entities.Expense>): Double? {
        val fullTanks = expenses.filter { it.isFullTank && it.fuelLiters != null }
            .sortedBy { it.date }
        if (fullTanks.size < 2) return null

        var totalLiters = 0.0
        var totalKm = 0

        for (i in 1 until fullTanks.size) {
            val km = fullTanks[i].odometer - fullTanks[i - 1].odometer
            val liters = fullTanks[i].fuelLiters!!
            if (km > 0 && liters > 0) {
                val consumption = liters * 100.0 / km
                if (consumption in 2.0..30.0) {
                    totalLiters += liters
                    totalKm += km
                }
            }
        }
        return if (totalKm > 0) totalLiters * 100.0 / totalKm else null
    }
}
