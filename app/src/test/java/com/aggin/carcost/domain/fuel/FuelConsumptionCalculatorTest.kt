package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Расход топлива — первое, что проверяется тестами в этом проекте.
 *
 * Повод конкретный: на экране аналитики расход считался как «все литры за
 * историю / весь пробег с покупки», и у любого, кто начал вести учёт не с
 * первого дня, получалось около 1 л/100 км. Ошибку нашли разбором формул, а не
 * запуском — эти тесты закрывают именно её.
 */
class FuelConsumptionCalculatorTest {

    private var seq = 0

    private fun fill(
        odometer: Int,
        liters: Double?,
        isFullTank: Boolean = true,
        day: Int = seq++,
        fuelType: String? = null
    ) = Expense(
        id = "e$odometer-$day",
        carId = "car",
        category = ExpenseCategory.FUEL,
        amount = (liters ?: 0.0) * 50,
        date = day * 24L * 60 * 60 * 1000,
        odometer = odometer,
        fuelLiters = liters,
        isFullTank = isFullTank,
        fuelType = fuelType
    )

    @Test
    fun `расход считается между полными баками, а не за всю историю`() {
        // Залили 40 л на первой заправке — они сгорели ДО первого замера и в
        // расчёт попадать не должны. Между 10 000 и 10 500 проехали 500 км на 40 л.
        val expenses = listOf(
            fill(odometer = 10_000, liters = 40.0),
            fill(odometer = 10_500, liters = 40.0)
        )

        // 40 л / 500 км = 8 л/100 км
        assertEquals(8.0, FuelConsumptionCalculator.average(expenses)!!, 0.001)
    }

    @Test
    fun `неполные заправки не участвуют в расчёте`() {
        val expenses = listOf(
            fill(odometer = 10_000, liters = 40.0),
            fill(odometer = 10_200, liters = 15.0, isFullTank = false), // долив
            fill(odometer = 10_500, liters = 40.0)
        )

        // Долив игнорируется: считаем те же 40 л на 500 км, а не 55 л
        assertEquals(8.0, FuelConsumptionCalculator.average(expenses)!!, 0.001)
    }

    @Test
    fun `одной заправки не хватает — возвращается null, а не ноль`() {
        assertNull(FuelConsumptionCalculator.average(listOf(fill(10_000, 40.0))))
    }

    @Test
    fun `длинный отрезок весит больше короткого`() {
        val expenses = listOf(
            fill(odometer = 0, liters = 10.0),
            fill(odometer = 100, liters = 10.0),    // 10 л/100км на 100 км
            fill(odometer = 1_100, liters = 60.0)   // 6 л/100км на 1000 км
        )

        // Среднее из средних дало бы 8.0; правильный ответ — по суммам:
        // 70 л на 1100 км = 6.36
        assertEquals(70.0 * 100 / 1100, FuelConsumptionCalculator.average(expenses)!!, 0.001)
    }

    @Test
    fun `неправдоподобные отрезки отбрасываются`() {
        val expenses = listOf(
            fill(odometer = 10_000, liters = 40.0),
            fill(odometer = 10_010, liters = 40.0),  // 400 л/100км — опечатка в одометре
            fill(odometer = 10_510, liters = 40.0)   // 8 л/100км — нормально
        )

        assertEquals(8.0, FuelConsumptionCalculator.average(expenses)!!, 0.001)
    }

    @Test
    fun `заправки с нулевым одометром не ломают расчёт`() {
        val expenses = listOf(
            fill(odometer = 0, liters = 40.0),       // одометр не заполнен
            fill(odometer = 10_000, liters = 40.0),
            fill(odometer = 10_500, liters = 40.0)
        )

        assertEquals(8.0, FuelConsumptionCalculator.average(expenses)!!, 0.001)
    }

    @Test
    fun `разные виды топлива не смешиваются`() {
        val expenses = listOf(
            fill(odometer = 10_000, liters = 40.0, fuelType = "GASOLINE"),
            fill(odometer = 10_500, liters = 40.0, fuelType = "GASOLINE"),
            fill(odometer = 10_700, liters = 30.0, fuelType = "LPG")
        )

        assertEquals(8.0, FuelConsumptionCalculator.average(expenses, fuelType = "GASOLINE")!!, 0.001)
    }

    @Test
    fun `порядок определяется датой, а не одометром`() {
        // Опечатка: во второй по времени заправке одометр меньше, чем в первой.
        // При сортировке по одометру запись уехала бы в начало и испортила два отрезка.
        val expenses = listOf(
            fill(odometer = 10_000, liters = 40.0, day = 1),
            fill(odometer = 1_050, liters = 40.0, day = 2),   // должно было быть 10 050
            fill(odometer = 10_500, liters = 40.0, day = 3)
        )

        // Оба отрезка неправдоподобны и отброшены — честнее, чем выдуманное среднее
        assertNull(FuelConsumptionCalculator.average(expenses))
    }
}
