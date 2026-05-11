package com.aggin.carcost.presentation.screens.planned_expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.PlannedExpenseStatus
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class PlannedExpensesUiState(
    val plannedExpenses: List<PlannedExpense> = emptyList(),
    val plannedCount: Int = 0,
    val totalEstimatedAmount: Double = 0.0,
    val isLoading: Boolean = true
)

class PlannedExpensesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: ""

    private val database = AppDatabase.getDatabase(application)
    private val repository = PlannedExpenseRepository(database.plannedExpenseDao())

    val uiState: StateFlow<PlannedExpensesUiState> = combine(
        repository.getPlannedExpensesByCarId(carId),
        repository.getPlannedCount(carId),
        repository.getTotalEstimatedAmount(carId)
    ) { plannedExpenses, plannedCount, totalEstimatedAmount ->
        PlannedExpensesUiState(
            plannedExpenses = plannedExpenses,
            plannedCount = plannedCount,
            totalEstimatedAmount = totalEstimatedAmount,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlannedExpensesUiState(isLoading = true)
    )

    fun markAsInProgress(id: String) {
        viewModelScope.launch {
            repository.updateStatus(id, PlannedExpenseStatus.IN_PROGRESS)
        }
    }

    fun deletePlannedExpense(plannedExpense: PlannedExpense) {
        viewModelScope.launch {
            repository.deletePlannedExpense(plannedExpense)
        }
    }

    /**
     * Mark expense as COMPLETED. If it has a recurrenceType, automatically
     * creates the next occurrence with the computed next target date.
     */
    fun markAsCompleted(plannedExpense: PlannedExpense) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.markAsCompleted(
                id = plannedExpense.id,
                completedDate = now,
                actualAmount = plannedExpense.estimatedAmount ?: 0.0,
                expenseId = plannedExpense.linkedExpenseId ?: ""
            )

            // Auto-create next occurrence for recurring expenses
            val recurrenceType = plannedExpense.recurrenceType ?: return@launch
            val anchorDate = plannedExpense.recurrenceAnchorDate
                ?: plannedExpense.targetDate
                ?: now
            val nextDate = computeNextDate(anchorDate, recurrenceType)

            val nextExpense = plannedExpense.copy(
                id = UUID.randomUUID().toString(),
                status = PlannedExpenseStatus.PLANNED,
                completedDate = null,
                actualAmount = null,
                linkedExpenseId = null,
                targetDate = nextDate,
                recurrenceAnchorDate = nextDate,
                createdAt = now,
                updatedAt = now,
                isSynced = false,
                sortOrder = 0
            )
            repository.insertPlannedExpense(nextExpense)
        }
    }

    /** Persist drag-reordered list to DB by updating sortOrder for each item. */
    fun persistOrder(items: List<PlannedExpense>) {
        viewModelScope.launch {
            items.forEachIndexed { index, item ->
                database.plannedExpenseDao().updateSortOrder(item.id, index + 1)
            }
        }
    }

    private fun computeNextDate(anchorMs: Long, recurrenceType: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = anchorMs
        when (recurrenceType) {
            "DAILY"   -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY"  -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            "YEARLY"  -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}