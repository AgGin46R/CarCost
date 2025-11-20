package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    // READ
    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: Long): Expense?

    // --- ДОБАВЛЕННЫЙ МЕТОД ---
    // Используется в ProfileViewModel для получения всех расходов для автомобиля
    @Query("SELECT * FROM expenses WHERE carId = :carId")
    fun getExpensesByCar(carId: Long): Flow<List<Expense>>
    // --- КОНЕЦ ДОБАВЛЕННОГО МЕТОДА ---

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    fun getExpensesByCarId(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC")
    fun getExpensesByCategory(carId: Long, category: ExpenseCategory): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesInDateRange(carId: Long, startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC LIMIT :limit")
    fun getRecentExpenses(carId: Long, limit: Int = 10): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC LIMIT 1")
    suspend fun getLastExpenseByCategory(carId: Long, category: ExpenseCategory): Expense?

    // Статистика
    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId")
    fun getTotalExpenses(carId: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND category = :category")
    fun getTotalExpensesByCategory(carId: Long, category: ExpenseCategory): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpensesInDateRange(carId: Long, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM expenses WHERE carId = :carId")
    fun getExpenseCount(carId: Long): Flow<Int>

    // Расчет расхода топлива
    @Query("""
        SELECT * FROM expenses 
        WHERE carId = :carId AND category = 'FUEL' AND isFullTank = 1
        ORDER BY date DESC 
        LIMIT :limit
    """)
    fun getFullTankRefuels(carId: Long, limit: Int = 10): Flow<List<Expense>>

    // UPDATE
    @Update
    suspend fun updateExpense(expense: Expense)

    // DELETE
    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Long)

    @Query("DELETE FROM expenses WHERE carId = :carId")
    suspend fun deleteExpensesByCarId(carId: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}