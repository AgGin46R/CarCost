package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense) // ✅ Void - не возвращает ID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    // READ
    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): Expense? // ✅ String UUID

    @Query("SELECT * FROM expenses WHERE carId = :carId")
    fun getExpensesByCar(carId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    fun getExpensesByCarId(carId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC")
    fun getExpensesByCategory(carId: String, category: ExpenseCategory): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC LIMIT :limit")
    fun getRecentExpenses(carId: String, limit: Int = 10): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC LIMIT 1")
    fun getLastExpenseByCategory(carId: String, category: ExpenseCategory): Flow<Expense?>

    // STATISTICS
    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId")
    fun getTotalExpenses(carId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND category = :category")
    fun getTotalExpensesByCategory(carId: String, category: ExpenseCategory): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM expenses WHERE carId = :carId")
    fun getExpenseCount(carId: String): Flow<Int>

    // FUEL-SPECIFIC
    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = 'FUEL' AND isFullTank = 1 ORDER BY date DESC LIMIT :limit")
    fun getFullTankRefuels(carId: String, limit: Int = 10): Flow<List<Expense>>

    // UPDATE
    @Update
    suspend fun updateExpense(expense: Expense)

    @Update
    suspend fun updateExpenses(expenses: List<Expense>)

    // DELETE
    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String) // ✅ String UUID

    @Query("DELETE FROM expenses WHERE carId = :carId")
    suspend fun deleteExpensesByCarId(carId: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses() // ✅ Удалить все расходы
}