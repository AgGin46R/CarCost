package com.aggin.carcost.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Номера уведомлений не должны пересекаться.
 *
 * Пересечение не роняет приложение и не пишет в лог — одно уведомление просто
 * заменяет другое, и заметить это можно, только поймав момент. Именно так
 * второе предупреждение о бюджете съедало уведомление идущей поездки: у обоих
 * оказался номер 2001.
 *
 * Тест перебирает все виды и проверяет, что их номера не совпадают. Новый вид,
 * заведённый мимо блока, здесь и споткнётся.
 */
class NotificationIdsTest {

    /** Номера, занятые вне реестра. Задокументированы в NotificationIds */
    private val reservedElsewhere = mapOf(
        2001 to "GpsTripService",
        3001 to "NavigationService",
        99_000 to "NotificationHelper.NOTIF_ID_UPDATE"
    )

    private val cars = listOf(
        "a1b2c3d4-0000-0000-0000-000000000001",
        "e5f6a7b8-0000-0000-0000-000000000002",
        "11111111-2222-3333-4444-555555555555",
        "",
        "короткий",
        "🚗"
    )

    /**
     * Все номера, которые приложение вообще может выдать при разумных объёмах.
     *
     * @return номер → чем он занят
     */
    private fun allIds(): Map<Int, String> {
        val out = linkedMapOf<Int, String>()

        fun put(id: Int, who: String) {
            val existing = out[id]
            assertEquals(
                "Номер $id занят дважды: «$existing» и «$who»",
                null,
                existing
            )
            out[id] = who
        }

        reservedElsewhere.forEach { (id, who) -> put(id, who) }

        put(NotificationIds.PARKING_TIMER, "таймер парковки")
        put(NotificationIds.FIRST_RECORD_NUDGE, "первая запись")
        put(NotificationIds.WEEKLY_SUMMARY, "итоги недели")
        put(NotificationIds.STATION_HINT, "подсказка о заправке")
        put(NotificationIds.GEOFENCE_FILL_UP, "геозона заправки")

        // Пять машин и по пять записей каждого вида — больше, чем бывает
        repeat(5) { i ->
            put(NotificationIds.fuelLow(i), "остаток топлива #$i")
            put(NotificationIds.maintenanceByKm(i), "ТО по пробегу #$i")
            put(NotificationIds.maintenanceByDate(i), "ТО по сроку #$i")
            put(NotificationIds.budget(i), "бюджет #$i")
            put(NotificationIds.vehicleTax(i), "налог #$i")
            put(NotificationIds.yearReview(i), "итоги года #$i")
        }

        // Пороги напоминаний о сроках — те, что реально используются
        listOf(30, 14, 7, 1).forEach { days ->
            repeat(5) { i ->
                put(NotificationIds.insurance(i, days), "страховка #$i за $days дн.")
                put(NotificationIds.document(i, days), "документ #$i за $days дн.")
            }
        }

        cars.forEach { car ->
            put(NotificationIds.fluid(car), "жидкости $car")
            put(NotificationIds.backgroundChat(car), "фон: чат $car")
            put(NotificationIds.backgroundExpense(car), "фон: расход $car")
            put(NotificationIds.backgroundReminder(car), "фон: напоминание $car")
            put(NotificationIds.realtimeChat(car), "подписка: сообщение $car")
            put(NotificationIds.realtimeExpense(car), "подписка: расход $car")
            put(NotificationIds.realtimeReminder(car), "подписка: напоминание $car")
            put(NotificationIds.realtimeInvitation(car), "подписка: приглашение $car")
        }

        return out
    }

    @Test
    fun `номера уведомлений не пересекаются`() {
        val ids = allIds()
        assertTrue("Ожидали заметное число номеров, получили ${ids.size}", ids.size > 100)
    }

    // ── Отдельные случаи, которые уже ломались ──────────────────────────────

    @Test
    fun `предупреждение о бюджете не наезжает на уведомление поездки`() {
        // Было: счётчик начинался с 2000 и второе предупреждение получало 2001 —
        // номер службы записи поездки. Уведомление подменялось текстом про
        // бюджет, и человек терял кнопку остановки записи
        repeat(20) { i ->
            assertNotEquals(2001, NotificationIds.budget(i))
            assertNotEquals(3001, NotificationIds.budget(i))
        }
    }

    @Test
    fun `ТО по сроку не совпадает с таймером парковки`() {
        repeat(20) { i ->
            assertNotEquals(NotificationIds.PARKING_TIMER, NotificationIds.maintenanceByDate(i))
        }
    }

    @Test
    fun `жидкости не пересекаются со страховками`() {
        val fluids = cars.map { NotificationIds.fluid(it) }.toSet()
        val insurance = listOf(30, 14, 7, 1)
            .flatMap { days -> (0 until 20).map { NotificationIds.insurance(it, days) } }
            .toSet()
        assertTrue(
            "Пересечение: ${fluids intersect insurance}",
            (fluids intersect insurance).isEmpty()
        )
    }

    @Test
    fun `у одного полиса напоминания за 30 и за 7 дней разные`() {
        // Иначе напоминание за неделю заменило бы собой напоминание за месяц,
        // и человек увидел бы только одно из двух
        assertNotEquals(NotificationIds.insurance(0, 30), NotificationIds.insurance(0, 7))
        assertNotEquals(NotificationIds.insurance(0, 14), NotificationIds.insurance(0, 1))
    }

    @Test
    fun `три вида фоновой синхронизации не смешиваются`() {
        // Было: базы разнесены на 5 000, а разброс по машине — 8 000, и чат
        // одной машины попадал на расход другой
        val chat = cars.map { NotificationIds.backgroundChat(it) }.toSet()
        val expense = cars.map { NotificationIds.backgroundExpense(it) }.toSet()
        val reminder = cars.map { NotificationIds.backgroundReminder(it) }.toSet()
        assertTrue((chat intersect expense).isEmpty())
        assertTrue((chat intersect reminder).isEmpty())
        assertTrue((expense intersect reminder).isEmpty())
    }

    // ── Границы ─────────────────────────────────────────────────────────────

    @Test
    fun `номер всегда положительный`() {
        // hashCode умеет возвращать Int.MIN_VALUE, у которого нет
        // положительного значения: abs от него возвращает его же
        val nasty = listOf("polygenelubricants", "", " ", " ", "𝕏".repeat(50))
        nasty.forEach { id ->
            assertTrue("fluid($id) = ${NotificationIds.fluid(id)}", NotificationIds.fluid(id) > 0)
            assertTrue(NotificationIds.backgroundChat(id) > 0)
            assertTrue(NotificationIds.realtimeChat(id) > 0)
        }
    }

    @Test
    fun `смещение не выводит номер за пределы блока`() {
        // Даже с нелепым индексом вид остаётся в своих десяти тысячах
        val huge = NotificationIds.budget(Int.MAX_VALUE)
        assertTrue("Ушли из блока: $huge", huge in 160_000..169_999)
        assertTrue(NotificationIds.budget(-5) in 160_000..169_999)
    }
}
