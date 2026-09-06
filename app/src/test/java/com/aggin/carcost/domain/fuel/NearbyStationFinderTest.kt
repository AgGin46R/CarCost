package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Узнавание заправки по координатам конца поездки.
 *
 * Ошибка здесь стоит дорого не деньгами, а доверием: приложение само пишет
 * человеку «вы заправились на Лукойле?». Если оно ошибётся — предложит не ту
 * заправку, или напомнит через минуту после того, как человек всё записал
 * сам, — уведомления выключат целиком, вместе с полезными.
 */
class NearbyStationFinderTest {

    private val now = 1_700_000_000_000L

    /** Заправка на Ленина */
    private val lat = 55.7558
    private val lon = 37.6173

    private fun fuel(
        location: String?,
        lat: Double?,
        lon: Double?,
        daysAgo: Int = 30
    ) = Expense(
        carId = "car",
        category = ExpenseCategory.FUEL,
        amount = 3000.0,
        date = now - daysAgo * 86_400_000L,
        odometer = 50_000,
        location = location,
        latitude = lat,
        longitude = lon
    )

    private fun find(expenses: List<Expense>, atLat: Double = lat, atLon: Double = lon) =
        NearbyStationFinder.find(expenses, atLat, atLon, now)

    // ── Расстояние ──────────────────────────────────────────────────────────

    @Test
    fun `расстояние до самой себя равно нулю`() {
        assertEquals(0.0, NearbyStationFinder.distanceMeters(lat, lon, lat, lon), 0.001)
    }

    @Test
    fun `сотня метров считается сотней метров`() {
        // 0,001 градуса широты — примерно 111 метров
        val d = NearbyStationFinder.distanceMeters(lat, lon, lat + 0.001, lon)
        assertTrue("Ожидали около 111 м, получили $d", d in 105.0..118.0)
    }

    // ── Узнавание ───────────────────────────────────────────────────────────

    @Test
    fun `заправка рядом узнаётся`() {
        val match = find(listOf(fuel("Лукойл на Ленина", lat + 0.0005, lon)))
        assertEquals("Лукойл", match!!.name)
        assertTrue(match.distanceMeters < NearbyStationFinder.MATCH_RADIUS_M)
    }

    @Test
    fun `далёкая заправка не предлагается`() {
        // Полкилометра — это уже другая заправка, даже если сеть та же
        assertNull(find(listOf(fuel("Лукойл", lat + 0.005, lon))))
    }

    @Test
    fun `из двух рядом выбирается ближняя`() {
        val match = find(
            listOf(
                fuel("Роснефть", lat + 0.0015, lon),
                fuel("Лукойл", lat + 0.0002, lon)
            )
        )
        assertEquals("Лукойл", match!!.name)
    }

    @Test
    fun `визиты на одну заправку считаются вместе`() {
        // Без группировки по названию «сколько раз бывали» всегда было бы 1
        val match = find(
            listOf(
                fuel("Лукойл на Ленина", lat + 0.0002, lon, daysAgo = 60),
                fuel("лукойл", lat + 0.0003, lon, daysAgo = 40),
                fuel("АЗС Лукойл №42", lat + 0.0001, lon, daysAgo = 20)
            )
        )
        assertEquals("Лукойл", match!!.name)
        assertEquals(3, match.visits)
    }

    // ── Когда предлагать нельзя ─────────────────────────────────────────────

    @Test
    fun `после свежей заправки ничего не предлагается`() {
        // Человек записал заправку сам, стоя у колонки. Уведомление «не забыли
        // записать заправку?» через минуту после этого — худшее, что можно
        // сделать
        val expenses = listOf(
            fuel("Лукойл", lat + 0.0002, lon, daysAgo = 30),
            fuel("Лукойл", lat + 0.0002, lon).copy(date = now - 60_000)
        )
        assertNull(find(expenses))
    }

    @Test
    fun `заправка вчера предложению не мешает`() {
        val expenses = listOf(
            fuel("Лукойл", lat + 0.0002, lon, daysAgo = 1)
        )
        assertEquals("Лукойл", find(expenses)!!.name)
    }

    @Test
    fun `заправки без координат не участвуют`() {
        assertNull(find(listOf(fuel("Лукойл", null, null))))
    }

    @Test
    fun `заправки без названия не участвуют`() {
        // Предложить «записать заправку на …» нечего
        assertNull(find(listOf(fuel(null, lat + 0.0002, lon))))
        assertNull(find(listOf(fuel("   ", lat + 0.0002, lon))))
    }

    @Test
    fun `нетопливные расходы рядом не считаются заправкой`() {
        val wash = fuel("Мойка у дома", lat + 0.0002, lon)
            .copy(category = ExpenseCategory.WASH)
        assertNull(find(listOf(wash)))
    }

    @Test
    fun `пустая история ничего не даёт`() {
        assertNull(find(emptyList()))
    }
}
