package com.aggin.carcost.domain.gamification

import com.aggin.carcost.data.local.database.dao.AchievementDao
import com.aggin.carcost.data.local.database.dao.CarDao
import com.aggin.carcost.data.local.database.dao.CategoryBudgetDao
import com.aggin.carcost.data.local.database.dao.ExpenseDao
import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.AchievementType
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Checks if any new achievements should be unlocked after user actions.
 * Call from ViewModels after adding expenses/documents/trips.
 */
class AchievementChecker(
    private val achievementDao: AchievementDao,
    private val expenseDao: ExpenseDao,
    private val categoryBudgetDao: CategoryBudgetDao? = null,
    private val carDao: CarDao? = null
) {

    /**
     * Check all expense-related achievements for a given user + car.
     */
    suspend fun checkAfterExpenseAdded(userId: String, carId: String) {
        val expenses = expenseDao.getExpensesByCar(carId).first()
        val totalCount = expenses.size

        // FIRST_EXPENSE
        if (totalCount >= 1) tryUnlock(userId, AchievementType.FIRST_EXPENSE)

        // EXPENSES_10
        if (totalCount >= 10) tryUnlock(userId, AchievementType.EXPENSES_10)

        // EXPENSES_100
        if (totalCount >= 100) tryUnlock(userId, AchievementType.EXPENSES_100)

        // ECO_DRIVER — avg fuel consumption below 8 L/100km for last 3 months
        checkEcoDriver(userId, carId)

        // BUDGET_MASTER — did not exceed budget for 3 consecutive months
        categoryBudgetDao?.let { checkBudgetMaster(userId, carId, it) }

        // REGULAR_MAINTENANCE — 5+ planned maintenance entries
        checkRegularMaintenance(userId, carId)
    }

    suspend fun checkAfterDocumentAdded(userId: String) {
        tryUnlock(userId, AchievementType.FIRST_DOCUMENT)
    }

    suspend fun checkAfterTripRecorded(userId: String) {
        tryUnlock(userId, AchievementType.TRIP_TRACKER)
    }

    suspend fun checkAfterGoalCompleted(userId: String) {
        tryUnlock(userId, AchievementType.SAVINGS_GOAL_COMPLETE)
    }

    /**
     * Check YEAR_OWNER: user has had a car (or expense) for 365+ days.
     * Uses the oldest car's createdAt, falls back to oldest expense.
     */
    suspend fun checkYearOwner(userId: String) {
        val now = System.currentTimeMillis()
        val threshold = 365L * 86_400_000L

        val oldestCarCreatedAt = carDao?.getAllActiveCarsSync()
            ?.minOfOrNull { it.createdAt }
        if (oldestCarCreatedAt != null && (now - oldestCarCreatedAt) >= threshold) {
            tryUnlock(userId, AchievementType.YEAR_OWNER)
            return
        }

        // Fallback: oldest expense for any car the user has
        val allCars = carDao?.getAllActiveCarsSync() ?: return
        val allExpenses = mutableListOf<com.aggin.carcost.data.local.database.entities.Expense>()
        for (car in allCars) {
            allExpenses.addAll(expenseDao.getExpensesByCarIdSync(car.id))
        }
        val oldestExpenseAt = allExpenses.minOfOrNull { it.createdAt }
        if (oldestExpenseAt != null && (now - oldestExpenseAt) >= threshold) {
            tryUnlock(userId, AchievementType.YEAR_OWNER)
        }
    }

    /**
     * BUDGET_MASTER: For each of the last 3 complete calendar months, check that
     * the total expenses per category did NOT exceed the set monthly budget.
     * All 3 months must have at least one budget set and all must be under limit.
     */
    suspend fun checkBudgetMaster(
        userId: String,
        carId: String,
        budgetDao: CategoryBudgetDao
    ) {
        val now = Calendar.getInstance()
        // We check the 3 most recent complete months (current month is not yet finished)
        val monthsToCheck = mutableListOf<Pair<Int, Int>>() // (month 1-12, year)
        val cal = Calendar.getInstance().apply {
            // Start from previous month
            add(Calendar.MONTH, -1)
        }
        repeat(3) {
            monthsToCheck.add(Pair(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)))
            cal.add(Calendar.MONTH, -1)
        }

        var allMonthsPassed = true
        var atLeastOneMonthHadBudgets = false

        for ((month, year) in monthsToCheck) {
            val budgets = budgetDao.getBudgetsSync(carId, month, year)
            if (budgets.isEmpty()) {
                // No budgets set for this month — skip (don't fail, but don't count)
                continue
            }
            atLeastOneMonthHadBudgets = true

            // Get start and end timestamps for this month
            val start = Calendar.getInstance().apply {
                set(year, month - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                set(year, month - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis

            for (budget in budgets) {
                val totalSpent = expenseDao.getTotalByCategoryAndPeriod(
                    carId, budget.category, start, end
                ) ?: 0.0
                if (totalSpent > budget.monthlyLimit) {
                    allMonthsPassed = false
                    break
                }
            }
            if (!allMonthsPassed) break
        }

        if (allMonthsPassed && atLeastOneMonthHadBudgets) {
            tryUnlock(userId, AchievementType.BUDGET_MASTER)
        }
    }

    /**
     * REGULAR_MAINTENANCE: User has logged 5+ maintenance expenses with a specific service type.
     */
    suspend fun checkRegularMaintenance(userId: String, carId: String) {
        val maintenanceExpenses = expenseDao.getExpensesByCarIdSync(carId)
            .filter { it.category == ExpenseCategory.MAINTENANCE && it.serviceType != null }
        if (maintenanceExpenses.size >= 5) {
            tryUnlock(userId, AchievementType.REGULAR_MAINTENANCE)
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun tryUnlock(userId: String, type: AchievementType) {
        if (!achievementDao.hasAchievement(userId, type)) {
            achievementDao.insert(Achievement(userId = userId, type = type))
        }
    }

    /**
     * ECO_DRIVER fix: check last 3 complete calendar months where fuel consumption
     * (L/100km, computed between consecutive full-tank fill-ups) averaged below 8.0.
     */
    private suspend fun checkEcoDriver(userId: String, carId: String) {
        val fuelExpenses = expenseDao.getExpensesByCarIdSync(carId)
            .filter { it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.date }

        if (fuelExpenses.size < 2) return

        // Compute L/100km for each consecutive pair, bucket by month of LATER fill-up
        data class MonthKey(val year: Int, val month: Int)

        val consumptionsByMonth = mutableMapOf<MonthKey, MutableList<Double>>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val distKm = curr.odometer - prev.odometer
            if (distKm > 50 && curr.fuelLiters != null) {
                val consumption = (curr.fuelLiters / distKm) * 100.0
                val cal = Calendar.getInstance().apply { timeInMillis = curr.date }
                val key = MonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                consumptionsByMonth.getOrPut(key) { mutableListOf() }.add(consumption)
            }
        }

        if (consumptionsByMonth.size < 3) return

        // Sort months descending, skip current incomplete month, take last 3
        val currentCal = Calendar.getInstance()
        val currentMonthKey = MonthKey(currentCal.get(Calendar.YEAR), currentCal.get(Calendar.MONTH))

        val sortedMonths = consumptionsByMonth.keys
            .filter { it != currentMonthKey }
            .sortedWith(compareByDescending<MonthKey> { it.year }.thenByDescending { it.month })

        if (sortedMonths.size < 3) return

        val last3 = sortedMonths.take(3)
        val allBelow8 = last3.all { key ->
            val avg = consumptionsByMonth[key]!!.average()
            avg < 8.0
        }

        if (allBelow8) tryUnlock(userId, AchievementType.ECO_DRIVER)
    }
}
