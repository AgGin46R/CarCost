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

/**
 * Фильтр поиска.
 *
 * Раньше фильтры существовали только внутри одного автомобиля
 * (ExpenseFilterDialog на экране деталей), а глобальный поиск умел лишь искать
 * подстроку. Теперь и то и другое в одном месте.
 */
data class SearchFilter(
    /** null — искать по всем автомобилям */
    val carId: String? = null,
    val categories: Set<ExpenseCategory> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    val isActive: Boolean
        get() = carId != null || categories.isNotEmpty() || startDate != null ||
            endDate != null || minAmount != null || maxAmount != null

    val activeCount: Int
        get() = listOfNotNull(
            carId, categories.takeIf { it.isNotEmpty() }, startDate, endDate, minAmount, maxAmount
        ).size
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val cars: List<Car> = emptyList(),
    val results: List<ExpenseSearchResult> = emptyList(),
    /** Сколько записей подошло всего — выдача обрезается до RESULT_LIMIT */
    val totalMatches: Int = 0,
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

    private val filterFlow = MutableStateFlow(SearchFilter())

    init {
        viewModelScope.launch {
            carDao.getAllCars().collect { cars ->
                carsMap = cars.associateBy { it.id }
                _uiState.update { it.copy(cars = cars) }
            }
        }

        viewModelScope.launch {
            combine(
                queryFlow.debounce(300).distinctUntilChanged(),
                filterFlow
            ) { query, filter -> query to filter }
                .collect { (query, filter) ->
                    // Ищем, если есть внятный запрос ИЛИ выставлен хоть один фильтр:
                    // фильтр сам по себе — это полноценный сценарий «покажи всё
                    // топливо за март по этой машине»
                    if (query.length >= 2 || filter.isActive) {
                        performSearch(query, filter)
                    } else {
                        _uiState.update {
                            it.copy(results = emptyList(), totalMatches = 0, hasSearched = false)
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun applyFilter(filter: SearchFilter) {
        _uiState.update { it.copy(filter = filter) }
        filterFlow.value = filter
    }

    fun clearFilter() = applyFilter(SearchFilter())

    private suspend fun performSearch(query: String, filter: SearchFilter) {
        com.aggin.carcost.data.analytics.Analytics.searchUsed()
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

        // Отбор целиком в SQL. Раньше сюда поднимались все расходы всех машин —
        // по запросу на машину — и фильтровались в Kotlin, хотя до выдачи
        // доходила максимум сотня строк.
        //
        // Берём на одну строку больше предела: если она пришла, значит совпадений
        // больше, чем показано, и об этом надо сказать честно.
        val matched = expenseDao.searchWithFilters(
            query = lowerQuery,
            queryTooShort = if (lowerQuery.length < 2) 1 else 0,
            carId = filter.carId,
            categories = filter.categories.toList(),
            hasCategoryFilter = if (filter.categories.isEmpty()) 0 else 1,
            matchedCategories = matchedCategories.toList(),
            hasMatchedCategories = if (matchedCategories.isEmpty()) 0 else 1,
            startDate = filter.startDate,
            endDate = filter.endDate,
            minAmount = filter.minAmount,
            maxAmount = filter.maxAmount,
            limit = RESULT_LIMIT + 1
        )

        val results = matched
            .take(RESULT_LIMIT)
            .map { expense -> ExpenseSearchResult(expense = expense, car = carsMap[expense.carId]) }

        _uiState.update {
            it.copy(
                results = results,
                totalMatches = matched.size,
                isSearching = false,
                hasSearched = true
            )
        }
    }

    fun clearQuery() {
        val cars = _uiState.value.cars
        _uiState.update { SearchUiState(cars = cars) }
        queryFlow.value = ""
        filterFlow.value = SearchFilter()
    }

    companion object {
        /** Больше отдавать в список бессмысленно; сколько нашлось всего — показываем отдельно */
        const val RESULT_LIMIT = 100

        // Не подписи категорий, а синонимы для поиска: «заправка» должна находить
        // топливо. Поэтому это НЕ то же самое, что displayName() в Labels.kt
        fun categoryRuName(category: ExpenseCategory): String = when (category) {
            ExpenseCategory.FUEL        -> "топливо заправка бензин дизель"
            ExpenseCategory.CHARGING    -> "зарядка электричество квт зарядная станция"
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
