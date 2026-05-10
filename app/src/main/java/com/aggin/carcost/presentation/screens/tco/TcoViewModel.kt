package com.aggin.carcost.presentation.screens.tco

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.domain.DepreciationCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class TcoCategoryBreakdown(
    val category: ExpenseCategory,
    val total: Double,
    val count: Int,
    val share: Float   // 0..1
)

data class TcoUiState(
    val car: Car? = null,
    val purchasePrice: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalCostOfOwnership: Double = 0.0,  // purchasePrice + totalExpenses
    val kmDriven: Int = 0,
    val monthsOwned: Int = 0,
    val costPerKm: Double = 0.0,
    val costPerMonth: Double = 0.0,
    val costPerYear: Double = 0.0,
    val categoryBreakdown: List<TcoCategoryBreakdown> = emptyList(),
    val monthlyAvgByCategory: List<TcoCategoryBreakdown> = emptyList(),
    // Depreciation fields
    val marketValueInput: String = "",                          // user-entered current market value
    val depreciationPoints: List<DepreciationCalculator.DepreciationPoint> = emptyList(),
    val estimatedCurrentValue: Double = 0.0,                   // from depreciation model or user override
    val totalDepreciation: Double = 0.0,                       // purchasePrice - currentValue
    val isLoading: Boolean = true
)

class TcoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()

    private val _uiState = MutableStateFlow(TcoUiState())
    val uiState: StateFlow<TcoUiState> = _uiState.asStateFlow()

    fun load(carId: String) {
        viewModelScope.launch {
            combine(
                carDao.getCarByIdFlow(carId),
                expenseDao.getExpensesByCarId(carId)
            ) { car, expenses ->
                car to expenses
            }.collect { (car, expenses) ->
                if (car == null) return@collect
                calculate(car, expenses)
            }
        }
    }

    private fun calculate(car: Car, expenses: List<Expense>) {
        val purchasePrice = car.purchasePrice ?: 0.0
        val totalExpenses = expenses.sumOf { it.amount }
        val tco = purchasePrice + totalExpenses

        // Пробег с момента покупки
        val kmDriven = if (car.purchaseOdometer != null)
            (car.currentOdometer - car.purchaseOdometer).coerceAtLeast(0)
        else
            car.currentOdometer

        // Месяцы владения
        val purchaseDate = car.purchaseDate
        val nowMs = System.currentTimeMillis()
        val monthsOwned = ((nowMs - purchaseDate) / (1000L * 60 * 60 * 24 * 30.44)).toInt().coerceAtLeast(1)

        val costPerKm = if (kmDriven > 0) tco / kmDriven else 0.0
        val costPerMonth = tco / monthsOwned
        val costPerYear = costPerMonth * 12

        // Разбивка по категориям
        val byCategory = expenses.groupBy { it.category }
        val breakdown = ExpenseCategory.entries
            .map { cat ->
                val catExpenses = byCategory[cat] ?: emptyList()
                TcoCategoryBreakdown(
                    category = cat,
                    total = catExpenses.sumOf { it.amount },
                    count = catExpenses.size,
                    share = if (totalExpenses > 0) (catExpenses.sumOf { it.amount } / totalExpenses).toFloat() else 0f
                )
            }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }

        // Depreciation
        val marketValueOverride = _uiState.value.marketValueInput.toDoubleOrNull()
        val depreciationPoints = if (purchasePrice > 0) {
            DepreciationCalculator.calculate(purchasePrice, car.purchaseDate, yearsForward = 10)
        } else emptyList()
        val estimatedCurrentValue = if (purchasePrice > 0) {
            DepreciationCalculator.currentEstimate(purchasePrice, car.purchaseDate, marketValueOverride)
        } else 0.0
        val totalDepreciation = (purchasePrice - estimatedCurrentValue).coerceAtLeast(0.0)

        _uiState.update {
            it.copy(
                car = car,
                purchasePrice = purchasePrice,
                totalExpenses = totalExpenses,
                totalCostOfOwnership = tco,
                kmDriven = kmDriven,
                monthsOwned = monthsOwned,
                costPerKm = costPerKm,
                costPerMonth = costPerMonth,
                costPerYear = costPerYear,
                categoryBreakdown = breakdown,
                depreciationPoints = depreciationPoints,
                estimatedCurrentValue = estimatedCurrentValue,
                totalDepreciation = totalDepreciation,
                isLoading = false
            )
        }
    }

    /** Called when user types in the market value field. */
    fun updateMarketValue(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { state ->
                val mv = value.toDoubleOrNull()
                val car = state.car ?: return@update state.copy(marketValueInput = value)
                val purchasePrice = car.purchasePrice ?: 0.0
                val estimated = if (purchasePrice > 0)
                    DepreciationCalculator.currentEstimate(purchasePrice, car.purchaseDate, mv)
                else 0.0
                state.copy(
                    marketValueInput = value,
                    estimatedCurrentValue = estimated,
                    totalDepreciation = (purchasePrice - estimated).coerceAtLeast(0.0)
                )
            }
        }
    }
}
