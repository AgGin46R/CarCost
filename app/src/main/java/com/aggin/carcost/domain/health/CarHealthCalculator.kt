package com.aggin.carcost.domain.health

import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder

/**
 * Car Health Score — composite metric (0–100) describing how well-kept a single
 * car is. Unlike [com.aggin.carcost.domain.gamification.DriverScoreCalculator]
 * (which grades the *driver* across all their cars), this score is specific to
 * one vehicle and drives the circular indicator on `CarDetailScreen`.
 *
 * Formula:
 *  • Base              : 60 pts
 *  • No overdue ТО     : +40 pts
 *  • Each overdue ТО   : −10 pts (capped at the +40)
 *  • Active insurance  : +20 pts (any policy with endDate in future)
 *  • Each incident     : −8 pts  (in the last 365 days)
 *  • Clamped to 0..100
 */
data class CarHealthScore(
    val total: Int,                  // 0–100
    val overdueReminders: Int,
    val activeInsurance: Boolean,
    val recentIncidents: Int,
    val breakdown: List<HealthFactor> // for UI explanation
)

data class HealthFactor(
    val label: String,
    val delta: Int,                  // positive or negative contribution
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
        factors += HealthFactor("Базовый балл", 60, positive = true)

        // ── Maintenance reminders ─────────────────────────────────────────────
        val overdueCount = reminders.count { r ->
            r.nextChangeOdometer != null && currentOdometer >= r.nextChangeOdometer
        }
        if (overdueCount == 0 && reminders.isNotEmpty()) {
            score += 40
            factors += HealthFactor("Нет просроченных ТО", +40, positive = true)
        } else if (overdueCount > 0) {
            val penalty = (overdueCount * 10).coerceAtMost(40)
            score -= penalty
            factors += HealthFactor("Просроченных ТО: $overdueCount", -penalty, positive = false)
        }

        // ── Insurance ────────────────────────────────────────────────────────
        val activeInsurance = policies.any { it.endDate >= now }
        if (activeInsurance) {
            score += 20
            factors += HealthFactor("Страховка активна", +20, positive = true)
        } else {
            factors += HealthFactor("Нет действующей страховки", 0, positive = false)
        }

        // ── Incidents in last year ──────────────────────────────────────────
        val recentIncidents = incidents.count { (now - it.date) in 0..INCIDENT_WINDOW_MS }
        if (recentIncidents > 0) {
            val penalty = recentIncidents * 8
            score -= penalty
            factors += HealthFactor("Инцидентов за год: $recentIncidents", -penalty, positive = false)
        }

        val clamped = score.coerceIn(0, 100)
        return CarHealthScore(
            total = clamped,
            overdueReminders = overdueCount,
            activeInsurance = activeInsurance,
            recentIncidents = recentIncidents,
            breakdown = factors
        )
    }
}
