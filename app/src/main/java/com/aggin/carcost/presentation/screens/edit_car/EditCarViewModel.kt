package com.aggin.carcost.presentation.screens.edit_car

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
    val currency: String = "RUB",
    val photoUri: String? = null,

    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = "",
    val showDeleteDialog: Boolean = false
)

class EditCarViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: "" // ✅ String

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val context = application.applicationContext

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)

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
                    photoUri = it.photoUri,
                    currency = it.currency,
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

    fun updateCurrency(value: String) {
        _uiState.value = _uiState.value.copy(currency = value)
    }

    fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun updateCarPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = true, showError = false)
            }
            try {
                val bytes = compressImage(uri)
                val fileName = "car-photos/$carId.jpg"
                val bucket = supabase.storage.from("car-photos")
                bucket.upload(path = fileName, data = bytes, upsert = true)
                // Добавляем версию для сброса кэша Coil при смене фото
                val photoUrl = bucket.publicUrl(fileName) + "?v=${System.currentTimeMillis()}"

                val car = _uiState.value.car ?: return@launch
                val updatedCar = car.copy(photoUri = photoUrl)
                carRepository.updateCar(updatedCar)
                viewModelScope.launch {
                    try { supabaseCarRepo.updateCar(updatedCar) } catch (e: Exception) { Log.e("EditCar", "Photo sync failed", e) }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        car = updatedCar,
                        photoUri = photoUrl,
                        isUploadingPhoto = false
                    )
                }
            } catch (e: Exception) {
                Log.e("EditCar", "Photo upload failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        showError = true,
                        errorMessage = "Ошибка загрузки фото: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть изображение")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) throw IllegalStateException("Не удалось декодировать изображение. Попробуйте выбрать другой формат (JPEG/PNG)")

        val maxSize = 1024
        val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height, 1f)
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        bitmap.recycle()
        scaledBitmap.recycle()
        outputStream.toByteArray()
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
                    color = state.color.ifBlank { null },
                    currency = state.currency,
                    photoUri = state.photoUri
                )

                // 1. Обновляем локально
                carRepository.updateCar(updatedCar)

                // 2. Синхронизируем с Supabase (в фоне)
                viewModelScope.launch {
                    try {
                        supabaseCarRepo.updateCar(updatedCar)
                        Log.d("EditCar", "Synced to Supabase: ${updatedCar.id}")
                    } catch (e: Exception) {
                        Log.e("EditCar", "Sync failed", e)
                    }
                }

                // 3. Сбрасываем состояние и вызываем onSuccess
                _uiState.value = state.copy(isSaving = false)
                onSuccess()

            } catch (e: Exception) {
                Log.e("EditCar", "Error updating car", e)
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

        Log.d("EditCarVM", "🚗 ===== DELETING CAR =====")
        Log.d("EditCarVM", "Car ID: ${car.id}")
        Log.d("EditCarVM", "Brand: ${car.brand}")
        Log.d("EditCarVM", "Model: ${car.model}")
        Log.d("EditCarVM", "User ID: ${supabaseAuth.getUserId()}")
        Log.d("EditCarVM", "============================")

        viewModelScope.launch {
            try {
                // 1. Удаляем локально
                carRepository.deleteCar(car)
                Log.d("EditCarVM", "✅ Car deleted locally")

                // 2. Удаляем из Supabase (в фоне)
                viewModelScope.launch {
                    try {
                        supabaseCarRepo.deleteCar(car.id) // ✅ String
                        Log.d("EditCarVM", "✅ Car deleted from Supabase: ${car.id}")
                    } catch (e: Exception) {
                        Log.e("EditCarVM", "❌ Failed to delete car from Supabase", e)
                    }
                }

                Log.d("EditCarVM", "✅ Calling onSuccess() - navigating away")
                onSuccess()

            } catch (e: Exception) {
                Log.e("EditCarVM", "❌ Error deleting car", e)
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = "Ошибка удаления: ${e.message}",
                    showDeleteDialog = false
                )
            }
        }
    }
}
