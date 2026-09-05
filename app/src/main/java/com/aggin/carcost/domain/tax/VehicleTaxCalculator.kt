package com.aggin.carcost.domain.tax

import java.util.Calendar

/**
 * Транспортный налог.
 *
 * **Про ставки — важное.** В России 85 регионов, ставки различаются в разы и
 * меняются каждый год. Зашитая в приложение полная таблица означала бы, что
 * приложение уверенно называет неверную сумму каждый раз, когда регион поднял
 * ставку, а обновления ещё не вышло. Поэтому здесь лежат только базовые ставки
 * из статьи 361 Налогового кодекса, а человек может ввести свою — и рядом с
 * расчётом всегда написано, по какой ставке считали.
 *
 * Повышающий коэффициент для дорогих машин не считается: он опирается на
 * перечень Минпромторга, который тоже пересматривается ежегодно.
 */
object VehicleTaxCalculator {

    /**
     * Базовые ставки в рублях за лошадиную силу — статья 361 НК РФ.
     *
     * Регион вправе увеличить или уменьшить их не более чем в десять раз,
     * поэтому фактическая ставка почти всегда отличается. Это отправная точка,
     * а не ответ.
     */
    private val BASE_RATES_CAR = listOf(
        100 to 2.5,
        150 to 3.5,
        200 to 5.0,
        250 to 7.5,
        Int.MAX_VALUE to 15.0
    )

    private val BASE_RATES_MOTORCYCLE = listOf(
        20 to 1.0,
        35 to 2.0,
        Int.MAX_VALUE to 5.0
    )

    /**
     * Базовая ставка для такой мощности.
     *
     * @param powerHp мощность в лошадиных силах
     * @param isMotorcycle у мотоциклов своя шкала и она заметно ниже
     */
    fun baseRate(powerHp: Int, isMotorcycle: Boolean = false): Double {
        val table = if (isMotorcycle) BASE_RATES_MOTORCYCLE else BASE_RATES_CAR
        return table.first { powerHp <= it.first }.second
    }

    /**
     * Налог за календарный год.
     *
     * @param powerHp мощность двигателя
     * @param ratePerHp ставка за силу. Пусто — берётся базовая
     * @param ownedMonths сколько месяцев года машина числилась за владельцем.
     *   По умолчанию весь год
     *
     * @return сумма в рублях, либо null если мощность не указана
     */
    fun annualTax(
        powerHp: Int?,
        ratePerHp: Double? = null,
        ownedMonths: Int = 12,
        isMotorcycle: Boolean = false
    ): Double? {
        if (powerHp == null || powerHp <= 0) return null
        val rate = ratePerHp?.takeIf { it > 0 } ?: baseRate(powerHp, isMotorcycle)
        val months = ownedMonths.coerceIn(0, 12)
        return powerHp * rate * months / 12.0
    }

    /**
     * Сколько месяцев года машина была в собственности.
     *
     * Месяц покупки считается полным, если машина поставлена на учёт до
     * пятнадцатого числа включительно, — так написано в пункте 3 статьи 362 НК.
     * Правило вводит разницу в целый месяц налога, поэтому упрощать его до
     * «считаем с месяца покупки» нельзя.
     *
     * @param purchaseDate дата покупки
     * @param year год, за который считаем
     */
    fun ownedMonthsIn(purchaseDate: Long, year: Int): Int {
        val purchase = Calendar.getInstance().apply { timeInMillis = purchaseDate }
        val purchaseYear = purchase.get(Calendar.YEAR)

        return when {
            // Куплена позже расчётного года — налога за него нет
            purchaseYear > year -> 0
            // Владел весь год
            purchaseYear < year -> 12
            else -> {
                val month = purchase.get(Calendar.MONTH)          // 0..11
                val day = purchase.get(Calendar.DAY_OF_MONTH)
                val fullMonths = 12 - month
                if (day <= 15) fullMonths else fullMonths - 1
            }
        }.coerceIn(0, 12)
    }

    /**
     * Срок уплаты — первое декабря следующего года.
     *
     * @return время начала первого декабря
     */
    fun dueDate(taxYear: Int): Long = Calendar.getInstance().apply {
        clear()
        set(taxYear + 1, Calendar.DECEMBER, 1, 0, 0, 0)
    }.timeInMillis

    /**
     * Год, за который налог считается сейчас.
     *
     * Уведомления рассылают осенью за прошедший год, поэтому до декабря
     * актуален прошлый год, а после уплаты — уже текущий.
     */
    fun currentTaxYear(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        return if (cal.get(Calendar.MONTH) >= Calendar.DECEMBER) year else year - 1
    }
}
