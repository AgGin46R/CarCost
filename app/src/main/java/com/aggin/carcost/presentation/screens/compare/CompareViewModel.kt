package com.aggin.carcost.presentation.screens.compare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CarCompareStats(
    val car: Car,
    val totalExpenses: Double,
    val expenseCount: Int,
    val topCategories: List<Pair<ExpenseCategory, Double>>,
    val costPerKm: Double,
    val avgFuelConsumption: Double?,
    val totalLiters: Double
)

data class CompareUiState(
    val availableCars: List<Car> = emptyList(),
    val selectedCarIds: Set<String> = emptySet(),
    val carStats: Map<String, CarCompareStats> = emptyMap(),
    val isLoading: Boolean = true
)

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            carDao.getAllActiveCars().collect { cars ->
                _uiState.update { it.copy(availableCars = cars, isLoading = false) }
            }
        }
    }

    fun toggleCarSelection(carId: String) {
        val current = _uiState.value.selectedCarIds
        val updated = if (carId in current) current - carId else current + carId
        _uiState.update { it.copy(selectedCarIds = updated) }

        if (updated.size >= 2) {
            loadStatsForSelected(updated)
        }
    }

    private fun loadStatsForSelected(carIds: Set<String>) {
        viewModelScope.launch {
            val allStats = mutableMapOf<String, CarCompareStats>()
            for (carId in carIds) {
                val car = carDao.getCarById(carId) ?: continue
                expenseDao.getExpensesByCarId(carId)
                    .first()
                    .let { expenses ->
                        allStats[carId] = buildStats(car, expenses)
                    }
            }
            _uiState.update { it.copy(carStats = allStats) }
        }
    }

    private fun buildStats(car: Car, expenses: List<Expense>): CarCompareStats {
        val totalExpenses = expenses.sumOf { it.amount }
        val kmDriven = car.currentOdometer - (car.purchaseOdometer ?: car.currentOdometer)
        val costPerKm = if (kmDriven > 0) totalExpenses / kmDriven else 0.0

        val topCategories = expenses
            .groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(4)

        val fuelExpenses = expenses.filter {
            it.category == ExpenseCategory.FUEL && (it.fuelLiters ?: 0.0) > 0
        }.sortedBy { it.odometer }

        val totalLiters = fuelExpenses.sumOf { it.fuelLiters ?: 0.0 }
        val fuelKm = if (kmDriven > 0) kmDriven else {
            val minOdom = fuelExpenses.minOfOrNull { it.odometer } ?: 0
            val maxOdom = fuelExpenses.maxOfOrNull { it.odometer } ?: 0
            maxOdom - minOdom
        }
        val avgConsumption = if (fuelKm > 0 && totalLiters > 0) (totalLiters / fuelKm) * 100 else null

        return CarCompareStats(
            car = car,
            totalExpenses = totalExpenses,
            expenseCount = expenses.size,
            topCategories = topCategories,
            costPerKm = costPerKm,
            avgFuelConsumption = avgConsumption,
            totalLiters = totalLiters
        )
    }
}
