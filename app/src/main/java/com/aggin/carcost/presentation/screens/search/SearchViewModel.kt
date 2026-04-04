package com.aggin.carcost.presentation.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseSearchResult(
    val expense: Expense,
    val car: Car?
)

data class SearchUiState(
    val query: String = "",
    val results: List<ExpenseSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()
    private val carDao = db.carDao()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    // Карта carId → Car для быстрого поиска
    private var carsMap: Map<String, Car> = emptyMap()

    init {
        viewModelScope.launch {
            carDao.getAllCars().collect { cars ->
                carsMap = cars.associateBy { it.id }
            }
        }

        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false) }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        val expenses = expenseDao.searchExpenses(query)
        val results = expenses.map { expense ->
            ExpenseSearchResult(expense = expense, car = carsMap[expense.carId])
        }
        _uiState.update { it.copy(results = results, isSearching = false, hasSearched = true) }
    }

    fun clearQuery() {
        _uiState.update { SearchUiState() }
        queryFlow.value = ""
    }
}
