package com.aggin.carcost.presentation.screens.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.local.repository.AuthRepository
import com.aggin.carcost.data.local.settings.SettingsManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private val authRepository = AuthRepository(userDao = database.userDao())
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
            authRepository.currentUser.collect { user ->
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

                // Создаем директорию для фото профиля
                val profilePhotosDir = File(context.filesDir, "profile_photos")
                if (!profilePhotosDir.exists()) {
                    profilePhotosDir.mkdirs()
                }

                // Создаем файл для сохранения фото
                val photoFile = File(profilePhotosDir, "${currentUser.uid}_${UUID.randomUUID()}.jpg")

                // Копируем содержимое URI в наш файл
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(photoFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Получаем путь к сохраненному файлу
                val photoPath = photoFile.absolutePath

                // Обновляем в локальной БД
                val updatedUser = currentUser.copy(photoUrl = photoPath)
                database.userDao().updateUser(updatedUser)

                // Обновляем также в Firebase Auth (опционально)
                try {
                    val firebaseUser = FirebaseAuth.getInstance().currentUser
                    firebaseUser?.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(updatedUser.displayName)
                            .build()
                    )?.await()
                } catch (e: Exception) {
                    // Игнорируем ошибки Firebase, работаем только локально
                    e.printStackTrace()
                }

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

                // Удаляем старое фото из файловой системы
                currentUser.photoUrl?.let { photoPath ->
                    val photoFile = File(photoPath)
                    if (photoFile.exists()) {
                        photoFile.delete()
                    }
                }

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
                val updatedUser = currentUser.copy(displayName = newName)

                database.userDao().updateUser(updatedUser)

                // Обновляем также в Firebase
                FirebaseAuth.getInstance().currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()
                )?.await()

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
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser == null || firebaseUser.email == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Пользователь не найден"
                    )
                    return@launch
                }

                // Реаутентификация
                val credential = EmailAuthProvider.getCredential(
                    firebaseUser.email!!,
                    oldPassword
                )
                firebaseUser.reauthenticate(credential).await()

                // Смена пароля
                firebaseUser.updatePassword(newPassword).await()

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
            authRepository.signOut()

            withContext(Dispatchers.Main) {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
}