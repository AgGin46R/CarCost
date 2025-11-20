package com.aggin.carcost.presentation.screens.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MapUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val expenseRepository = ExpenseRepository(database.expenseDao())

    private val _selectedCarId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<MapUiState> = _selectedCarId
        .flatMapLatest { carId ->
            if (carId != null) {
                expenseRepository.getExpensesByCarId(carId).map { expenses ->
                    // Фильтруем только расходы с координатами
                    MapUiState(
                        expenses = expenses.filter { it.latitude != null && it.longitude != null },
                        isLoading = false
                    )
                }
            } else {
                flowOf(MapUiState(isLoading = false))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MapUiState()
        )

    fun setCarId(carId: Long) {
        _selectedCarId.value = carId
    }
}