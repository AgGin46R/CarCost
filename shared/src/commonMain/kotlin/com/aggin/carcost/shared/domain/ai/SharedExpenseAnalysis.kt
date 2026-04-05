package com.aggin.carcost.shared.domain.ai

/**
 * Platform-independent expense analysis models and utilities.
 * Used by both Android and iOS.
 */

data class SharedExpense(
    val id: String,
    val category: String,
    val amount: Double,
    val dateMs: Long,
    val odometer: Int,
    val fuelLiters: Double? = null
)

data class SharedInsight(
    val type: String,
    val title: String,
    val body: String,
    val severity: String  // INFO, WARNING, CRITICAL
)

object SharedExpenseAnalysisEngine {

    fun detectCostSpike(expenses: List<SharedExpense>, category: String): SharedInsight? {
        val catExpenses = expenses.filter { it.category == category }.sortedBy { it.dateMs }
        if (catExpenses.size < 4) return null

        val monthlyTotals = getMonthlyTotals(catExpenses)
        if (monthlyTotals.size < 3) return null

        val historical = monthlyTotals.dropLast(1)
        val avg = historical.map { it.second }.average()
        val lastMonth = monthlyTotals.last().second

        if (avg > 0 && lastMonth > avg * 2.0) {
            val percent = ((lastMonth / avg - 1) * 100).toInt()
            return SharedInsight(
                type = "COST_SPIKE",
                title = "Рост расходов: $category",
                body = "Расходы выросли на $percent% (${lastMonth.toInt()} vs ${avg.toInt()} руб.)",
                severity = if (percent > 100) "WARNING" else "INFO"
            )
        }
        return null
    }

    fun calculateFuelConsumption(expenses: List<SharedExpense>): Double? {
        val fuelExpenses = expenses
            .filter { it.category == "FUEL" && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.dateMs }

        if (fuelExpenses.size < 2) return null

        val consumptions = mutableListOf<Double>()
        for (i in 1 until fuelExpenses.size) {
            val distKm = fuelExpenses[i].odometer - fuelExpenses[i - 1].odometer
            val liters = fuelExpenses[i].fuelLiters ?: continue
            if (distKm > 50) consumptions += (liters / distKm) * 100.0
        }

        return if (consumptions.isNotEmpty()) consumptions.average() else null
    }

    private fun getMonthlyTotals(expenses: List<SharedExpense>): List<Pair<String, Double>> {
        return expenses
            .groupBy { dateToYearMonth(it.dateMs) }
            .map { (key, list) -> key to list.sumOf { it.amount } }
            .sortedBy { it.first }
    }

    private fun dateToYearMonth(ms: Long): String {
        // Simple implementation without Calendar (platform-agnostic)
        val days = ms / 86_400_000L
        val years = days / 365
        val dayOfYear = (days % 365).toInt()
        val month = (dayOfYear / 30).coerceIn(0, 11)
        return "$years-$month"
    }
}
