package com.aggin.carcost.domain.maintenance

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Прогноз даты следующего ТО.
 *
 * Прежняя версия молчала почти у всех: темп считался только по GPS-поездкам,
 * которые записывает меньшинство. А тем, у кого поездки были, могла назвать
 * дату в следующем веке — при одном коротком выезде за месяц деление давало
 * десятки тысяч дней.
 *
 * Ни то, ни другое не выглядело поломкой: в первом случае строка просто не
 * появлялась, во втором дата была формально посчитана.
 */
class MaintenancePredictionEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 6, 1)
    private val now: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun daysAgo(n: Long) = now - n * day

    private fun expense(daysAgo: Long, odometer: Int) = Expense(
        carId = "car",
        category = ExpenseCategory.FUEL,
        amount = 3000.0,
        date = daysAgo(daysAgo),
        odometer = odometer
    )

    private fun trip(daysAgo: Long, km: Double) = GpsTrip(
        carId = "car",
        startTime = daysAgo(daysAgo),
        distanceKm = km
    )

    private fun reminder(nextOdometer: Int, nextDate: Long? = null) = MaintenanceReminder(
        carId = "car",
        type = MaintenanceType.OIL_CHANGE,
        lastChangeOdometer = 40_000,
        intervalKm = 10_000,
        nextChangeOdometer = nextOdometer,
        nextChangeDate = nextDate
    )

    private fun predict(
        currentOdometer: Int,
        reminder: MaintenanceReminder,
        expenses: List<Expense> = emptyList(),
        trips: List<GpsTrip> = emptyList()
    ) = MaintenancePredictionEngine.predictNextServiceDate(
        currentOdometer, reminder, expenses, trips, now, zone
    )

    // ── Темп по одометру: главный источник ──────────────────────────────────

    @Test
    fun `темп считается по одометру из расходов`() {
        // Ровно 3000 км за ровно 100 дней — 30 км в день
        val expenses = listOf(expense(101, 47_000), expense(1, 50_000))
        val pace = MaintenancePredictionEngine.averagePace(expenses, now = now)!!
        assertEquals(30.0, pace.kmPerDay, 0.01)
        assertEquals(100L, pace.observedDays)
        assertEquals(MaintenancePredictionEngine.Source.ODOMETER, pace.source)
    }

    @Test
    fun `поездок нет, а прогноз есть`() {
        // Ровно та ситуация, из-за которой прогноза не видело большинство:
        // человек пишет заправки с пробегом, но GPS-поездки не записывает
        val expenses = listOf(expense(101, 47_000), expense(1, 50_000))
        val date = predict(50_000, reminder(nextOdometer = 53_000), expenses)
        // 30 км в день, до ТО 3000 км — сто дней
        assertEquals(today.plusDays(100), date)
    }

    @Test
    fun `записи без одометра не растягивают период`() {
        // Ноль означает «не указан». Если его учесть, пробег посчитается от
        // нуля до текущего, то есть за всю жизнь машины
        val expenses = listOf(
            expense(100, 0),
            expense(90, 47_000),
            expense(1, 50_000)
        )
        val pace = MaintenancePredictionEngine.averagePace(expenses, now = now)!!
        assertEquals(3_000.0 / 89, pace.kmPerDay, 0.5)
    }

    @Test
    fun `опечатка в меньшую сторону не даёт отрицательный темп`() {
        // Человек ввёл 5000 вместо 50000 — берём крайние значения, а не
        // разность последней и первой записи
        val expenses = listOf(
            expense(100, 47_000),
            expense(50, 50_000),
            expense(1, 5_000)
        )
        val pace = MaintenancePredictionEngine.averagePace(expenses, now = now)
        assertTrue("Темп должен остаться положительным", pace!!.kmPerDay > 0)
    }

    // ── Слишком короткое наблюдение ─────────────────────────────────────────

    @Test
    fun `три дня наблюдения — не повод для прогноза`() {
        // Было: пробег за период делили на 30 всегда. Поставил приложение три
        // дня назад, проехал 300 км — получал 10 км в день вместо ста, и срок
        // уезжал вдесятеро
        val expenses = listOf(expense(3, 49_700), expense(0, 50_000))
        assertNull(MaintenancePredictionEngine.averagePace(expenses, now = now))
        assertNull(predict(50_000, reminder(nextOdometer = 53_000), expenses))
    }

    @Test
    fun `одной записи мало`() {
        assertNull(MaintenancePredictionEngine.averagePace(listOf(expense(50, 50_000)), now = now))
    }

    @Test
    fun `машина стояла — темпа нет`() {
        val expenses = listOf(expense(100, 50_000), expense(1, 50_000))
        assertNull(MaintenancePredictionEngine.averagePace(expenses, now = now))
    }

    // ── Слишком далёкий срок ────────────────────────────────────────────────

    @Test
    fun `дата в следующем веке не показывается`() {
        // Один короткий выезд в месяц: 20 км за 100 дней — 0,2 км в день.
        // До ТО 10 000 км, то есть пятьдесят тысяч дней
        val expenses = listOf(expense(100, 49_990), expense(1, 50_010))
        assertNull(predict(50_010, reminder(nextOdometer = 60_000), expenses))
    }

    @Test
    fun `но если срок задан явно, он показывается и без темпа`() {
        val expenses = listOf(expense(100, 49_990), expense(1, 50_010))
        val date = predict(
            50_010,
            reminder(nextOdometer = 60_000, nextDate = daysAgo(-90)),
            expenses
        )
        assertEquals(today.plusDays(90), date)
    }

    // ── Просрочка ───────────────────────────────────────────────────────────

    @Test
    fun `пробег уже перевален — дата сегодняшняя`() {
        val expenses = listOf(expense(100, 47_000), expense(1, 61_000))
        assertEquals(today, predict(61_000, reminder(nextOdometer = 60_000), expenses))
    }

    // ── Пробег и срок вместе ────────────────────────────────────────────────

    @Test
    fun `берётся то, что наступит раньше`() {
        // Регламент задаётся и пробегом, и сроком, и выполняется по первому.
        // По пробегу выходит ровно сто дней: 30 км в день, до ТО 3000 км
        val expenses = listOf(expense(101, 47_000), expense(1, 50_000))

        // По пробегу — через 100 дней, срок — через 30. Ждём срок
        val early = predict(
            50_000,
            reminder(nextOdometer = 53_000, nextDate = daysAgo(-30)),
            expenses
        )
        assertEquals(today.plusDays(30), early)

        // По пробегу — через 100 дней, срок — через 300. Ждём пробег
        val late = predict(
            50_000,
            reminder(nextOdometer = 53_000, nextDate = daysAgo(-300)),
            expenses
        )
        assertEquals(today.plusDays(100), late)
    }

    // ── GPS как запасной источник ───────────────────────────────────────────

    @Test
    fun `без одометра темп берётся из поездок`() {
        val trips = listOf(trip(100, 1_500.0), trip(1, 1_500.0))
        val pace = MaintenancePredictionEngine.averagePace(emptyList(), trips, now)!!
        assertEquals(MaintenancePredictionEngine.Source.GPS, pace.source)
        assertEquals(3_000.0 / 99, pace.kmPerDay, 0.5)
    }

    @Test
    fun `одометр главнее поездок`() {
        // Поездки пишутся не всегда и покрывают не весь пробег: если считать
        // по ним при наличии одометра, темп выйдет заниженным
        val expenses = listOf(expense(100, 47_000), expense(1, 50_000))
        val trips = listOf(trip(100, 10.0), trip(1, 10.0))
        val pace = MaintenancePredictionEngine.averagePace(expenses, trips, now)!!
        assertEquals(MaintenancePredictionEngine.Source.ODOMETER, pace.source)
    }

    // ── Старые данные ───────────────────────────────────────────────────────

    @Test
    fun `записи старше полугода в темп не входят`() {
        val expenses = listOf(
            expense(700, 10_000),
            expense(600, 20_000),
            expense(100, 47_000),
            expense(1, 50_000)
        )
        val pace = MaintenancePredictionEngine.averagePace(expenses, now = now)!!
        // Если бы старые учитывались, период был бы 699 дней, а пробег 40 000
        assertEquals(30.0, pace.kmPerDay, 1.0)
        assertEquals(99L, pace.observedDays)
    }

    @Test
    fun `пустые данные не роняют расчёт`() {
        assertNull(MaintenancePredictionEngine.averagePace(emptyList(), emptyList(), now))
        assertNull(predict(50_000, reminder(nextOdometer = 60_000)))
    }
}
