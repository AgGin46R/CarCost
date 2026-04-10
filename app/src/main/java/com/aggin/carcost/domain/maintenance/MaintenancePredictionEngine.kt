package com.aggin.carcost.domain.maintenance

import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object MaintenancePredictionEngine {

    /**
     * Predicts when the next service is due based on avg km/month from recent GPS trips.
     * Returns null if there isn't enough data to make a prediction.
     */
    fun predictNextServiceDate(
        car: Car,
        reminder: MaintenanceReminder,
        recentTrips: List<GpsTrip>
    ): LocalDate? {
        val targetOdometer = reminder.nextChangeOdometer ?: return null
        val kmLeft = targetOdometer - car.currentOdometer
        if (kmLeft <= 0) return LocalDate.now() // already overdue

        val avgKmPerDay = calcAvgKmPerDay(recentTrips)
        if (avgKmPerDay <= 0) return null

        val daysUntilDue = (kmLeft / avgKmPerDay).toLong()
        return LocalDate.now().plusDays(daysUntilDue)
    }

    private fun calcAvgKmPerDay(trips: List<GpsTrip>): Double {
        if (trips.isEmpty()) return 0.0
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val recent = trips.filter { it.startTime >= thirtyDaysAgo }
        if (recent.isEmpty()) return 0.0
        val totalKm = recent.sumOf { it.distanceKm }
        return totalKm / 30.0
    }
}
