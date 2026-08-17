package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory

/**
 * Расход электричества методом «от полного заряда до полного».
 *
 * Устроен намеренно так же, как [FuelConsumptionCalculator]: сколько киловатт-часов
 * вошло между двумя полными зарядками, столько и потрачено на пройденном между
 * ними расстоянии. Неполные зарядки в расчёт не годятся — неизвестно, сколько
 * оставалось в батарее.
 *
 * Отдельный объект, а не параметр к топливному: величины, единицы и пределы
 * правдоподобия разные, а попытка обслужить оба случая одним кодом с флажком
 * даёт функцию, которую нельзя прочитать, не держа в голове оба варианта.
 */
object EnergyConsumptionCalculator {

    /**
     * Правдоподобный диапазон, кВт·ч/100 км.
     *
     * Нижняя граница — очень экономичная езда на лёгкой машине летом; верхняя —
     * крупный внедорожник зимой на трассе. Всё за пределами почти наверняка
     * опечатка в одометре или киловатт-часах, и такая точка портит среднее
     * сильнее, чем её отсутствие.
     */
    private val PLAUSIBLE_RANGE = 8.0..40.0

    /** Одна пара «полный заряд → полный заряд» */
    data class Segment(
        val date: Long,
        val km: Int,
        val kwh: Double
    ) {
        val consumption: Double get() = kwh * 100.0 / km
    }

    /**
     * Отбирает зарядки, пригодные для расчёта: только зарядка, только до полного,
     * с указанными киловатт-часами и ненулевым одометром.
     *
     * Сортировка по дате, а не по одометру: опечатка в пробеге при сортировке по
     * одометру переставляет запись в другое место истории и портит сразу два
     * соседних отрезка.
     */
    private fun usableCharges(expenses: List<Expense>): List<Expense> =
        expenses
            .filter {
                it.category == ExpenseCategory.CHARGING &&
                    it.isFullTank &&
                    (it.energyKwh ?: 0.0) > 0.0 &&
                    it.odometer > 0
            }
            .sortedBy { it.date }

    /** Отрезки между соседними полными зарядками, с отбраковкой неправдоподобных */
    fun segments(expenses: List<Expense>): List<Segment> {
        val charges = usableCharges(expenses)
        if (charges.size < 2) return emptyList()

        return (1 until charges.size).mapNotNull { i ->
            val km = charges[i].odometer - charges[i - 1].odometer
            val kwh = charges[i].energyKwh ?: return@mapNotNull null
            if (km <= 0) return@mapNotNull null

            val segment = Segment(date = charges[i].date, km = km, kwh = kwh)
            segment.takeIf { it.consumption in PLAUSIBLE_RANGE }
        }
    }

    /**
     * Средний расход, кВт·ч/100 км. `null`, если данных не хватает — это честнее
     * нуля или выдуманной цифры.
     *
     * Считается по суммам, а не как среднее из средних: длинный отрезок должен
     * весить больше короткого.
     */
    fun average(expenses: List<Expense>): Double? {
        val segments = segments(expenses)
        if (segments.isEmpty()) return null

        val totalKm = segments.sumOf { it.km }
        val totalKwh = segments.sumOf { it.kwh }
        return if (totalKm > 0) totalKwh * 100.0 / totalKm else null
    }

    /** Сколько километров покрыто расчётом — нужно, чтобы честно сказать «мало данных» */
    fun coveredKm(expenses: List<Expense>): Int = segments(expenses).sumOf { it.km }
}
