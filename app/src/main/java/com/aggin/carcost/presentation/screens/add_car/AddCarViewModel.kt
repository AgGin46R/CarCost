package com.aggin.carcost.presentation.screens.add_car

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.OdometerUnit
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

data class AddCarUiState(
    val carId: String = java.util.UUID.randomUUID().toString(),
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val currentOdometer: String = "",
    val fuelType: FuelType = FuelType.GASOLINE,
    val purchasePrice: String = "",
    val purchaseDate: Long? = null,
    val vin: String = "",
    val color: String = "",
    val photoUri: String? = null,
    val isUploadingPhoto: Boolean = false,

    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

class AddCarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val context = application.applicationContext

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)

    private val _uiState = MutableStateFlow(AddCarUiState())
    val uiState: StateFlow<AddCarUiState> = _uiState.asStateFlow()

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

    fun updatePurchaseDate(value: Long) {
        _uiState.value = _uiState.value.copy(purchaseDate = value)
    }

    fun updateCarPhoto(uri: Uri) {
        val carId = _uiState.value.carId
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = true, showError = false)
            }
            try {
                val bytes = compressImage(uri)
                val fileName = "car-photos/$carId.jpg"
                val bucket = supabase.storage.from("car-photos")
                bucket.upload(path = fileName, data = bytes, upsert = true)
                val photoUrl = bucket.publicUrl(fileName)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(photoUri = photoUrl, isUploadingPhoto = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("AddCar", "Photo upload failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
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
                // Проверяем авторизацию
                val userId = supabaseAuth.getUserId()
                if (userId == null) {
                    android.util.Log.e("AddCar", "User not authenticated!")
                    _uiState.value = state.copy(
                        isSaving = false,
                        showError = true,
                        errorMessage = "Пользователь не авторизован"
                    )
                    return@launch
                }

                android.util.Log.d("AddCar", "Creating car for user: $userId")

                val car = Car(
                    id = state.carId,
                    brand = state.brand.trim(),
                    model = state.model.trim(),
                    year = state.year.toInt(),
                    licensePlate = state.licensePlate.trim(),
                    currentOdometer = state.currentOdometer.toInt(),
                    fuelType = state.fuelType,
                    purchaseDate = state.purchaseDate ?: System.currentTimeMillis(),
                    purchasePrice = state.purchasePrice.toDoubleOrNull(),
                    purchaseOdometer = state.currentOdometer.toIntOrNull(),
                    vin = state.vin.ifBlank { null },
                    color = state.color.ifBlank { null },
                    photoUri = state.photoUri,
                    odometerUnit = OdometerUnit.KM
                )

                // 1. Сохраняем локально
                val carId = carRepository.insertCar(car)
                android.util.Log.d("AddCar", "Car saved locally with ID: $carId")

                // 2. ✅ ИСПРАВЛЕНИЕ: Синхронизируем с Supabase БЕЗ локального ID
                // НЕ передаём локальный ID - пусть Supabase сгенерирует свой!
                // SupabaseCarRepository.insertCar автоматически добавит .copy(id = null)

                val result = supabaseCarRepo.insertCar(car)  // ✅ Передаём car БЕЗ локального ID

                result.fold(
                    onSuccess = { syncedCar ->
                        android.util.Log.d("AddCar", "✅ SUCCESS! Car synced to Supabase with server ID: ${syncedCar.id}")
                        android.util.Log.d("AddCar", "Local ID: $carId, Server ID: ${syncedCar.id}")
                        android.util.Log.d("AddCar", "Brand: ${syncedCar.brand}, Model: ${syncedCar.model}")
                    },
                    onFailure = { error ->
                        android.util.Log.e("AddCar", "❌ FAILED to sync to Supabase!", error)
                        android.util.Log.e("AddCar", "Error message: ${error.message}")
                        android.util.Log.e("AddCar", "Error type: ${error::class.simpleName}")
                        error.printStackTrace()

                        // Показываем ошибку пользователю
                        _uiState.value = state.copy(
                            isSaving = false,
                            showError = true,
                            errorMessage = "Машина сохранена локально, но не синхронизирована: ${error.message}"
                        )
                        return@launch
                    }
                )

                // 3. Успех - сбрасываем состояние
                _uiState.value = state.copy(isSaving = false)
                onSuccess()

            } catch (e: Exception) {
                android.util.Log.e("AddCar", "❌ Exception in saveCar!", e)
                e.printStackTrace()
                _uiState.value = state.copy(
                    isSaving = false,
                    showError = true,
                    errorMessage = "Ошибка сохранения: ${e.message}"
                )
            }
        }
    }
}