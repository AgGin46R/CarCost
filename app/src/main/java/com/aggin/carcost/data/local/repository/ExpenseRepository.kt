package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.ExpenseDao
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    // Create
    suspend fun insertExpense(expense: Expense): String { // ✅ Возвращает String UUID
        expenseDao.insertExpense(expense)
        return expense.id // ✅ Возвращаем UUID
    }

    // Read
    suspend fun getExpenseById(expenseId: String): Expense? { // ✅ String UUID
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

    /**
     * Запись, пришедшая С СЕРВЕРА, — без перештамповки updatedAt.
     *
     * Обычный update ставит updatedAt = сейчас. Для загрузки это ломало всё:
     * скачанная запись немедленно становилась «новее» серверной, на следующей
     * синхронизации уходила обратно UPDATE'ом, сервер ставил своё «сейчас» —
     * и так по кругу, вечно. Каждая хоть раз синхронизированная запись давала
     * лишний запрос при КАЖДОМ разворачивании приложения: у совладельца с 300
     * расходами это 300 запросов на ровном месте.
     *
     * Здесь метка времени сохраняется серверная — она и есть источник истины
     * для сравнения "кто новее".
     */
    suspend fun saveFromServer(expense: Expense) {
        expenseDao.upsertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
    }

    // Delete
    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteExpenseById(expenseId: String) { // ✅ String UUID
        expenseDao.deleteExpenseById(expenseId)
    }

    // Business logic - Date ranges

    /**
     * Траты за последние 30 дней.
     *
     * Раньше считался календарный месяц, с 1-го числа. Каждое начало месяца
     * карточка автомобиля обнулялась и показывала «—»: 2-го числа в окне лежал
     * один день. При этом экран самой машины считал скользящие 30 дней и число
     * показывал — то есть одна и та же подпись «За месяц» означала на двух
     * экранах разное.
     *
     * Скользящее окно выбрано потому, что это глазная сводка, а не отчётный
     * период: она должна быть осмысленной в любой день. Для бюджетов, которые
     * действительно привязаны к календарному месяцу, есть отдельный расчёт.
     */
    fun getLast30DaysExpenses(carId: String): Flow<Double> {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        return getTotalExpensesInDateRange(carId, thirtyDaysAgo, now)
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