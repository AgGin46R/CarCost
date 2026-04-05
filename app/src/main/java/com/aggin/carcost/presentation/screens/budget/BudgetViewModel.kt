package com.aggin.carcost.presentation.screens.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.CategoryBudget
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class BudgetItem(
    val category: ExpenseCategory,
    val limit: Double?,       // null = без лимита
    val spent: Double,
    val budgetId: String?
) {
    val progress: Float get() = if (limit != null && limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val isOverBudget: Boolean get() = limit != null && spent > limit
    val remaining: Double get() = if (limit != null) limit - spent else 0.0
}

data class BudgetUiState(
    val items: List<BudgetItem> = emptyList(),
    val month: Int = 0,
    val year: Int = 0,
    val totalLimit: Double = 0.0,
    val totalSpent: Double = 0.0,
    val isLoading: Boolean = true
)

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val budgetDao = db.categoryBudgetDao()
    private val expenseDao = db.expenseDao()

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private var currentCarId: String = ""
    private var currentMonth: Int = 0
    private var currentYear: Int = 0

    fun load(carId: String) {
        currentCarId = carId
        val cal = Calendar.getInstance()
        currentMonth = cal.get(Calendar.MONTH) + 1  // 1-based
        currentYear = cal.get(Calendar.YEAR)

        viewModelScope.launch {
            budgetDao.getBudgetsByCarIdAndPeriod(carId, currentMonth, currentYear)
                .collect { budgets ->
                    refreshItems(carId, budgets, currentMonth, currentYear)
                }
        }
    }

    private suspend fun refreshItems(
        carId: String,
        budgets: List<CategoryBudget>,
        month: Int,
        year: Int
    ) {
        val (startMs, endMs) = monthRange(month, year)
        val budgetMap = budgets.associateBy { it.category }

        val items = ExpenseCategory.entries.map { category ->
            val spent = expenseDao.getTotalByCategoryAndPeriod(carId, category, startMs, endMs) ?: 0.0
            val budget = budgetMap[category]
            BudgetItem(
                category = category,
                limit = budget?.monthlyLimit,
                spent = spent,
                budgetId = budget?.id
            )
        }
            .sortedWith(compareByDescending<BudgetItem> { it.limit != null }.thenByDescending { it.spent })

        val totalLimit = items.sumOf { it.limit ?: 0.0 }
        val totalSpent = items.sumOf { it.spent }

        _uiState.update {
            it.copy(
                items = items,
                month = month,
                year = year,
                totalLimit = totalLimit,
                totalSpent = totalSpent,
                isLoading = false
            )
        }
    }

    fun setLimit(category: ExpenseCategory, limitRub: Double) {
        viewModelScope.launch {
            val existing = budgetDao.getBudget(currentCarId, category, currentMonth, currentYear)
            if (existing != null) {
                budgetDao.updateBudget(existing.copy(monthlyLimit = limitRub, updatedAt = System.currentTimeMillis()))
            } else {
                budgetDao.insertBudget(
                    CategoryBudget(
                        carId = currentCarId,
                        category = category,
                        monthlyLimit = limitRub,
                        month = currentMonth,
                        year = currentYear
                    )
                )
            }
        }
    }

    fun removeLimit(category: ExpenseCategory) {
        viewModelScope.launch {
            budgetDao.deleteBudgetByCategory(currentCarId, category, currentMonth, currentYear)
        }
    }

    private fun monthRange(month: Int, year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month - 1, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }
}
