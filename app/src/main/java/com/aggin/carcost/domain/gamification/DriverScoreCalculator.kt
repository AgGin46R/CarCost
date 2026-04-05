package com.aggin.carcost.domain.gamification

import com.aggin.carcost.data.local.database.entities.CategoryBudget
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import java.util.Calendar

data class DriverScore(
    val total: Int,          // 0–100
    val maintenanceScore: Int,
    val budgetScore: Int,
    val fuelScore: Int,
    val regularityScore: Int
)

/**
 * Calculates a driver score (0–100) based on maintenance regularity,
 * budget adherence, fuel efficiency, and recording frequency.
 */
object DriverScoreCalculator {

    fun calculate(
        expenses: List<Expense>,
        reminders: List<MaintenanceReminder>,
        budgets: List<CategoryBudget>,
        currentOdometer: Int
    ): DriverScore {
        val maintenance = maintenanceScore(reminders, currentOdometer)
        val budget = budgetScore(expenses, budgets)
        val fuel = fuelScore(expenses)
        val regularity = regularityScore(expenses)

        val total = (maintenance * 0.35 + budget * 0.25 + fuel * 0.20 + regularity * 0.20).toInt()
            .coerceIn(0, 100)

        return DriverScore(
            total = total,
            maintenanceScore = maintenance,
            budgetScore = budget,
            fuelScore = fuel,
            regularityScore = regularity
        )
    }

    // Score based on how many reminders are overdue
    private fun maintenanceScore(reminders: List<MaintenanceReminder>, odometer: Int): Int {
        if (reminders.isEmpty()) return 70 // neutral
        val overdueCount = reminders.count { reminder ->
            reminder.nextChangeOdometer != null && odometer >= reminder.nextChangeOdometer
        }
        return when (overdueCount) {
            0 -> 100
            1 -> 70
            2 -> 40
            else -> 10
        }
    }

    // Score based on budget adherence over last 3 months
    private fun budgetScore(expenses: List<Expense>, budgets: List<CategoryBudget>): Int {
        if (budgets.isEmpty()) return 70
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentYear = cal.get(Calendar.YEAR)

        val currentBudgets = budgets.filter { it.month == currentMonth && it.year == currentYear }
        if (currentBudgets.isEmpty()) return 70

        var exceededCount = 0
        for (budget in currentBudgets) {
            val spent = expenses.filter { e ->
                e.category == budget.category &&
                Calendar.getInstance().apply { timeInMillis = e.date }.let {
                    it.get(Calendar.MONTH) + 1 == currentMonth && it.get(Calendar.YEAR) == currentYear
                }
            }.sumOf { it.amount }
            if (spent > budget.monthlyLimit) exceededCount++
        }

        val adherenceRate = 1.0 - (exceededCount.toDouble() / currentBudgets.size)
        return (adherenceRate * 100).toInt()
    }

    // Score based on fuel consumption efficiency
    private fun fuelScore(expenses: List<Expense>): Int {
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.date }

        if (fuelExpenses.size < 3) return 70

        val consumptions = mutableListOf<Double>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val distKm = curr.odometer - prev.odometer
            if (distKm > 50 && curr.fuelLiters != null) {
                consumptions += (curr.fuelLiters / distKm) * 100.0
            }
        }

        if (consumptions.isEmpty()) return 70
        val avg = consumptions.average()

        return when {
            avg < 6.0 -> 100
            avg < 8.0 -> 85
            avg < 10.0 -> 65
            avg < 12.0 -> 45
            else -> 20
        }
    }

    // Score based on how regularly user records expenses (last 3 months)
    private fun regularityScore(expenses: List<Expense>): Int {
        val threeMonthsAgo = System.currentTimeMillis() - (90L * 86_400_000L)
        val recentExpenses = expenses.filter { it.date >= threeMonthsAgo }

        return when {
            recentExpenses.size >= 15 -> 100
            recentExpenses.size >= 8 -> 80
            recentExpenses.size >= 3 -> 55
            recentExpenses.size >= 1 -> 30
            else -> 0
        }
    }
}
