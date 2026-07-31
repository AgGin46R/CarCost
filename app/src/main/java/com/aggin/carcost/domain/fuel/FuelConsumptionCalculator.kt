package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory

/**
 * Расход топлива методом «от полного бака до полного».
 *
 * Единственно корректный способ посчитать расход по чекам: сколько литров
 * влезло между двумя полными баками, столько и сожгли на пройденном между ними
 * расстоянии. Неполные заправки в расчёт не годятся — неизвестно, сколько
 * оставалось в баке.
 *
 * Раньше экран аналитики считал иначе: все залитые за историю литры делились на
 * весь пробег с момента покупки. Числитель и знаменатель брались из разных
 * периодов, и у любого, кто начал вести учёт не с первого дня владения,
 * получалось что-то вроде 1 л/100 км. При этом в карточке автомобиля лежал
 * правильный расчёт — то есть приложение показывало два разных средних расхода
 * для одной машины. Этот файл — общий источник для обоих экранов.
 */
object FuelConsumptionCalculator {

    /**
     * Правдоподобный диапазон, л/100 км. Всё за его пределами — почти наверняка
     * опечатка в одометре или литрах, и попадание такой точки в среднее портит
     * картину сильнее, чем её отсутствие.
     */
    private val PLAUSIBLE_RANGE = 2.0..30.0

    /** Одна пара «полный бак → полный бак» */
    data class Segment(
        val date: Long,
        val km: Int,
        val liters: Double
    ) {
        val consumption: Double get() = liters * 100.0 / km
    }

    /**
     * Отбирает заправки, пригодные для расчёта: только топливо, только полный
     * бак, с указанными литрами и ненулевым одометром.
     *
     * Сортировка по дате, а не по одометру: опечатка в пробеге при сортировке по
     * одометру переставляет запись в другое место истории и портит сразу два
     * соседних отрезка.
     *
     * @param fuelType если задан — считаем только по этому топливу, чтобы не
     *        смешивать бензин с газом у машин с ГБО
     */
    private fun usableFillUps(expenses: List<Expense>, fuelType: String?): List<Expense> =
        expenses
            .filter {
                it.category == ExpenseCategory.FUEL &&
                    it.isFullTank &&
                    (it.fuelLiters ?: 0.0) > 0.0 &&
                    it.odometer > 0 &&
                    (fuelType == null || it.fuelType == null || it.fuelType.equals(fuelType, ignoreCase = true))
            }
            .sortedBy { it.date }

    /** Отрезки между соседними полными баками, с отбраковкой неправдоподобных */
    fun segments(expenses: List<Expense>, fuelType: String? = null): List<Segment> {
        val fillUps = usableFillUps(expenses, fuelType)
        if (fillUps.size < 2) return emptyList()

        return (1 until fillUps.size).mapNotNull { i ->
            val km = fillUps[i].odometer - fillUps[i - 1].odometer
            val liters = fillUps[i].fuelLiters ?: return@mapNotNull null
            if (km <= 0) return@mapNotNull null

            val segment = Segment(date = fillUps[i].date, km = km, liters = liters)
            segment.takeIf { it.consumption in PLAUSIBLE_RANGE }
        }
    }

    /**
     * Средний расход, л/100 км. `null`, если данных не хватает — это честнее
     * нуля или выдуманной цифры.
     *
     * Считается по суммам, а не как среднее из средних: длинный отрезок должен
     * весить больше короткого.
     */
    fun average(expenses: List<Expense>, fuelType: String? = null): Double? {
        val segments = segments(expenses, fuelType)
        if (segments.isEmpty()) return null

        val totalKm = segments.sumOf { it.km }
        val totalLiters = segments.sumOf { it.liters }
        return if (totalKm > 0) totalLiters * 100.0 / totalKm else null
    }

    /** Сколько километров покрыто расчётом — нужно, чтобы честно сказать «мало данных» */
    fun coveredKm(expenses: List<Expense>, fuelType: String? = null): Int =
        segments(expenses, fuelType).sumOf { it.km }
}
