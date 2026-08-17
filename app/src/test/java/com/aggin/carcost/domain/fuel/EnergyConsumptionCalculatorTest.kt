package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Расход электричества.
 *
 * Проверяется то же, что и у топлива, и по той же причине: ошибка в такой
 * формуле не видна глазом — цифра выглядит правдоподобно и оказывается неверной
 * только при сверке вручную.
 */
class EnergyConsumptionCalculatorTest {

    private var seq = 0

    private fun charge(
        odometer: Int,
        kwh: Double?,
        isFull: Boolean = true,
        day: Int = seq++
    ) = Expense(
        id = "c$odometer-$day",
        carId = "car",
        category = ExpenseCategory.CHARGING,
        amount = (kwh ?: 0.0) * 8,
        date = day * 24L * 60 * 60 * 1000,
        odometer = odometer,
        energyKwh = kwh,
        isFullTank = isFull
    )

    @Test
    fun `расход считается между полными зарядками, а не за всю историю`() {
        // Первая зарядка израсходована ДО начала отсчёта и в расчёт не идёт.
        // Между 10 000 и 10 300 проехали 300 км на 54 кВт·ч → 18 кВт·ч/100 км.
        val expenses = listOf(
            charge(odometer = 10_000, kwh = 54.0),
            charge(odometer = 10_300, kwh = 54.0)
        )

        assertEquals(18.0, EnergyConsumptionCalculator.average(expenses)!!, 0.01)
    }

    @Test
    fun `одной зарядки недостаточно — отрезка ещё нет`() {
        assertNull(EnergyConsumptionCalculator.average(listOf(charge(10_000, 50.0))))
    }

    @Test
    fun `неполные зарядки в расчёт не берутся`() {
        // Остаток батареи при неполной зарядке неизвестен, считать по ней нечего
        val expenses = listOf(
            charge(odometer = 10_000, kwh = 50.0, isFull = false),
            charge(odometer = 10_300, kwh = 50.0, isFull = false)
        )

        assertNull(EnergyConsumptionCalculator.average(expenses))
    }

    @Test
    fun `неправдоподобный отрезок отбрасывается`() {
        // Опечатка в одометре: 10 км вместо 10 000 даёт сотни кВт·ч на сотню.
        // Такая точка портит среднее сильнее, чем её отсутствие.
        val expenses = listOf(
            charge(odometer = 10_000, kwh = 54.0),
            charge(odometer = 10_010, kwh = 54.0),
            charge(odometer = 10_310, kwh = 54.0)
        )

        val average = EnergyConsumptionCalculator.average(expenses)!!
        assertEquals(18.0, average, 0.01)
    }

    @Test
    fun `движение назад по одометру не создаёт отрицательный отрезок`() {
        val expenses = listOf(
            charge(odometer = 10_300, kwh = 54.0),
            charge(odometer = 10_000, kwh = 54.0)
        )

        // Сортировка по дате оставляет записи в этом порядке, разница отрицательна
        assertNull(EnergyConsumptionCalculator.average(expenses))
    }

    @Test
    fun `заправки не попадают в расчёт электричества`() {
        val expenses = listOf(
            charge(odometer = 10_000, kwh = 54.0),
            Expense(
                id = "fuel",
                carId = "car",
                category = ExpenseCategory.FUEL,
                amount = 3000.0,
                date = 5 * 24L * 60 * 60 * 1000,
                odometer = 10_150,
                fuelLiters = 40.0,
                isFullTank = true
            ),
            charge(odometer = 10_300, kwh = 54.0, day = 9)
        )

        // 300 км на 54 кВт·ч — заправка посередине к электричеству отношения не имеет
        assertEquals(18.0, EnergyConsumptionCalculator.average(expenses)!!, 0.01)
    }

    @Test
    fun `покрытый пробег отражает только зачтённые отрезки`() {
        val expenses = listOf(
            charge(odometer = 10_000, kwh = 54.0),
            charge(odometer = 10_300, kwh = 54.0)
        )

        assertEquals(300, EnergyConsumptionCalculator.coveredKm(expenses))
        assertTrue(EnergyConsumptionCalculator.segments(expenses).size == 1)
    }
}
