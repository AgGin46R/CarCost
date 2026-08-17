package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory

/**
 * Затраты на движение у машины с двумя источниками энергии.
 *
 * ## Зачем отдельный расчёт
 *
 * У подключаемого гибрида «л/100 км» и «кВт·ч/100 км» по отдельности почти
 * бессмысленны. Обе величины зависят не от прожорливости машины, а от того,
 * какую долю пути проехали на электричестве. Человек, месяц ездивший от розетки,
 * увидит расход бензина 1,5 л/100 км — и это не достижение двигателя, а просто
 * редкие поездки на бензине. Сравнить такое ни с обычной машиной, ни с самим
 * собой месяц назад нельзя.
 *
 * Поэтому главной величиной здесь служит **стоимость километра**: она складывает
 * оба источника и отвечает на вопрос, который человека действительно волнует —
 * во сколько обходится ездить.
 *
 * ## Чего этот расчёт намеренно не делает
 *
 * Не пытается определить, сколько километров проехали на электричестве, а
 * сколько на бензине. По чекам это неизвестно: гибрид переключается между
 * источниками сам, десятки раз за поездку, и никакие данные о заправках этого
 * не восстановят. Доля электротяги ниже считается по **деньгам**, а не по
 * километрам, и названа соответственно.
 */
object DualSourceCalculator {

    /**
     * @param costPerKm    рублей (или другой валюты автомобиля) за километр
     * @param km           пробег, на котором считалось
     * @param fuelCost     потрачено на топливо
     * @param energyCost   потрачено на зарядку
     */
    data class Result(
        val costPerKm: Double,
        val km: Int,
        val fuelCost: Double,
        val energyCost: Double
    ) {
        /**
         * Какая часть денег на движение ушла на электричество, 0..1.
         *
         * Именно денег, а не километров: сколько проехали на каждом источнике,
         * по чекам не узнать.
         */
        val electricCostShare: Double
            get() {
                val total = fuelCost + energyCost
                return if (total > 0) energyCost / total else 0.0
            }
    }

    /**
     * Стоимость километра за период.
     *
     * Пробег берётся как разница одометра между первой и последней записью о
     * движении — заправкой или зарядкой. Не по всем расходам подряд: мойка или
     * страховка к пройденному пути отношения не имеют, а вот их одометр может
     * быть записан как угодно.
     *
     * Возвращает `null`, если считать не на чем: меньше двух записей или нулевой
     * пробег. Честнее выдуманной цифры.
     */
    fun costPerKm(expenses: List<Expense>): Result? {
        val moving = expenses
            .filter {
                (it.category == ExpenseCategory.FUEL || it.category == ExpenseCategory.CHARGING) &&
                    it.odometer > 0
            }
            .sortedBy { it.date }

        if (moving.size < 2) return null

        val km = moving.last().odometer - moving.first().odometer
        if (km <= 0) return null

        // Первая запись задаёт начало отсчёта: её деньги потрачены на путь ДО
        // периода, а не внутри него. Включать их — завышать стоимость километра.
        val counted = moving.drop(1)

        val fuelCost = counted.filter { it.category == ExpenseCategory.FUEL }.sumOf { it.amount }
        val energyCost = counted.filter { it.category == ExpenseCategory.CHARGING }.sumOf { it.amount }
        val total = fuelCost + energyCost
        if (total <= 0.0) return null

        return Result(
            costPerKm = total / km,
            km = km,
            fuelCost = fuelCost,
            energyCost = energyCost
        )
    }
}
