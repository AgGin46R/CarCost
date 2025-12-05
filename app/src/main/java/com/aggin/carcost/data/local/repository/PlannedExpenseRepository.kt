package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.PlannedExpenseDao
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.PlannedExpenseStatus
import com.aggin.carcost.data.local.database.entities.PlannedExpensePriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlannedExpenseRepository(private val dao: PlannedExpenseDao) {

    // Create
    suspend fun insertPlannedExpense(plannedExpense: PlannedExpense): String {
        dao.insertPlannedExpense(plannedExpense)
        return plannedExpense.id
    }

    // Read
    suspend fun getPlannedExpenseById(id: String): PlannedExpense? {
        return dao.getPlannedExpenseById(id)
    }

    fun getPlannedExpenseByIdFlow(id: String): Flow<PlannedExpense?> {
        return dao.getPlannedExpenseByIdFlow(id)
    }

    fun getPlannedExpensesByCarId(carId: String): Flow<List<PlannedExpense>> {
        return dao.getPlannedExpensesByCarId(carId)
    }

    fun getActivePlannedExpenses(carId: String): Flow<List<PlannedExpense>> {
        return dao.getActivePlannedExpenses(carId)
    }

    fun getCompletedPlannedExpenses(carId: String): Flow<List<PlannedExpense>> {
        return dao.getCompletedPlannedExpenses(carId)
    }

    fun getPlannedExpensesByStatus(carId: String, status: PlannedExpenseStatus): Flow<List<PlannedExpense>> {
        return dao.getPlannedExpensesByStatus(carId, status)
    }

    fun getPlannedExpensesByPriority(carId: String, priority: PlannedExpensePriority): Flow<List<PlannedExpense>> {
        return dao.getPlannedExpensesByPriority(carId, priority)
    }

    fun getUpcomingPlannedExpenses(carId: String, date: Long): Flow<List<PlannedExpense>> {
        return dao.getUpcomingPlannedExpenses(carId, date)
    }

    // Statistics
    fun getPlannedCount(carId: String): Flow<Int> {
        return dao.getPlannedCount(carId)
    }

    fun getTotalEstimatedAmount(carId: String): Flow<Double> {
        return dao.getTotalEstimatedAmount(carId).map { it ?: 0.0 }
    }

    fun getTotalActualAmount(carId: String): Flow<Double> {
        return dao.getTotalActualAmount(carId).map { it ?: 0.0 }
    }

    // Update
    suspend fun updatePlannedExpense(plannedExpense: PlannedExpense) {
        dao.updatePlannedExpense(plannedExpense.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateStatus(id: String, status: PlannedExpenseStatus) {
        dao.updateStatus(id, status)
    }

    suspend fun markAsCompleted(
        id: String,
        completedDate: Long,
        actualAmount: Double,
        expenseId: String
    ) {
        dao.markAsCompleted(id, completedDate, actualAmount, expenseId)
    }

    suspend fun updateSyncStatus(id: String, isSynced: Boolean) {
        dao.updateSyncStatus(id, isSynced)
    }

    // Delete
    suspend fun deletePlannedExpense(plannedExpense: PlannedExpense) {
        dao.deletePlannedExpense(plannedExpense)
    }

    suspend fun deletePlannedExpenseById(id: String) {
        dao.deletePlannedExpenseById(id)
    }
}