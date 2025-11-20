package com.aggin.carcost.presentation.screens.edit_car

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.repository.CarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditCarUiState(
    val car: Car? = null,
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val currentOdometer: String = "",
    val fuelType: FuelType = FuelType.GASOLINE,
    val vin: String = "",
    val color: String = "",

    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = "",
    val showDeleteDialog: Boolean = false
)

class EditCarViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: Long = savedStateHandle.get<String>("carId")?.toLongOrNull() ?: 0L

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())

    private val _uiState = MutableStateFlow(EditCarUiState())
    val uiState: StateFlow<EditCarUiState> = _uiState.asStateFlow()

    init {
        loadCar()
    }

    private fun loadCar() {
        viewModelScope.launch {
            val car = carRepository.getCarById(carId)
            car?.let {
                _uiState.value = _uiState.value.copy(
                    car = it,
                    brand = it.brand,
                    model = it.model,
                    year = it.year.toString(),
                    licensePlate = it.licensePlate,
                    currentOdometer = it.currentOdometer.toString(),
                    fuelType = it.fuelType,
                    vin = it.vin ?: "",
                    color = it.color ?: "",
                    isLoading = false
                )
            }
        }
    }

    fun updateBrand(value: String) {
        _uiState.value = _uiState.value.copy(brand = value, showError = false)
    }

    fun updateModel(value: String) {
        _uiState.value = _uiState.value.copy(model = value, showError = false)
    }

    fun updateYear(value: String) {
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

    fun updateVin(value: String) {
        _uiState.value = _uiState.value.copy(vin = value.uppercase(), showError = false)
    }

    fun updateColor(value: String) {
        _uiState.value = _uiState.value.copy(color = value, showError = false)
    }

    fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun updateCar(onSuccess: () -> Unit) {
        val state = _uiState.value
        val car = state.car ?: return

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
                val updatedCar = car.copy(
                    brand = state.brand.trim(),
                    model = state.model.trim(),
                    year = state.year.toInt(),
                    licensePlate = state.licensePlate.trim(),
                    currentOdometer = state.currentOdometer.toInt(),
                    fuelType = state.fuelType,
                    vin = state.vin.ifBlank { null },
                    color = state.color.ifBlank { null }
                )

                carRepository.updateCar(updatedCar)
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

    fun deleteCar(onSuccess: () -> Unit) {
        val car = _uiState.value.car ?: return

        viewModelScope.launch {
            try {
                carRepository.deleteCar(car)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = "Ошибка удаления: ${e.message}",
                    showDeleteDialog = false
                )
            }
        }
    }
}