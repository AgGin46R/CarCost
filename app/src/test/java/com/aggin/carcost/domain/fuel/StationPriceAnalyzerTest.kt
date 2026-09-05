package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цены литра по заправкам.
 *
 * Считается по фактически уплаченному, поэтому легко ошибиться в двух местах:
 * взять среднее из цен вместо средней по объёму и не свести разные написания
 * одной сети. Оба случая проверяются здесь.
 */
class StationPriceAnalyzerTest {

    private var seq = 0

    private fun fill(
        place: String?,
        liters: Double?,
        amount: Double,
        day: Int = seq++
    ) = Expense(
        id = "e$day",
        carId = "car",
        category = ExpenseCategory.FUEL,
        amount = amount,
        date = day * 24L * 60 * 60 * 1000,
        odometer = 100_000 + day * 500,
        location = place,
        fuelLiters = liters
    )

    @Test
    fun `средняя цена считается по объёму, а не как среднее из цен заправок`() {
        // Сорок литров по 50 и один литр по 100. Среднее из цен дало бы 75,
        // хотя человек заплатил 2100 за 41 литр — это 51,2 за литр.
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Лукойл", 40.0, 2000.0),
                fill("Лукойл", 1.0, 100.0)
            )
        )

        assertEquals(1, stations.size)
        assertEquals(51.22, stations[0].averagePerLiter, 0.01)
    }

    @Test
    fun `разные написания одной сети сводятся в одну заправку`() {
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Лукойл", 40.0, 2000.0),
                fill("лукойл на Ленина", 40.0, 2000.0),
                fill("АЗС ЛУКОЙЛ №42", 40.0, 2000.0)
            )
        )

        assertEquals(1, stations.size)
        assertEquals("Лукойл", stations[0].name)
        assertEquals(3, stations[0].fillUps)
    }

    @Test
    fun `Газпромнефть не сводится к Газпрому`() {
        // Разные сети с разными ценами. Если проверять вхождение по короткому
        // названию раньше длинного, одна поглотит другую.
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Газпромнефть", 40.0, 2000.0),
                fill("Газпромнефть", 40.0, 2000.0),
                fill("Газпром", 40.0, 2400.0),
                fill("Газпром", 40.0, 2400.0)
            )
        )

        assertEquals(2, stations.size)
        assertTrue(stations.any { it.name == "Газпромнефть" })
        assertTrue(stations.any { it.name == "Газпром" })
    }

    @Test
    fun `переплата считается от самой дешёвой заправки`() {
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Лукойл", 50.0, 2500.0),   // 50 за литр
                fill("Лукойл", 50.0, 2500.0),
                fill("Татнефть", 50.0, 2600.0), // 52 за литр
                fill("Татнефть", 50.0, 2600.0)
            )
        )

        val cheap = stations.first { it.name == "Лукойл" }
        val pricey = stations.first { it.name == "Татнефть" }

        assertEquals(0.0, cheap.overpayPerLiter, 0.001)
        assertEquals(2.0, pricey.overpayPerLiter, 0.001)
        // Сто литров по два рубля разницы
        assertEquals(200.0, pricey.overpayTotal, 0.01)
        assertEquals(200.0, StationPriceAnalyzer.totalOverpay(stations), 0.01)
    }

    @Test
    fun `самая дешёвая идёт первой`() {
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Татнефть", 50.0, 2600.0),
                fill("Татнефть", 50.0, 2600.0),
                fill("Лукойл", 50.0, 2500.0),
                fill("Лукойл", 50.0, 2500.0)
            )
        )
        assertEquals("Лукойл", stations[0].name)
    }

    @Test
    fun `заправка с одним визитом в сравнение не попадает`() {
        // Одна цена в один день — это не цена заправки, а случайный день
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Лукойл", 40.0, 2000.0),
                fill("Лукойл", 40.0, 2000.0),
                fill("Случайная АЗС в отпуске", 40.0, 3200.0)
            )
        )
        assertEquals(1, stations.size)
        assertEquals("Лукойл", stations[0].name)
    }

    @Test
    fun `записи без литров и без места пропускаются`() {
        val stations = StationPriceAnalyzer.analyze(
            listOf(
                fill("Лукойл", null, 2000.0),
                fill("Лукойл", null, 2000.0),
                fill(null, 40.0, 2000.0),
                fill("", 40.0, 2000.0)
            )
        )
        assertTrue(stations.isEmpty())
    }

    @Test
    fun `не топливные расходы не считаются`() {
        val wash = Expense(
            id = "w", carId = "car", category = ExpenseCategory.WASH,
            amount = 700.0, date = 0L, odometer = 100_000,
            location = "Мойка №5", fuelLiters = 10.0
        )
        assertTrue(StationPriceAnalyzer.analyze(listOf(wash, wash)).isEmpty())
    }
}
