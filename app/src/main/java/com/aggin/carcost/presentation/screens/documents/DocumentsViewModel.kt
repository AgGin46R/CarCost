package com.aggin.carcost.presentation.screens.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.CarDocument
import com.aggin.carcost.data.local.database.entities.DocumentType
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.repository.CarDocumentRepository
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.local.database.entities.VehicleType
import com.aggin.carcost.domain.tax.VehicleTaxCalculator
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.domain.gamification.AchievementChecker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DocumentsUiState(
    val documents: List<CarDocument> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val tax: VehicleTaxInfo? = null
)

/**
 * Транспортный налог за прошедший год.
 *
 * Считается, только когда указана мощность двигателя: без неё любая сумма была
 * бы выдумкой, а налог человек платит настоящий.
 */
data class VehicleTaxInfo(
    val year: Int,
    val powerHp: Int,
    val ratePerHp: Double,
    /** Ставку ввёл владелец, а не взяли базовую из НК */
    val isCustomRate: Boolean,
    val ownedMonths: Int,
    val amount: Double,
    val dueDate: Long
)

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CarDocumentRepository(db.carDocumentDao())
    private val carRepository = CarRepository(db.carDao())
    private val supabaseCarRepo = SupabaseCarRepository(SupabaseAuthRepository())
    private val supabaseAuth = SupabaseAuthRepository()

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    fun loadDocuments(carId: String) {
        viewModelScope.launch {
            repository.getDocuments(carId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { docs ->
                    _uiState.update { it.copy(documents = docs, isLoading = false) }
                }
        }
        viewModelScope.launch {
            db.carDao().getCarByIdFlow(carId).collect { car ->
                _uiState.update { it.copy(tax = car?.let { c -> calculateTax(c) }) }
            }
        }
    }

    /**
     * Ставка, введённая владельцем.
     *
     * Пустое значение возвращает расчёт к базовой ставке из НК — это не то же
     * самое, что ставка ноль, поэтому ноль сюда не записываем.
     */
    fun setTaxRate(carId: String, rate: Double?) {
        viewModelScope.launch {
            val car = carRepository.getCarById(carId) ?: return@launch
            val updated = car.copy(
                taxRatePerHp = rate?.takeIf { it > 0 },
                updatedAt = System.currentTimeMillis()
            )
            carRepository.updateCar(updated)
            launch {
                try {
                    supabaseCarRepo.updateCar(updated)
                } catch (e: Exception) {
                    android.util.Log.w("DocumentsViewModel", "Не удалось отправить ставку налога", e)
                }
            }
        }
    }

    private fun calculateTax(car: Car): VehicleTaxInfo? {
        val power = car.enginePowerHp?.takeIf { it > 0 } ?: return null
        val year = VehicleTaxCalculator.currentTaxYear()
        val months = VehicleTaxCalculator.ownedMonthsIn(car.purchaseDate, year)
        val isMotorcycle = car.vehicleType == VehicleType.MOTORCYCLE
        val rate = car.taxRatePerHp?.takeIf { it > 0 }
        val amount = VehicleTaxCalculator.annualTax(
            powerHp = power,
            ratePerHp = rate,
            ownedMonths = months,
            isMotorcycle = isMotorcycle
        ) ?: return null

        return VehicleTaxInfo(
            year = year,
            powerHp = power,
            ratePerHp = rate ?: VehicleTaxCalculator.baseRate(power, isMotorcycle),
            isCustomRate = rate != null,
            ownedMonths = months,
            amount = amount,
            dueDate = VehicleTaxCalculator.dueDate(year)
        )
    }

    fun addDocument(
        carId: String,
        type: DocumentType,
        title: String,
        fileUri: String?,
        expiryDate: Long?,
        notes: String?
    ) {
        viewModelScope.launch {
            val doc = CarDocument(
                carId = carId,
                type = type,
                title = title,
                fileUri = fileUri,
                expiryDate = expiryDate,
                notes = notes
            )
            repository.addDocument(doc)
            com.aggin.carcost.data.analytics.Analytics.documentAdded()
            // Check FIRST_DOCUMENT achievement
            try {
                val userId = supabaseAuth.getUserId()
                if (userId != null) {
                    AchievementChecker(db.achievementDao(), db.expenseDao())
                        .checkAfterDocumentAdded(userId)
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsViewModel", "Achievement check failed", e)
            }
        }
    }

    fun updateDocument(
        document: CarDocument,
        type: DocumentType,
        title: String,
        fileUri: String?,
        expiryDate: Long?,
        notes: String?
    ) {
        viewModelScope.launch {
            repository.updateDocument(
                document.copy(
                    type = type,
                    title = title,
                    fileUri = fileUri,
                    expiryDate = expiryDate,
                    notes = notes
                )
            )
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
        }
    }
}
