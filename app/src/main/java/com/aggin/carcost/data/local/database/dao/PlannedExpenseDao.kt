package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.PlannedExpenseStatus
import com.aggin.carcost.data.local.database.entities.PlannedExpensePriority
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedExpenseDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedExpense(plannedExpense: PlannedExpense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedExpenses(plannedExpenses: List<PlannedExpense>)

    // READ
    @Query("SELECT * FROM planned_expenses WHERE id = :id")
    suspend fun getPlannedExpenseById(id: String): PlannedExpense?

    @Query("SELECT * FROM planned_expenses WHERE id = :id")
    fun getPlannedExpenseByIdFlow(id: String): Flow<PlannedExpense?>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId ORDER BY priority DESC, targetDate ASC")
    fun getPlannedExpensesByCarId(carId: String): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId AND status = :status ORDER BY priority DESC, targetDate ASC")
    fun getPlannedExpensesByStatus(carId: String, status: PlannedExpenseStatus): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId AND priority = :priority ORDER BY targetDate ASC")
    fun getPlannedExpensesByPriority(carId: String, priority: PlannedExpensePriority): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId AND status = 'PLANNED' ORDER BY priority DESC, targetDate ASC")
    fun getActivePlannedExpenses(carId: String): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId AND status = 'COMPLETED' ORDER BY completedDate DESC")
    fun getCompletedPlannedExpenses(carId: String): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE carId = :carId AND targetDate <= :date AND status = 'PLANNED' ORDER BY priority DESC")
    fun getUpcomingPlannedExpenses(carId: String, date: Long): Flow<List<PlannedExpense>>

    // Статистика
    @Query("SELECT COUNT(*) FROM planned_expenses WHERE carId = :carId AND status = 'PLANNED'")
    fun getPlannedCount(carId: String): Flow<Int>

    @Query("SELECT SUM(estimatedAmount) FROM planned_expenses WHERE carId = :carId AND status = 'PLANNED'")
    fun getTotalEstimatedAmount(carId: String): Flow<Double?>

    @Query("SELECT SUM(actualAmount) FROM planned_expenses WHERE carId = :carId AND status = 'COMPLETED'")
    fun getTotalActualAmount(carId: String): Flow<Double?>

    // UPDATE
    @Update
    suspend fun updatePlannedExpense(plannedExpense: PlannedExpense)

    @Query("UPDATE planned_expenses SET status = :status, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: String, status: PlannedExpenseStatus, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE planned_expenses SET status = 'COMPLETED', completedDate = :completedDate, actualAmount = :actualAmount, linkedExpenseId = :expenseId, updatedAt = :timestamp WHERE id = :id")
    suspend fun markAsCompleted(
        id: String,
        completedDate: Long,
        actualAmount: Double,
        expenseId: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE planned_expenses SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean)

    // DELETE
    @Delete
    suspend fun deletePlannedExpense(plannedExpense: PlannedExpense)

    @Query("DELETE FROM planned_expenses WHERE id = :id")
    suspend fun deletePlannedExpenseById(id: String)

    @Query("DELETE FROM planned_expenses WHERE carId = :carId")
    suspend fun deleteAllPlannedExpensesByCarId(carId: String)
}