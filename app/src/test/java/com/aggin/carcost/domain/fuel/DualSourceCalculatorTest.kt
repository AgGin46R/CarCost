package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Стоимость километра у машины с двумя источниками энергии.
 *
 * Здесь легче всего ошибиться незаметно: если включить в расчёт первую запись,
 * цифра завышается, и понять это по виду результата нельзя — он выглядит
 * правдоподобно. Поэтому проверяется явно.
 */
class DualSourceCalculatorTest {

    private var seq = 0

    private fun fuel(odometer: Int, amount: Double, day: Int = seq++) = Expense(
        id = "f$odometer-$day",
        carId = "car",
        category = ExpenseCategory.FUEL,
        amount = amount,
        date = day * 24L * 60 * 60 * 1000,
        odometer = odometer,
        fuelLiters = amount / 55,
        isFullTank = true
    )

    private fun charge(odometer: Int, amount: Double, day: Int = seq++) = Expense(
        id = "c$odometer-$day",
        carId = "car",
        category = ExpenseCategory.CHARGING,
        amount = amount,
        date = day * 24L * 60 * 60 * 1000,
        odometer = odometer,
        energyKwh = amount / 8,
        isFullTank = true
    )

    @Test
    fun `первая запись задаёт начало отсчёта и в деньги не входит`() {
        // Первая заправка оплачивает путь ДО периода. Считаем 1000 км и 5000 ₽,
        // потраченных на этом пути, а не 8000 ₽ вместе с первой.
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 3000.0),
            fuel(odometer = 11_000, amount = 5000.0)
        )

        val result = DualSourceCalculator.costPerKm(expenses)!!
        assertEquals(1000, result.km)
        assertEquals(5.0, result.costPerKm, 0.001)
    }

    @Test
    fun `оба источника складываются в стоимость километра`() {
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 3000.0),
            charge(odometer = 10_500, amount = 800.0),
            fuel(odometer = 11_000, amount = 2200.0)
        )

        val result = DualSourceCalculator.costPerKm(expenses)!!
        assertEquals(1000, result.km)
        assertEquals(3000.0, result.fuelCost + result.energyCost, 0.001)
        assertEquals(3.0, result.costPerKm, 0.001)
    }

    @Test
    fun `доля электричества считается по деньгам`() {
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 1000.0),
            charge(odometer = 10_500, amount = 750.0),
            fuel(odometer = 11_000, amount = 250.0)
        )

        val result = DualSourceCalculator.costPerKm(expenses)!!
        // 750 из 1000 потраченных внутри периода — три четверти
        assertEquals(0.75, result.electricCostShare, 0.001)
    }

    @Test
    fun `только электричество — доля равна единице`() {
        val expenses = listOf(
            charge(odometer = 10_000, amount = 500.0),
            charge(odometer = 10_400, amount = 400.0)
        )

        val result = DualSourceCalculator.costPerKm(expenses)!!
        assertEquals(1.0, result.electricCostShare, 0.001)
        assertEquals(1.0, result.costPerKm, 0.001)
    }

    @Test
    fun `только бензин — доля равна нулю`() {
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 3000.0),
            fuel(odometer = 10_600, amount = 3000.0)
        )

        assertEquals(0.0, DualSourceCalculator.costPerKm(expenses)!!.electricCostShare, 0.001)
    }

    @Test
    fun `одной записи недостаточно`() {
        assertNull(DualSourceCalculator.costPerKm(listOf(fuel(10_000, 3000.0))))
    }

    @Test
    fun `нулевой пробег не даёт делить на ноль`() {
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 3000.0),
            fuel(odometer = 10_000, amount = 3000.0)
        )

        assertNull(DualSourceCalculator.costPerKm(expenses))
    }

    @Test
    fun `посторонние расходы не влияют на стоимость километра`() {
        // Мойка и страховка к пройденному пути отношения не имеют, а одометр у
        // них может быть записан как угодно
        val expenses = listOf(
            fuel(odometer = 10_000, amount = 3000.0),
            Expense(
                id = "wash",
                carId = "car",
                category = ExpenseCategory.WASH,
                amount = 900.0,
                date = 5 * 24L * 60 * 60 * 1000,
                odometer = 99_999
            ),
            fuel(odometer = 11_000, amount = 5000.0, day = 9)
        )

        val result = DualSourceCalculator.costPerKm(expenses)!!
        assertEquals(1000, result.km)
        assertEquals(5.0, result.costPerKm, 0.001)
    }
}
