package com.aggin.carcost.domain.gamification

import com.aggin.carcost.data.local.database.dao.AchievementDao
import com.aggin.carcost.data.local.database.dao.ExpenseDao
import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.AchievementType
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.first

/**
 * Checks if any new achievements should be unlocked after user actions.
 * Call from ViewModels after adding expenses/documents/trips.
 */
class AchievementChecker(
    private val achievementDao: AchievementDao,
    private val expenseDao: ExpenseDao
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
        checkEcoDriver(userId, expenses)
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

    private suspend fun tryUnlock(userId: String, type: AchievementType) {
        if (!achievementDao.hasAchievement(userId, type)) {
            achievementDao.insert(Achievement(userId = userId, type = type))
        }
    }

    private suspend fun checkEcoDriver(userId: String, expenses: List<com.aggin.carcost.data.local.database.entities.Expense>) {
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.date }

        if (fuelExpenses.size < 5) return

        val consumptions = mutableListOf<Double>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val distKm = curr.odometer - prev.odometer
            if (distKm > 50 && curr.fuelLiters != null) {
                consumptions += (curr.fuelLiters / distKm) * 100.0
            }
        }

        if (consumptions.size >= 3 && consumptions.takeLast(3).average() < 8.0) {
            tryUnlock(userId, AchievementType.ECO_DRIVER)
        }
    }
}
