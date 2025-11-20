package com.aggin.carcost.presentation.screens.add_car

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.OdometerUnit
import com.aggin.carcost.data.local.repository.CarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository

data class AddCarUiState(
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val currentOdometer: String = "",
    val fuelType: FuelType = FuelType.GASOLINE,
    val purchasePrice: String = "",
    val vin: String = "",
    val color: String = "",

    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

class AddCarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())

    private val _uiState = MutableStateFlow(AddCarUiState())
    val uiState: StateFlow<AddCarUiState> = _uiState.asStateFlow()

    fun updateBrand(value: String) {
        _uiState.value = _uiState.value.copy(brand = value, showError = false)
    }

    fun updateModel(value: String) {
        _uiState.value = _uiState.value.copy(model = value, showError = false)
    }

    fun updateYear(value: String) {
        // Разрешаем только цифры
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(year = value, showError = false)
        }
    }

    fun updateLicensePlate(value: String) {
        _uiState.value = _uiState.value.copy(licensePlate = value.uppercase(), showError = false)
    }

    fun updateOdometer(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(currentOdometer = value, showError = false)
        }
    }

    fun updateFuelType(value: FuelType) {
        _uiState.value = _uiState.value.copy(fuelType = value)
    }

    fun updatePurchasePrice(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(purchasePrice = value, showError = false)
        }
    }

    fun updateVin(value: String) {
        _uiState.value = _uiState.value.copy(vin = value.uppercase(), showError = false)
    }

    fun updateColor(value: String) {
        _uiState.value = _uiState.value.copy(color = value, showError = false)
    }

    fun saveCar(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Валидация
        if (state.brand.isBlank()) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите марку")
            return
        }
        if (state.model.isBlank()) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите модель")
            return
        }
        if (state.year.isBlank() || state.year.toIntOrNull() == null) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите корректный год")
            return
        }
        if (state.licensePlate.isBlank()) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите номер")
            return
        }
        if (state.currentOdometer.isBlank() || state.currentOdometer.toIntOrNull() == null) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите пробег")
            return
        }

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val car = Car(
                    brand = state.brand.trim(),
                    model = state.model.trim(),
                    year = state.year.toInt(),
                    licensePlate = state.licensePlate.trim(),
                    currentOdometer = state.currentOdometer.toInt(),
                    fuelType = state.fuelType,
                    purchaseDate = System.currentTimeMillis(),
                    purchasePrice = state.purchasePrice.toDoubleOrNull(),
                    purchaseOdometer = state.currentOdometer.toIntOrNull(),
                    vin = state.vin.ifBlank { null },
                    color = state.color.ifBlank { null },
                    odometerUnit = OdometerUnit.KM
                )

                val carId = carRepository.insertCar(car)

                onSuccess()
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isSaving = false,
                    showError = true,
                    errorMessage = "Ошибка сохранения: ${e.message}"
                )
            }
        }
    }
}