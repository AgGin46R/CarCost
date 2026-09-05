package com.aggin.carcost.domain.year

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.GpsTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Итоги года.
 *
 * Страницу итогов человек показывает другим — отправляет в чат, выкладывает.
 * Неверное число здесь расходится дальше и живёт дольше, чем ошибка на обычном
 * экране, и исправить его задним числом уже нельзя.
 *
 * Отдельно закреплено то, что при нехватке данных страница не выдумывает:
 * годовой пробег из записей без одометра, среднюю цену литра без литров,
 * сравнение с годом, которого в данных нет.
 */
class YearSummaryCalculatorTest {

    private fun date(year: Int, month: Int, day: Int = 10): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis

    private fun expense(
        year: Int,
        month: Int = Calendar.MARCH,
        amount: Double = 1000.0,
        odometer: Int = 0,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        liters: Double? = null,
        location: String? = null
    ) = Expense(
        carId = "car",
        category = category,
        amount = amount,
        date = date(year, month),
        odometer = odometer,
        fuelLiters = liters,
        location = location
    )

    // ── Деньги и пробег ─────────────────────────────────────────────────────

    @Test
    fun `считается только запрошенный год`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, amount = 1000.0),
                expense(2025, amount = 500.0),
                expense(2024, amount = 9999.0)
            ),
            year = 2025
        )
        assertEquals(2, s.recordCount)
        assertEquals(1500.0, s.totalSpent, 0.01)
    }

    @Test
    fun `пробег за год — разница крайних одометров`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, Calendar.JANUARY, odometer = 40_000),
                expense(2025, Calendar.JUNE, odometer = 52_000),
                expense(2025, Calendar.DECEMBER, odometer = 58_000)
            ),
            year = 2025
        )
        assertEquals(18_000, s.kmDriven)
    }

    @Test
    fun `записи без одометра не превращают годовой пробег во весь пробег машины`() {
        // Ноль в поле одометра означает «не указан». Если его учесть,
        // получится пробег от нуля до текущего — то есть за всю жизнь машины
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, Calendar.JANUARY, odometer = 0),
                expense(2025, Calendar.JUNE, odometer = 52_000),
                expense(2025, Calendar.DECEMBER, odometer = 58_000)
            ),
            year = 2025
        )
        assertEquals(6_000, s.kmDriven)
    }

    @Test
    fun `одной записи с одометром для годового пробега мало`() {
        val s = YearSummaryCalculator.calculate(
            listOf(expense(2025, odometer = 50_000), expense(2025, odometer = 0)),
            year = 2025
        )
        assertNull(s.kmDriven)
        assertNull(s.costPerKm)
    }

    @Test
    fun `стоимость километра считается от годового пробега`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, Calendar.JANUARY, amount = 5000.0, odometer = 10_000),
                expense(2025, Calendar.DECEMBER, amount = 5000.0, odometer = 20_000)
            ),
            year = 2025
        )
        assertEquals(1.0, s.costPerKm!!, 0.001)
    }

    // ── Месяцы ──────────────────────────────────────────────────────────────

    @Test
    fun `самый дорогой и самый тихий месяцы`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, Calendar.MARCH, amount = 1000.0),
                expense(2025, Calendar.JULY, amount = 8000.0),
                expense(2025, Calendar.SEPTEMBER, amount = 200.0)
            ),
            year = 2025
        )
        assertEquals(Calendar.JULY, s.busiestMonth!!.month)
        assertEquals(Calendar.SEPTEMBER, s.quietestMonth!!.month)
    }

    @Test
    fun `при одном месяце тихий месяц не показывается`() {
        // Иначе он совпал бы с самым дорогим, и страница сообщала бы одно и то
        // же дважды
        val s = YearSummaryCalculator.calculate(
            listOf(expense(2025, Calendar.MARCH), expense(2025, Calendar.MARCH)),
            year = 2025
        )
        assertEquals(Calendar.MARCH, s.busiestMonth!!.month)
        assertNull(s.quietestMonth)
    }

    // ── Топливо ─────────────────────────────────────────────────────────────

    @Test
    fun `средняя цена литра считается по объёму, а не как среднее из цен`() {
        // 10 л по 50 и 100 л по 60. Среднее из цен дало бы 55, по объёму — 59,09
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, amount = 500.0, category = ExpenseCategory.FUEL, liters = 10.0),
                expense(2025, amount = 6000.0, category = ExpenseCategory.FUEL, liters = 100.0)
            ),
            year = 2025
        )
        assertEquals(59.09, s.averagePricePerLiter!!, 0.01)
        assertEquals(110.0, s.liters, 0.01)
        assertEquals(2, s.fillUps)
    }

    @Test
    fun `без литров средняя цена не выдумывается`() {
        val s = YearSummaryCalculator.calculate(
            listOf(expense(2025, amount = 3000.0, category = ExpenseCategory.FUEL)),
            year = 2025
        )
        assertNull(s.averagePricePerLiter)
    }

    @Test
    fun `любимая заправка — та, на которую ушло больше литров`() {
        // По числу визитов победила бы «Роснефть» — три заправки против одной
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, category = ExpenseCategory.FUEL, liters = 60.0, location = "Лукойл на Ленина"),
                expense(2025, category = ExpenseCategory.FUEL, liters = 10.0, location = "Роснефть"),
                expense(2025, category = ExpenseCategory.FUEL, liters = 10.0, location = "Роснефть"),
                expense(2025, category = ExpenseCategory.FUEL, liters = 10.0, location = "Роснефть")
            ),
            year = 2025
        )
        assertEquals("Лукойл", s.favouriteStation)
    }

    @Test
    fun `точки одной сети сводятся в одну заправку`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, category = ExpenseCategory.FUEL, liters = 20.0, location = "лукойл"),
                expense(2025, category = ExpenseCategory.FUEL, liters = 20.0, location = "АЗС Лукойл №42"),
                expense(2025, category = ExpenseCategory.FUEL, liters = 35.0, location = "Роснефть")
            ),
            year = 2025
        )
        assertEquals("Лукойл", s.favouriteStation)
    }

    // ── Поездки ─────────────────────────────────────────────────────────────

    @Test
    fun `самая длинная поездка берётся только из нужного года`() {
        val s = YearSummaryCalculator.calculate(
            listOf(expense(2025)),
            trips = listOf(
                GpsTrip(carId = "car", startTime = date(2025, Calendar.MAY), distanceKm = 320.0),
                GpsTrip(carId = "car", startTime = date(2024, Calendar.MAY), distanceKm = 900.0)
            ),
            year = 2025
        )
        assertEquals(320.0, s.longestTripKm!!, 0.01)
        assertEquals(1, s.tripsRecorded)
    }

    @Test
    fun `без поездок раздел пустой, а не нулевой`() {
        val s = YearSummaryCalculator.calculate(listOf(expense(2025)), year = 2025)
        assertNull(s.longestTripKm)
        assertEquals(0, s.tripsRecorded)
    }

    // ── Сравнение с прошлым годом ───────────────────────────────────────────

    @Test
    fun `сравнение с прошлым годом`() {
        val s = YearSummaryCalculator.calculate(
            listOf(
                expense(2025, amount = 12_000.0),
                expense(2024, amount = 10_000.0)
            ),
            year = 2025
        )
        assertEquals(10_000.0, s.previousYearSpent!!, 0.01)
        assertEquals(0.2, s.changeVsPrevious!!, 0.001)
    }

    @Test
    fun `без прошлого года сравнения нет`() {
        val s = YearSummaryCalculator.calculate(listOf(expense(2025)), year = 2025)
        assertNull(s.previousYearSpent)
        assertNull(s.changeVsPrevious)
    }

    // ── Достаточно ли данных ────────────────────────────────────────────────

    @Test
    fun `год с парой записей итогами не считается`() {
        val s = YearSummaryCalculator.calculate(
            listOf(expense(2025), expense(2025)),
            year = 2025
        )
        assertFalse(s.hasEnoughData)
    }

    @Test
    fun `пяти записей уже достаточно`() {
        val s = YearSummaryCalculator.calculate(List(5) { expense(2025) }, year = 2025)
        assertTrue(s.hasEnoughData)
    }
}
