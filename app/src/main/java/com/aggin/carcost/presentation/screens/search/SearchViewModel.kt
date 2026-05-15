package com.aggin.carcost.presentation.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
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
        val lowerQuery = query.lowercase()

        // Актуальный список авто (на случай если carsMap ещё не заполнен)
        val cars = carDao.getAllCars().first()
        if (carsMap.isEmpty() && cars.isNotEmpty()) {
            carsMap = cars.associateBy { it.id }
        }

        // Категории, чьё русское название содержит запрос (для поиска по "топливо", "штраф" и т.д.)
        val matchedCategories = ExpenseCategory.values()
            .filter { cat -> categoryRuName(cat).lowercase().contains(lowerQuery) }
            .toSet()

        // Загружаем все расходы по всем авто пользователя
        val allExpenses = mutableListOf<Expense>()
        for (car in cars) {
            allExpenses += expenseDao.getExpensesByCarIdSync(car.id)
        }

        // Фильтрация на стороне Kotlin — корректно работает с кириллицей любого регистра
        val results = allExpenses
            .filter { expense ->
                expense.title?.lowercase()?.contains(lowerQuery) == true
                    || expense.description?.lowercase()?.contains(lowerQuery) == true
                    || expense.location?.lowercase()?.contains(lowerQuery) == true
                    || expense.workshopName?.lowercase()?.contains(lowerQuery) == true
                    || expense.maintenanceParts?.lowercase()?.contains(lowerQuery) == true
                    || "%.0f".format(expense.amount).contains(lowerQuery)
                    || expense.category in matchedCategories
            }
            .sortedByDescending { it.date }
            .take(100)
            .map { expense -> ExpenseSearchResult(expense = expense, car = carsMap[expense.carId]) }

        _uiState.update { it.copy(results = results, isSearching = false, hasSearched = true) }
    }

    fun clearQuery() {
        _uiState.update { SearchUiState() }
        queryFlow.value = ""
    }

    companion object {
        fun categoryRuName(category: ExpenseCategory): String = when (category) {
            ExpenseCategory.FUEL        -> "топливо заправка бензин дизель"
            ExpenseCategory.MAINTENANCE -> "то техническое обслуживание"
            ExpenseCategory.REPAIR      -> "ремонт"
            ExpenseCategory.INSURANCE   -> "страховка осаго каско"
            ExpenseCategory.TAX         -> "налог"
            ExpenseCategory.PARKING     -> "парковка"
            ExpenseCategory.TOLL        -> "платная дорога"
            ExpenseCategory.WASH        -> "мойка"
            ExpenseCategory.FINE        -> "штраф"
            ExpenseCategory.ACCESSORIES -> "аксессуары"
            ExpenseCategory.OTHER       -> "другое прочее"
        }
    }
}
