package com.aggin.carcost.domain.gamification

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.domain.fuel.EnergyConsumptionCalculator
import com.aggin.carcost.domain.fuel.FuelConsumptionCalculator
import java.util.Calendar

/**
 * Единый расчёт прогресса достижений.
 *
 * Появился, потому что счёт вёлся в двух местах и по-разному. Экран считал
 * записи по всем машинам сразу, а разблокировку проверяли по одной — человек
 * с тремя машинами видел «10 из 10» под закрытым значком и справедливо считал
 * это поломкой. Теперь у обеих сторон один источник.
 *
 * Заодно исчез третий экземпляр формулы расхода: и экран, и проверяльщик
 * считали литры на сотню сами, оба без проверки «до полного бака» — то есть
 * заведомо неверно. Здесь используется общий [FuelConsumptionCalculator].
 */
object AchievementProgressCalculator {

    /** Сколько месяцев подряд нужно ездить экономно */
    const val ECO_MONTHS_REQUIRED = 3

    /**
     * Насколько ниже собственного среднего должен быть расход, чтобы месяц
     * засчитался. Пять процентов — чтобы случайные колебания не выдавали
     * достижение за экономичную езду.
     */
    private const val ECO_MARGIN = 0.95

    /** Все расходы человека по всем его машинам */
    fun totalExpenseCount(allExpenses: List<Expense>): Int = allExpenses.size

    fun fuelCount(allExpenses: List<Expense>): Int =
        allExpenses.count { it.category == ExpenseCategory.FUEL }

    fun maintenanceCount(allExpenses: List<Expense>): Int =
        allExpenses.count { it.category == ExpenseCategory.MAINTENANCE && it.serviceType != null }

    fun receiptPhotoCount(allExpenses: List<Expense>): Int =
        allExpenses.count { it.receiptPhotoUri != null }

    /**
     * Сколько последних завершённых месяцев подряд расход был ниже собственного
     * среднего. Значение 0..[ECO_MONTHS_REQUIRED].
     *
     * Сравнение с собственным средним, а не с числом 8 л/100 км: восьмёрка
     * ничего не значит ни для внедорожника, ни для малолитражки, ни тем более
     * для электромобиля. «Ниже среднего» — то, что и обещает описание.
     */
    fun ecoMonths(allExpenses: List<Expense>): Int {
        // Топливо и электричество считаются отдельными рядами и не смешиваются:
        // литры и киловатт-часы несопоставимы, и усреднять их вместе — получить
        // число, не значащее ничего. У гибрида берём лучший из двух рядов.
        val fuelStreak = streakBelowOwnAverage(
            FuelConsumptionCalculator.segments(allExpenses).map { it.date to it.consumption }
        )
        val energyStreak = streakBelowOwnAverage(
            EnergyConsumptionCalculator.segments(allExpenses).map { it.date to it.consumption }
        )
        return maxOf(fuelStreak, energyStreak)
    }

    /**
     * Сколько последних завершённых месяцев подряд среднее по ряду было ниже
     * собственного среднего этого ряда.
     */
    private fun streakBelowOwnAverage(points: List<Pair<Long, Double>>): Int {
        if (points.isEmpty()) return 0

        val byMonth = mutableMapOf<Int, MutableList<Double>>()
        points.forEach { (date, value) ->
            val cal = Calendar.getInstance().apply { timeInMillis = date }
            val key = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            byMonth.getOrPut(key) { mutableListOf() }.add(value)
        }

        val overall = points.map { it.second }.average()
        if (overall <= 0.0) return 0
        val threshold = overall * ECO_MARGIN

        // Текущий месяц не в счёт: он ещё не закончился, и судить по нему рано
        val current = Calendar.getInstance()
            .let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

        var streak = 0
        for (monthIndex in byMonth.keys.sortedDescending()) {
            if (monthIndex >= current) continue
            if (byMonth.getValue(monthIndex).average() < threshold) streak++ else break
            if (streak >= ECO_MONTHS_REQUIRED) break
        }
        return streak
    }
}
