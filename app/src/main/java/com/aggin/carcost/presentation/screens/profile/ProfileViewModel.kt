package com.aggin.carcost.presentation.screens.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class UserStatistics(
    val carsCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val totalOdometer: Int = 0
)

data class ProfileUiState(
    val user: User? = null,
    val statistics: UserStatistics = UserStatistics(),
    val isLoading: Boolean = true,
    val isUploadingPhoto: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val supabaseAuth = SupabaseAuthRepository()
    private val userDao = database.userDao()
    private val carDao = database.carDao()
    private val expenseDao = database.expenseDao()
    private val settingsManager = SettingsManager(application)
    private val context = application.applicationContext

    var tempCameraUri: Uri? = null
        private set

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            // Получаем текущего пользователя из Supabase
            val userId = supabaseAuth.getUserId()

            if (userId != null) {
                // Загружаем пользователя из локальной БД
                // getUserById() возвращает Flow, поэтому используем .first()
                userDao.getUserById(userId).collect { user ->
                    if (user != null) {
                        val cars = carDao.getAllActiveCars().first()
                        val allExpenses = mutableListOf<Expense>()
                        cars.forEach { car ->
                            val carExpenses = expenseDao.getExpensesByCar(car.id).first()
                            allExpenses.addAll(carExpenses)
                        }

                        _uiState.value = ProfileUiState(
                            user = user,
                            statistics = UserStatistics(
                                carsCount = cars.size,
                                totalExpenses = allExpenses.sumOf { it.amount },
                                totalOdometer = cars.sumOf { it.currentOdometer }
                            ),
                            isLoading = false
                        )
                    } else {
                        _uiState.value = ProfileUiState(isLoading = false)
                    }
                }
            } else {
                _uiState.value = ProfileUiState(isLoading = false)
            }
        }
    }

    fun createTempImageUri(context: Context): Uri? {
        return try {
            val tempFile = File.createTempFile(
                "profile_${UUID.randomUUID()}",
                ".jpg",
                context.cacheDir
            ).apply {
                createNewFile()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            ).also {
                tempCameraUri = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                errorMessage = "Ошибка создания файла: ${e.message}"
            )
            null
        }
    }

    // ✅ Функция для сжатия изображения
    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Не удалось открыть файл")

        // Читаем изображение
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // Определяем размер для сжатия (макс 1024px по большей стороне)
        val maxSize = 1024
        val ratio = minOf(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height
        )

        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()

        // Масштабируем
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        // Сжимаем в JPEG с качеством 85%
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

        // Освобождаем память
        bitmap.recycle()
        scaledBitmap.recycle()

        outputStream.toByteArray()
    }

    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = true, errorMessage = null)
                }

                val currentUser = _uiState.value.user
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isUploadingPhoto = false,
                            errorMessage = "Пользователь не найден"
                        )
                    }
                    return@launch
                }

                val userId = supabaseAuth.getUserId() ?: throw Exception("User ID not found")
                val fileName = "$userId/avatar_${System.currentTimeMillis()}.jpg"

                // ✅ Сжимаем изображение перед загрузкой
                val bytes = compressImage(uri)

                android.util.Log.d("ProfileViewModel", "Размер сжатого файла: ${bytes.size / 1024} KB")

                // Загружаем в Supabase Storage
                val bucket = supabase.storage.from("avatars")
                bucket.upload(
                    path = fileName,
                    data = bytes,
                    upsert = true
                )

                // Получаем публичный URL
                val photoUrl = bucket.publicUrl(fileName)

                // Обновляем в Supabase таблице users
                supabaseAuth.updateProfile(displayName = null, photoUrl = photoUrl)

                // Обновляем в локальной БД
                val updatedUser = currentUser.copy(photoUrl = photoUrl)
                database.userDao().updateUser(updatedUser)

                // Немедленно обновляем UI
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        user = updatedUser,
                        isUploadingPhoto = false,
                        errorMessage = null
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("ProfileViewModel", "Ошибка загрузки фото", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        errorMessage = "Ошибка загрузки фото: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = true, errorMessage = null)
                }

                val currentUser = _uiState.value.user
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isUploadingPhoto = false,
                            errorMessage = "Пользователь не найден"
                        )
                    }
                    return@launch
                }

                // Удаляем старое фото из Supabase Storage (если есть)
                currentUser.photoUrl?.let { url ->
                    try {
                        // Извлекаем путь к файлу из URL
                        val userId = supabaseAuth.getUserId()
                        if (userId != null && url.contains(userId)) {
                            val fileName = url.substringAfter("avatars/")
                            val bucket = supabase.storage.from("avatars")
                            bucket.delete(fileName)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Не удалось удалить файл из Storage", e)
                    }
                }

                // Обновляем в Supabase таблице users
                supabaseAuth.updateProfile(displayName = null, photoUrl = null)

                // Обновляем в локальной БД
                val updatedUser = currentUser.copy(photoUrl = null)
                database.userDao().updateUser(updatedUser)

                // Немедленно обновляем UI
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        user = updatedUser,
                        isUploadingPhoto = false,
                        errorMessage = null
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        errorMessage = "Ошибка удаления фото: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsManager.saveTheme(theme)
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            try {
                val currentUser = _uiState.value.user ?: return@launch

                // Обновляем в Supabase
                supabaseAuth.updateProfile(displayName = newName, photoUrl = null)

                // Обновляем локально
                val updatedUser = currentUser.copy(displayName = newName)
                database.userDao().updateUser(updatedUser)

                _uiState.value = _uiState.value.copy(
                    user = updatedUser,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка обновления профиля: ${e.message}"
                )
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                // Меняем пароль через Supabase
                supabaseAuth.updatePassword(newPassword)

                _uiState.value = _uiState.value.copy(
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка смены пароля: ${e.message}"
                )
            }
        }
    }

    fun signOut(navController: NavController) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Выходим из Supabase
                supabaseAuth.signOut()

                // 2. КРИТИЧНО: Очищаем ВСЕ локальные данные
                database.userDao().deleteAllUsers()
                database.carDao().deleteAllCars()
                database.expenseDao().deleteAllExpenses()

                // Очищаем все таблицы (включая reminders и tags)
                try {
                    database.clearAllTables()
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Error clearing tables", e)
                }

                // 3. Переходим на экран входа
                withContext(Dispatchers.Main) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error signing out", e)
                // Даже при ошибке выходим
                withContext(Dispatchers.Main) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
}