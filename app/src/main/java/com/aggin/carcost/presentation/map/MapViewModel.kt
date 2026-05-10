package com.aggin.carcost.presentation.screens.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MapUiState(
    /** All geo-tagged expenses (unfiltered) */
    val allExpenses: List<Expense> = emptyList(),
    /** Currently visible expenses after category filter */
    val expenses: List<Expense> = emptyList(),
    /** Categories to display (empty = show all) */
    val selectedCategories: Set<ExpenseCategory> = emptySet(),
    /** Categories that have at least one geo-tagged expense */
    val availableCategories: Set<ExpenseCategory> = emptySet(),
    val isLoading: Boolean = true
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val expenseRepository = ExpenseRepository(database.expenseDao())

    private val _selectedCarId = MutableStateFlow<String?>(null)
    private val _selectedCategories = MutableStateFlow<Set<ExpenseCategory>>(emptySet())

    val uiState: StateFlow<MapUiState> = combine(
        _selectedCarId.flatMapLatest { carId ->
            if (carId != null) {
                expenseRepository.getExpensesByCarId(carId).map { expenses ->
                    expenses.filter { it.latitude != null && it.longitude != null }
                }
            } else {
                flowOf(emptyList())
            }
        },
        _selectedCategories
    ) { allGeoExpenses, selectedCats ->
        val available = allGeoExpenses.map { it.category }.toSet()
        val filtered = if (selectedCats.isEmpty()) allGeoExpenses
                       else allGeoExpenses.filter { it.category in selectedCats }
        MapUiState(
            allExpenses = allGeoExpenses,
            expenses = filtered,
            selectedCategories = selectedCats,
            availableCategories = available,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapUiState()
    )

    fun setCarId(carId: String) {
        _selectedCarId.value = carId
    }

    fun toggleCategory(category: ExpenseCategory) {
        val current = _selectedCategories.value
        _selectedCategories.value = if (category in current) current - category else current + category
    }

    fun clearFilter() {
        _selectedCategories.value = emptySet()
    }
}