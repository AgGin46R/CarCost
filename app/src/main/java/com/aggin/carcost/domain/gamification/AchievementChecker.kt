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
        // Считаем по ВСЕМ машинам человека, а не по одной.
        //
        // Раньше здесь брались расходы только текущего автомобиля, и владелец
        // трёх машин с пятью записями в каждой не получал «10 расходов» никогда,
        // хотя описание обещает именно общее число. Экран достижений при этом
        // считал правильно — по всем машинам, — и показывал «10 из 10» под
        // закрытым значком.
        val expenses = allExpensesOf(carId)
        val totalCount = expenses.size

        // FIRST_EXPENSE
        if (totalCount >= 1) tryUnlock(userId, AchievementType.FIRST_EXPENSE)

        // EXPENSES_10
        if (totalCount >= 10) tryUnlock(userId, AchievementType.EXPENSES_10)

        // EXPENSES_50
        if (totalCount >= 50) tryUnlock(userId, AchievementType.EXPENSES_50)

        // EXPENSES_100
        if (totalCount >= 100) tryUnlock(userId, AchievementType.EXPENSES_100)

        // ECO_DRIVER — avg fuel consumption below 8 L/100km for last 3 months
        checkEcoDriver(userId, carId)

        // BUDGET_MASTER — did not exceed budget for 3 consecutive months
        categoryBudgetDao?.let { checkBudgetMaster(userId, carId, it) }

        // REGULAR_MAINTENANCE — 5+ planned maintenance entries
        checkRegularMaintenance(userId, carId)

        // FUEL_VETERAN — 20+ fuel fill-ups
        val fuelCount = expenses.count { it.category == ExpenseCategory.FUEL }
        if (fuelCount >= 20) tryUnlock(userId, AchievementType.FUEL_VETERAN)

        // NIGHT_DRIVER — expense added between 23:00 and 05:00
        val lastExpense = expenses.maxByOrNull { it.createdAt }
        if (lastExpense != null) {
            val hour = Calendar.getInstance().apply { timeInMillis = lastExpense.createdAt }
                .get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour < 5) tryUnlock(userId, AchievementType.NIGHT_DRIVER)
        }

        // PHOTO_COLLECTOR — 10+ expenses with receipt photo
        val photoCount = expenses.count { it.receiptPhotoUri != null }
        if (photoCount >= 10) tryUnlock(userId, AchievementType.PHOTO_COLLECTOR)

        // WORKSHOP_REGULAR — visited same workshop 5+ times
        val workshopCounts = expenses
            .mapNotNull { it.workshopName?.trim()?.lowercase() }
            .groupingBy { it }
            .eachCount()
        if (workshopCounts.any { (_, count) -> count >= 5 }) {
            tryUnlock(userId, AchievementType.WORKSHOP_REGULAR)
        }

        // HIGH_MILEAGE — odometer reached 100 000 km
        val maxOdometer = expenses.maxOfOrNull { it.odometer } ?: 0
        if (maxOdometer >= 100_000) tryUnlock(userId, AchievementType.HIGH_MILEAGE)
    }

    /**
     * Check MULTI_CAR: user has 2+ cars. Call after adding a car.
     */
    suspend fun checkAfterCarAdded(userId: String) {
        val activeCars = carDao?.getAllActiveCarsSync() ?: return
        if (activeCars.size >= 2) tryUnlock(userId, AchievementType.MULTI_CAR)
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

    /**
     * Все расходы человека. Если список машин недоступен (carDao не передан),
     * честно откатываемся к одной машине — это хуже, но предсказуемо.
     */
    private suspend fun allExpensesOf(fallbackCarId: String): List<com.aggin.carcost.data.local.database.entities.Expense> {
        val cars = carDao?.getAllActiveCarsSync()
        if (cars.isNullOrEmpty()) return expenseDao.getExpensesByCar(fallbackCarId).first()
        return cars.flatMap { expenseDao.getExpensesByCarIdSync(it.id) }
    }

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
        // Расчёт переехал в AchievementProgressCalculator: та же формула нужна
        // экрану достижений, а две копии уже успели разойтись — здесь и там
        // расход считался без проверки «до полного бака», то есть неверно.
        val expenses = allExpensesOf(carId)
        val months = AchievementProgressCalculator.ecoMonths(expenses)
        if (months >= AchievementProgressCalculator.ECO_MONTHS_REQUIRED) {
            tryUnlock(userId, AchievementType.ECO_DRIVER)
        }
    }
}
