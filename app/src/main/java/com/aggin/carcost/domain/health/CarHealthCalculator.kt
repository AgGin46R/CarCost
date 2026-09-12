package com.aggin.carcost.domain.health

import androidx.annotation.StringRes
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder

/**
 * Оценка состояния автомобиля от 0 до 100.
 *
 * В отличие от [com.aggin.carcost.domain.gamification.DriverScoreCalculator],
 * который оценивает владельца по всем его машинам, это оценка одной машины —
 * та, что показывается кружком на карточке.
 *
 * Формула:
 *  - основа                       : 60
 *  - нет просроченного ТО         : +40
 *  - каждое просроченное ТО       : −10, но не больше −40
 *  - действующая страховка        : +20
 *  - каждое происшествие за год   : −8
 *  - результат приводится к 0..100
 */
data class CarHealthScore(
    val total: Int,
    val overdueReminders: Int,
    val activeInsurance: Boolean,
    val recentIncidents: Int,
    /** Из чего сложилась оценка — показывается человеку, когда он раскроет карточку */
    val breakdown: List<HealthFactor>
)

/**
 * Слагаемое оценки.
 *
 * Подпись хранится ресурсом, а не готовой строкой: раньше здесь лежал русский
 * текст прямо в коде, и на английском и казахском интерфейсе расшифровка
 * оценки на главном экране машины оставалась русской.
 *
 * @param count число для подстановки в подпись, если она его требует
 */
data class HealthFactor(
    @StringRes val labelRes: Int,
    val count: Int? = null,
    val delta: Int,
    val positive: Boolean
)

object CarHealthCalculator {

    private const val INCIDENT_WINDOW_MS = 365L * 24L * 60L * 60L * 1000L

    fun calculate(
        currentOdometer: Int,
        reminders: List<MaintenanceReminder>,
        incidents: List<CarIncident>,
        policies: List<InsurancePolicy>,
        now: Long = System.currentTimeMillis()
    ): CarHealthScore {
        val factors = mutableListOf<HealthFactor>()
        var score = 60
        factors += HealthFactor(R.string.health_base, delta = 60, positive = true)

        // ── Техобслуживание ─────────────────────────────────────────────────
        //
        // Выключенные напоминания не считаются: человек отключил напоминание
        // сознательно, а уведомления по ним и не приходят — воркер берёт
        // только активные. Раньше оценка учитывала все подряд, и выключенное
        // напоминание продолжало тянуть её вниз молча.
        val active = reminders.filter { it.isActive }

        // Просрочка бывает двух видов, и раньше считалась только первая.
        // Уведомление «Масло просрочено на 40 дней» приходило, а на карточке
        // той же машины стояло 100 из 100 — приложение спорило само с собой.
        val overdueCount = active.count { r ->
            val byOdometer = currentOdometer >= r.nextChangeOdometer
            val byDate = r.nextChangeDate?.let { now >= it } ?: false
            byOdometer || byDate
        }

        if (overdueCount == 0 && active.isNotEmpty()) {
            score += 40
            factors += HealthFactor(R.string.health_no_overdue, delta = 40, positive = true)
        } else if (overdueCount > 0) {
            val penalty = (overdueCount * 10).coerceAtMost(40)
            score -= penalty
            factors += HealthFactor(
                R.string.health_overdue,
                count = overdueCount,
                delta = -penalty,
                positive = false
            )
        }
        // Напоминаний нет вовсе — ни бонуса, ни штрафа: о состоянии ТО этой
        // машины нам просто нечего сказать

        // ── Страховка ───────────────────────────────────────────────────────
        val activeInsurance = policies.any { it.endDate >= now }
        if (activeInsurance) {
            score += 20
            factors += HealthFactor(R.string.health_insurance_ok, delta = 20, positive = true)
        } else {
            factors += HealthFactor(R.string.health_insurance_none, delta = 0, positive = false)
        }

        // ── Происшествия за последний год ───────────────────────────────────
        val recentIncidents = incidents.count { (now - it.date) in 0..INCIDENT_WINDOW_MS }
        if (recentIncidents > 0) {
            val penalty = recentIncidents * 8
            score -= penalty
            factors += HealthFactor(
                R.string.health_incidents,
                count = recentIncidents,
                delta = -penalty,
                positive = false
            )
        }

        return CarHealthScore(
            total = score.coerceIn(0, 100),
            overdueReminders = overdueCount,
            activeInsurance = activeInsurance,
            recentIncidents = recentIncidents,
            breakdown = factors
        )
    }
}
