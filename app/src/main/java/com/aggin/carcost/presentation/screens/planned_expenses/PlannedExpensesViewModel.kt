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
}