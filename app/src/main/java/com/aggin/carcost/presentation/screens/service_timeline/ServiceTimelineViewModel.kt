package com.aggin.carcost.presentation.screens.service_timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TimelineEvent(
    val expense: Expense,
    val isFirst: Boolean = false,
    val isLast: Boolean = false
)

data class ServiceTimelineUiState(
    val car: Car? = null,
    val events: List<TimelineEvent> = emptyList(),
    val totalServiceExpenses: Double = 0.0,
    val serviceCount: Int = 0,
    val isLoading: Boolean = true
)

class ServiceTimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()

    private val _uiState = MutableStateFlow(ServiceTimelineUiState())
    val uiState: StateFlow<ServiceTimelineUiState> = _uiState.asStateFlow()

    fun load(carId: String) {
        viewModelScope.launch {
            combine(
                carDao.getCarByIdFlow(carId),
                expenseDao.getExpensesByCarId(carId)
            ) { car, expenses ->
                car to expenses
            }.collect { (car, expenses) ->
                // Только ТО и ремонт, сортируем по дате DESC (новейшие вверху)
                val serviceExpenses = expenses
                    .filter { it.category == ExpenseCategory.MAINTENANCE || it.category == ExpenseCategory.REPAIR }
                    .sortedByDescending { it.date }

                val events = serviceExpenses.mapIndexed { index, expense ->
                    TimelineEvent(
                        expense = expense,
                        isFirst = index == 0,
                        isLast = index == serviceExpenses.lastIndex
                    )
                }

                _uiState.update {
                    it.copy(
                        car = car,
                        events = events,
                        totalServiceExpenses = serviceExpenses.sumOf { e -> e.amount },
                        serviceCount = serviceExpenses.size,
                        isLoading = false
                    )
                }
            }
        }
    }
}
