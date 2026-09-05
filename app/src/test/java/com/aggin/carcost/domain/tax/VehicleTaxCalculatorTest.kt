package com.aggin.carcost.domain.tax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Расчёт транспортного налога.
 *
 * Приложение называет человеку сумму, которую он собирается заплатить
 * государству. Ошибка здесь не падает и ничем себя не выдаёт — просто цифра
 * расходится с квитанцией, и доверия к остальным расчётам больше нет.
 *
 * Отдельно закреплено правило пятнадцатого числа: оно даёт разницу в целый
 * месяц налога и выглядит достаточно странно, чтобы его «упростили» при
 * следующей правке.
 */
class VehicleTaxCalculatorTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis

    // ── Базовые ставки ──────────────────────────────────────────────────────

    @Test
    fun `ставка растёт ступенями по мощности`() {
        assertEquals(2.5, VehicleTaxCalculator.baseRate(100), 0.001)
        assertEquals(3.5, VehicleTaxCalculator.baseRate(101), 0.001)
        assertEquals(3.5, VehicleTaxCalculator.baseRate(150), 0.001)
        assertEquals(5.0, VehicleTaxCalculator.baseRate(151), 0.001)
        assertEquals(7.5, VehicleTaxCalculator.baseRate(201), 0.001)
        assertEquals(15.0, VehicleTaxCalculator.baseRate(300), 0.001)
    }

    @Test
    fun `у мотоцикла своя шкала`() {
        assertEquals(1.0, VehicleTaxCalculator.baseRate(20, isMotorcycle = true), 0.001)
        assertEquals(2.0, VehicleTaxCalculator.baseRate(35, isMotorcycle = true), 0.001)
        assertEquals(5.0, VehicleTaxCalculator.baseRate(36, isMotorcycle = true), 0.001)
        // Та же мощность у автомобиля стоит дороже
        assertEquals(2.5, VehicleTaxCalculator.baseRate(36), 0.001)
    }

    // ── Сумма ───────────────────────────────────────────────────────────────

    @Test
    fun `налог за полный год по базовой ставке`() {
        // 106 л.с. попадает в ступень до 150 — ставка 3,5
        assertEquals(371.0, VehicleTaxCalculator.annualTax(106)!!, 0.01)
    }

    @Test
    fun `ставка владельца заменяет базовую`() {
        assertEquals(2544.0, VehicleTaxCalculator.annualTax(106, ratePerHp = 24.0)!!, 0.01)
    }

    @Test
    fun `неполный год считается по месяцам`() {
        // Полгода владения — половина налога
        assertEquals(
            1272.0,
            VehicleTaxCalculator.annualTax(106, ratePerHp = 24.0, ownedMonths = 6)!!,
            0.01
        )
    }

    @Test
    fun `без мощности сумма не выдумывается`() {
        assertNull(VehicleTaxCalculator.annualTax(null))
        assertNull(VehicleTaxCalculator.annualTax(0))
    }

    @Test
    fun `нулевая ставка не обнуляет налог, а возвращает к базовой`() {
        // Ноль в поле ставки — это «не заполнено», а не «налога нет»
        assertEquals(371.0, VehicleTaxCalculator.annualTax(106, ratePerHp = 0.0)!!, 0.01)
    }

    // ── Месяцы владения ─────────────────────────────────────────────────────

    @Test
    fun `машина, купленная в прошлые годы, числится весь год`() {
        assertEquals(12, VehicleTaxCalculator.ownedMonthsIn(date(2020, Calendar.MARCH, 20), 2025))
    }

    @Test
    fun `машина, купленная позже расчётного года, налогом не облагается`() {
        assertEquals(0, VehicleTaxCalculator.ownedMonthsIn(date(2026, Calendar.MARCH, 20), 2025))
    }

    @Test
    fun `покупка до пятнадцатого числа делает месяц полным`() {
        // Куплена 15 марта: март считается, остаётся 10 месяцев
        assertEquals(10, VehicleTaxCalculator.ownedMonthsIn(date(2025, Calendar.MARCH, 15), 2025))
    }

    @Test
    fun `покупка после пятнадцатого числа месяц не засчитывает`() {
        // Куплена 16 марта: март не считается, остаётся 9 месяцев
        assertEquals(9, VehicleTaxCalculator.ownedMonthsIn(date(2025, Calendar.MARCH, 16), 2025))
    }

    @Test
    fun `покупка в январе даёт полный год`() {
        assertEquals(12, VehicleTaxCalculator.ownedMonthsIn(date(2025, Calendar.JANUARY, 10), 2025))
    }

    @Test
    fun `покупка в конце декабря не даёт ни одного месяца`() {
        assertEquals(0, VehicleTaxCalculator.ownedMonthsIn(date(2025, Calendar.DECEMBER, 20), 2025))
    }

    @Test
    fun `покупка в начале декабря даёт один месяц`() {
        assertEquals(1, VehicleTaxCalculator.ownedMonthsIn(date(2025, Calendar.DECEMBER, 5), 2025))
    }

    // ── Срок и год ──────────────────────────────────────────────────────────

    @Test
    fun `срок уплаты — первое декабря следующего года`() {
        val due = Calendar.getInstance().apply {
            timeInMillis = VehicleTaxCalculator.dueDate(2025)
        }
        assertEquals(2026, due.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, due.get(Calendar.MONTH))
        assertEquals(1, due.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `до декабря актуален прошлый год`() {
        assertEquals(2024, VehicleTaxCalculator.currentTaxYear(date(2025, Calendar.NOVEMBER, 10)))
        assertEquals(2024, VehicleTaxCalculator.currentTaxYear(date(2025, Calendar.JANUARY, 3)))
    }

    @Test
    fun `в декабре расчётным становится текущий год`() {
        assertEquals(2025, VehicleTaxCalculator.currentTaxYear(date(2025, Calendar.DECEMBER, 2)))
    }
}
