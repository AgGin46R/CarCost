package com.aggin.carcost.domain.health

import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.IncidentType
import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Оценка состояния автомобиля.
 *
 * Кружок на карточке машины — первое, что человек видит, открыв автомобиль, и
 * единственная цифра, которой он верит не проверяя. Она уже расходилась с
 * остальным приложением: уведомление сообщало «масло просрочено на 40 дней», а
 * оценка в тот же момент показывала 100 из 100, потому что просрочку считали
 * только по пробегу.
 */
class CarHealthCalculatorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun reminder(
        nextOdometer: Int = 100_000,
        nextDate: Long? = null,
        active: Boolean = true
    ) = MaintenanceReminder(
        carId = "car",
        type = MaintenanceType.OIL_CHANGE,
        lastChangeOdometer = 40_000,
        intervalKm = 10_000,
        nextChangeOdometer = nextOdometer,
        nextChangeDate = nextDate,
        isActive = active
    )

    private fun policy(endsIn: Long) = InsurancePolicy(
        carId = "car",
        type = "OSAGO",
        startDate = now - 300 * day,
        endDate = now + endsIn
    )

    private fun incident(daysAgo: Long) = CarIncident(
        carId = "car",
        date = now - daysAgo * day,
        type = IncidentType.ACCIDENT,
        description = "тест"
    )

    private fun score(
        odometer: Int = 50_000,
        reminders: List<MaintenanceReminder> = emptyList(),
        incidents: List<CarIncident> = emptyList(),
        policies: List<InsurancePolicy> = emptyList()
    ) = CarHealthCalculator.calculate(odometer, reminders, incidents, policies, now)

    // ── Просрочка по пробегу ────────────────────────────────────────────────

    @Test
    fun `всё в порядке — сто баллов`() {
        val s = score(
            odometer = 50_000,
            reminders = listOf(reminder(nextOdometer = 60_000)),
            policies = listOf(policy(endsIn = 100 * day))
        )
        assertEquals(100, s.total)
        assertEquals(0, s.overdueReminders)
    }

    @Test
    fun `просрочка по пробегу снимает баллы`() {
        val s = score(odometer = 61_000, reminders = listOf(reminder(nextOdometer = 60_000)))
        assertEquals(1, s.overdueReminders)
        assertEquals(50, s.total)  // 60 − 10
    }

    @Test
    fun `штраф за просрочки не превышает сорока`() {
        val s = score(
            odometer = 61_000,
            reminders = List(10) { reminder(nextOdometer = 60_000) }
        )
        assertEquals(10, s.overdueReminders)
        assertEquals(20, s.total)  // 60 − 40, а не 60 − 100
    }

    // ── Просрочка по дате ───────────────────────────────────────────────────

    @Test
    fun `просрочка по дате тоже считается просрочкой`() {
        // Пробег в порядке, а срок вышел. Уведомление о такой просрочке
        // приложение шлёт — значит и оценка обязана её видеть
        val s = score(
            odometer = 50_000,
            reminders = listOf(reminder(nextOdometer = 60_000, nextDate = now - 40 * day))
        )
        assertEquals(1, s.overdueReminders)
        assertTrue("Оценка не должна быть стопроцентной", s.total < 100)
    }

    @Test
    fun `срок ещё не вышел — просрочки нет`() {
        val s = score(
            odometer = 50_000,
            reminders = listOf(reminder(nextOdometer = 60_000, nextDate = now + 40 * day))
        )
        assertEquals(0, s.overdueReminders)
    }

    @Test
    fun `просрочено и по пробегу, и по дате — это одна просрочка`() {
        // Иначе одно напоминание снимало бы двадцать баллов вместо десяти
        val s = score(
            odometer = 61_000,
            reminders = listOf(reminder(nextOdometer = 60_000, nextDate = now - day))
        )
        assertEquals(1, s.overdueReminders)
    }

    // ── Выключенные напоминания ─────────────────────────────────────────────

    @Test
    fun `выключенное напоминание оценку не портит`() {
        // Уведомления по нему не приходят — воркер берёт только активные.
        // Тянуть оценку вниз оно тоже не должно
        val s = score(
            odometer = 61_000,
            reminders = listOf(reminder(nextOdometer = 60_000, active = false))
        )
        assertEquals(0, s.overdueReminders)
    }

    @Test
    fun `выключенные напоминания не считаются и за порядок`() {
        // Одни выключенные — это «нам нечего сказать», а не «всё в порядке»
        val s = score(reminders = listOf(reminder(active = false)))
        assertEquals(60, s.total)
    }

    @Test
    fun `без напоминаний ни бонуса, ни штрафа`() {
        assertEquals(60, score().total)
    }

    // ── Страховка ───────────────────────────────────────────────────────────

    @Test
    fun `действующая страховка добавляет баллы`() {
        assertTrue(score(policies = listOf(policy(endsIn = day))).activeInsurance)
        assertEquals(80, score(policies = listOf(policy(endsIn = day))).total)
    }

    @Test
    fun `истёкшая страховка не считается действующей`() {
        val expired = policy(endsIn = -day)
        assertFalse(score(policies = listOf(expired)).activeInsurance)
        assertEquals(60, score(policies = listOf(expired)).total)
    }

    // ── Происшествия ────────────────────────────────────────────────────────

    @Test
    fun `происшествие за год снимает восемь баллов`() {
        val s = score(incidents = listOf(incident(daysAgo = 30)))
        assertEquals(1, s.recentIncidents)
        assertEquals(52, s.total)
    }

    @Test
    fun `происшествие старше года не учитывается`() {
        val s = score(incidents = listOf(incident(daysAgo = 400)))
        assertEquals(0, s.recentIncidents)
        assertEquals(60, s.total)
    }

    @Test
    fun `происшествие из будущего не учитывается`() {
        // Дату вводят руками и ошибаются в годе
        val future = incident(daysAgo = -10)
        assertEquals(0, score(incidents = listOf(future)).recentIncidents)
    }

    // ── Границы ─────────────────────────────────────────────────────────────

    @Test
    fun `оценка не уходит ниже нуля`() {
        val s = score(
            odometer = 61_000,
            reminders = List(5) { reminder(nextOdometer = 60_000) },
            incidents = List(10) { incident(daysAgo = 10) }
        )
        assertEquals(0, s.total)
    }

    @Test
    fun `оценка не превышает ста`() {
        val s = score(
            odometer = 50_000,
            reminders = listOf(reminder(nextOdometer = 60_000)),
            policies = List(5) { policy(endsIn = day) }
        )
        assertEquals(100, s.total)
    }

    @Test
    fun `расшифровка объясняет каждую составляющую`() {
        val s = score(
            odometer = 61_000,
            reminders = listOf(reminder(nextOdometer = 60_000)),
            incidents = listOf(incident(daysAgo = 10)),
            policies = listOf(policy(endsIn = day))
        )
        // основа, просрочка, страховка, происшествия
        assertEquals(4, s.breakdown.size)
        // Сумма слагаемых должна сходиться с итогом — иначе расшифровка
        // объясняет не ту цифру, которую человек видит
        assertEquals(s.total, s.breakdown.sumOf { it.delta })
    }
}
