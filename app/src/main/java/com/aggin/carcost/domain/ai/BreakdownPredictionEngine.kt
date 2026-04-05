package com.aggin.carcost.domain.ai

import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.InsightSeverity
import com.aggin.carcost.data.local.database.entities.InsightType
import com.aggin.carcost.data.local.database.entities.ServiceType
import java.util.Calendar
import java.util.UUID

/**
 * Predicts potential issues based on car age, mileage, and service history.
 * All rules are based on standard Russian/CIS maintenance recommendations.
 */
object BreakdownPredictionEngine {

    fun predict(carId: String, car: Car, expenses: List<Expense>): List<AiInsight> {
        val insights = mutableListOf<AiInsight>()
        val currentOdometer = car.currentOdometer
        val carAgeYears = Calendar.getInstance().get(Calendar.YEAR) - car.year

        insights += checkOilChange(carId, currentOdometer, expenses)
        insights += checkTimingBelt(carId, currentOdometer, carAgeYears, expenses)
        insights += checkBrakePads(carId, currentOdometer, expenses)
        insights += checkBattery(carId, carAgeYears, expenses)
        insights += checkCoolant(carId, currentOdometer, expenses)

        return insights
    }

    private fun checkOilChange(carId: String, odometer: Int, expenses: List<Expense>): List<AiInsight> {
        val lastOilChange = expenses
            .filter { it.serviceType == ServiceType.OIL_CHANGE || it.serviceType == ServiceType.OIL_FILTER }
            .maxByOrNull { it.odometer }

        val lastOilOdometer = lastOilChange?.odometer ?: 0
        val kmSinceOil = odometer - lastOilOdometer

        return when {
            kmSinceOil > 12000 -> listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Замена масла просрочена",
                "Прошло $kmSinceOil км с последней замены масла (рекомендуется каждые 10 000–12 000 км). Рекомендуется срочная замена.",
                InsightSeverity.CRITICAL
            ))
            kmSinceOil > 9000 -> listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Скоро замена масла",
                "Прошло $kmSinceOil км с последней замены масла. Запланируйте замену в ближайшее время.",
                InsightSeverity.WARNING
            ))
            else -> emptyList()
        }
    }

    private fun checkTimingBelt(carId: String, odometer: Int, ageYears: Int, expenses: List<Expense>): List<AiInsight> {
        val lastBeltChange = expenses
            .filter { it.serviceType == ServiceType.TIMING_BELT }
            .maxByOrNull { it.odometer }

        val lastOdo = lastBeltChange?.odometer ?: 0
        val kmSinceBelt = odometer - lastOdo

        // Typical interval: 60 000 km or 5 years
        val isOverdueByKm = kmSinceBelt > 55000
        val isOverdueByAge = lastBeltChange == null && ageYears >= 5

        return if (isOverdueByKm || isOverdueByAge) {
            listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Проверьте ремень ГРМ",
                if (isOverdueByKm)
                    "Прошло $kmSinceBelt км с замены ремня ГРМ. Стандартный интервал: 60 000 км. Обрыв ремня может привести к капитальному ремонту двигателя."
                else
                    "Нет записей о замене ремня ГРМ, а возраст авто $ageYears лет. Рекомендуется проверить состояние ремня.",
                InsightSeverity.WARNING
            ))
        } else emptyList()
    }

    private fun checkBrakePads(carId: String, odometer: Int, expenses: List<Expense>): List<AiInsight> {
        val lastBrakeChange = expenses
            .filter { it.serviceType == ServiceType.BRAKE_PADS }
            .maxByOrNull { it.odometer }

        val lastOdo = lastBrakeChange?.odometer ?: 0
        val kmSinceBrakes = odometer - lastOdo

        return if (kmSinceBrakes > 40000) {
            listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Проверьте тормозные колодки",
                "Прошло $kmSinceBrakes км с последней замены тормозных колодок. Рекомендуется диагностика тормозной системы.",
                InsightSeverity.WARNING
            ))
        } else emptyList()
    }

    private fun checkBattery(carId: String, ageYears: Int, expenses: List<Expense>): List<AiInsight> {
        val lastBatteryChange = expenses
            .filter { it.serviceType == ServiceType.BATTERY }
            .maxByOrNull { it.date }

        val yearsSinceBattery = if (lastBatteryChange != null) {
            ((System.currentTimeMillis() - lastBatteryChange.date) / (365L * 86_400_000L)).toInt()
        } else {
            ageYears
        }

        return if (yearsSinceBattery >= 4) {
            listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Проверьте аккумулятор",
                "Аккумулятор служит в среднем 3–5 лет. Прошло $yearsSinceBattery лет с последней замены. Особенно актуально перед зимой.",
                InsightSeverity.INFO
            ))
        } else emptyList()
    }

    private fun checkCoolant(carId: String, odometer: Int, expenses: List<Expense>): List<AiInsight> {
        val lastCoolantChange = expenses
            .filter { it.serviceType == ServiceType.COOLANT }
            .maxByOrNull { it.odometer }

        val lastOdo = lastCoolantChange?.odometer ?: 0
        val kmSinceCoolant = odometer - lastOdo

        return if (kmSinceCoolant > 80000) {
            listOf(insight(
                carId, InsightType.MAINTENANCE_PREDICTION,
                "Замена охлаждающей жидкости",
                "Прошло $kmSinceCoolant км с последней замены ОЖ (рекомендуется каждые 60 000–80 000 км).",
                InsightSeverity.INFO
            ))
        } else emptyList()
    }

    private fun insight(
        carId: String,
        type: InsightType,
        title: String,
        body: String,
        severity: InsightSeverity
    ) = AiInsight(
        id = UUID.randomUUID().toString(),
        carId = carId,
        type = type,
        title = title,
        body = body,
        severity = severity
    )
}
