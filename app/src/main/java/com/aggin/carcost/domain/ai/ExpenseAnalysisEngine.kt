package com.aggin.carcost.domain.ai

import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.InsightSeverity
import com.aggin.carcost.data.local.database.entities.InsightType
import java.util.Calendar
import java.util.UUID

/**
 * Rule-based expense analysis engine.
 * Detects anomalies, spikes, and seasonal patterns without external APIs.
 */
object ExpenseAnalysisEngine {

    /**
     * Analyze expenses and generate AI insights.
     * @param carId target car
     * @param expenses all expenses for the car (sorted by date)
     * @return list of new insights (caller is responsible for saving them)
     */
    fun analyze(carId: String, expenses: List<Expense>): List<AiInsight> {
        if (expenses.size < 3) return emptyList()

        val insights = mutableListOf<AiInsight>()

        insights += detectCostSpike(carId, expenses)
        insights += detectHighFuelConsumption(carId, expenses)
        insights += detectMissingMaintenance(carId, expenses)
        insights += detectSeasonalPattern(carId, expenses)

        return insights
    }

    private fun detectCostSpike(carId: String, expenses: List<Expense>): List<AiInsight> {
        val insights = mutableListOf<AiInsight>()
        val byCategory = expenses.groupBy { it.category }

        for ((category, catExpenses) in byCategory) {
            if (catExpenses.size < 4) continue

            val sorted = catExpenses.sortedBy { it.date }
            val recentMonths = getMonthlyTotals(sorted)
            if (recentMonths.size < 3) continue

            val historical = recentMonths.dropLast(1)
            val avg = historical.map { it.second }.average()
            val lastMonth = recentMonths.last().second

            if (avg > 0 && lastMonth > avg * 2.0) {
                val percent = ((lastMonth / avg - 1) * 100).toInt()
                insights += AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.COST_SPIKE,
                    title = "Рост расходов: ${category.displayName}",
                    body = "В этом месяце вы потратили на ${category.displayName.lowercase()} на $percent% больше обычного (${lastMonth.toInt()} vs среднее ${avg.toInt()} руб.)",
                    severity = if (percent > 100) InsightSeverity.WARNING else InsightSeverity.INFO
                )
            }
        }
        return insights
    }

    private fun detectHighFuelConsumption(carId: String, expenses: List<Expense>): List<AiInsight> {
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.date }

        if (fuelExpenses.size < 3) return emptyList()

        val consumptions = mutableListOf<Double>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val distKm = curr.odometer - prev.odometer
            if (distKm > 50 && curr.fuelLiters != null) {
                consumptions += (curr.fuelLiters / distKm) * 100.0
            }
        }

        if (consumptions.size < 2) return emptyList()

        val avg = consumptions.average()
        val last = consumptions.last()

        // Alert if last fill consumption is 25%+ above average
        if (last > avg * 1.25 && avg > 0) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.FUEL_EFFICIENCY,
                    title = "Повышенный расход топлива",
                    body = "Последний расчётный расход %.1f л/100км превышает средний %.1f л/100км. Проверьте давление в шинах и состояние воздушного фильтра.".format(last, avg),
                    severity = InsightSeverity.WARNING
                )
            )
        }
        return emptyList()
    }

    private fun detectMissingMaintenance(carId: String, expenses: List<Expense>): List<AiInsight> {
        val maintenanceExpenses = expenses.filter {
            it.category == ExpenseCategory.MAINTENANCE || it.category == ExpenseCategory.REPAIR
        }
        if (maintenanceExpenses.isEmpty()) return emptyList()

        val lastMaintenance = maintenanceExpenses.maxByOrNull { it.date } ?: return emptyList()
        val daysSince = (System.currentTimeMillis() - lastMaintenance.date) / 86_400_000L

        if (daysSince > 180) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.MAINTENANCE_PREDICTION,
                    title = "Давно не было ТО",
                    body = "Последнее обслуживание было $daysSince дней назад. Рекомендуется пройти плановое ТО.",
                    severity = if (daysSince > 365) InsightSeverity.CRITICAL else InsightSeverity.WARNING
                )
            )
        }
        return emptyList()
    }

    private fun detectSeasonalPattern(carId: String, expenses: List<Expense>): List<AiInsight> {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1 // 1-12

        // In Russia: month 3-4 = spring tires, 10-11 = winter tires
        val isTireChangeSeason = currentMonth in listOf(3, 4, 10, 11)
        if (!isTireChangeSeason) return emptyList()

        val tireExpenses = expenses.filter {
            it.title?.contains("шин", ignoreCase = true) == true ||
            it.description?.contains("шин", ignoreCase = true) == true ||
            it.serviceType?.name == "TIRES"
        }

        val currentYear = cal.get(Calendar.YEAR)
        val tireChangeThisYear = tireExpenses.any {
            val expCal = Calendar.getInstance().apply { timeInMillis = it.date }
            expCal.get(Calendar.YEAR) == currentYear &&
            expCal.get(Calendar.MONTH) + 1 in listOf(3, 4, 10, 11)
        }

        if (!tireChangeThisYear) {
            val season = if (currentMonth in listOf(3, 4)) "летнюю резину" else "зимнюю резину"
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.SEASONAL_TIP,
                    title = "Сезонная замена шин",
                    body = "Пришло время заменить на $season. Не забудьте также проверить давление и балансировку.",
                    severity = InsightSeverity.INFO
                )
            )
        }
        return emptyList()
    }

    private fun getMonthlyTotals(expenses: List<Expense>): List<Pair<String, Double>> {
        return expenses
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
            }
            .map { (key, list) -> key to list.sumOf { it.amount } }
            .sortedBy { it.first }
    }
}

// Extension for readable name
private val ExpenseCategory.displayName: String
    get() = when (this) {
        ExpenseCategory.FUEL -> "Топливо"
        ExpenseCategory.MAINTENANCE -> "ТО"
        ExpenseCategory.REPAIR -> "Ремонт"
        ExpenseCategory.INSURANCE -> "Страховка"
        ExpenseCategory.PARKING -> "Парковка"
        ExpenseCategory.TAX -> "Налоги"
        ExpenseCategory.TOLL -> "Платная дорога"
        ExpenseCategory.WASH -> "Мойка"
        ExpenseCategory.FINE -> "Штраф"
        ExpenseCategory.ACCESSORIES -> "Аксессуары"
        ExpenseCategory.OTHER -> "Прочее"
    }
