package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.ExpenseDao
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    // Create
    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    // Read
    suspend fun getExpenseById(expenseId: Long): Expense? {
        return expenseDao.getExpenseById(expenseId)
    }

    fun getExpensesByCarId(carId: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByCarId(carId)
    }

    fun getExpensesByCategory(carId: String, category: ExpenseCategory): Flow<List<Expense>> {
        return expenseDao.getExpensesByCategory(carId, category)
    }

    fun getExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesInDateRange(carId, startDate, endDate)
    }

    fun getRecentExpenses(carId: String, limit: Int = 10): Flow<List<Expense>> {
        return expenseDao.getRecentExpenses(carId, limit)
    }

    // Statistics
    fun getTotalExpenses(carId: String): Flow<Double> {
        return expenseDao.getTotalExpenses(carId).map { it ?: 0.0 }
    }

    fun getTotalExpensesByCategory(carId: String, category: ExpenseCategory): Flow<Double> {
        return expenseDao.getTotalExpensesByCategory(carId, category).map { it ?: 0.0 }
    }

    fun getTotalExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<Double> {
        return expenseDao.getTotalExpensesInDateRange(carId, startDate, endDate).map { it ?: 0.0 }
    }

    fun getExpenseCount(carId: String): Flow<Int> {
        return expenseDao.getExpenseCount(carId)
    }

    // Fuel-specific
    fun getFullTankRefuels(carId: String, limit: Int = 10): Flow<List<Expense>> {
        return expenseDao.getFullTankRefuels(carId, limit)
    }

    fun calculateMonthlyExpenses(expenses: List<Expense>): Double {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        return expenses.filter {
            calendar.timeInMillis = it.date
            calendar.get(Calendar.YEAR) == currentYear && calendar.get(Calendar.MONTH) == currentMonth
        }.sumOf { it.amount }
    }
    // Update
    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
    }

    // Delete
    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteExpenseById(expenseId: Long) {
        expenseDao.deleteExpenseById(expenseId)
    }

    // Business logic - Date ranges
    fun getMonthlyExpenses(carId: String): Flow<Double> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis
        val endOfMonth = System.currentTimeMillis()

        return getTotalExpensesInDateRange(carId, startOfMonth, endOfMonth)
    }

    fun getYearlyExpenses(carId: String): Flow<Double> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfYear = calendar.timeInMillis
        val endOfYear = System.currentTimeMillis()

        return getTotalExpensesInDateRange(carId, startOfYear, endOfYear)
    }
}