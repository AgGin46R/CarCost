package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)
    private val supabaseTagRepo = SupabaseExpenseTagRepository(supabaseAuth)

    private val localCarRepo = CarRepository(database.carDao())
    private val localExpenseRepo = ExpenseRepository(database.expenseDao())
    private val localReminderRepo = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val localTagRepo = ExpenseTagRepository(database.expenseTagDao())

    private val syncRepo = SyncRepository(
        localCarRepo = localCarRepo,
        localExpenseRepo = localExpenseRepo,
        localReminderRepo = localReminderRepo,
        localTagRepo = localTagRepo,
        localTagDao = AppDatabase.getDatabase(getApplication()).expenseTagDao(), // ✅ ДОБАВЛЕНО
        supabaseAuthRepo = supabaseAuth,
        supabaseCarRepo = supabaseCarRepo,
        supabaseExpenseRepo = supabaseExpenseRepo,
        supabaseReminderRepo = supabaseReminderRepo,
        supabaseTagRepo = supabaseTagRepo
    )

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value, errorMessage = null)
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun signUp() {
        val state = _uiState.value

        // Валидация
        when {
            state.displayName.isBlank() -> {
                _uiState.value = state.copy(errorMessage = "Введите имя")
                return
            }
            state.email.isBlank() -> {
                _uiState.value = state.copy(errorMessage = "Введите email")
                return
            }
            state.password.isBlank() -> {
                _uiState.value = state.copy(errorMessage = "Введите пароль")
                return
            }
            state.password.length < 6 -> {
                _uiState.value = state.copy(errorMessage = "Пароль должен быть не менее 6 символов")
                return
            }
            state.password != state.confirmPassword -> {
                _uiState.value = state.copy(errorMessage = "Пароли не совпадают")
                return
            }
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // 1. Регистрируем через Supabase
                val result = supabaseAuth.signUp(
                    email = state.email,
                    password = state.password
                )

                result.fold(
                    onSuccess = {
                        // 2. Обновляем профиль пользователя
                        supabaseAuth.updateProfile(
                            displayName = state.displayName,
                            photoUrl = null
                        )

                        // 3. Получаем userId
                        val userId = supabaseAuth.getUserId()

                        if (userId != null) {
                            // 4. Создаем и сохраняем пользователя в локальной БД
                            val user = User(
                                uid = userId,
                                email = state.email,
                                displayName = state.displayName,
                                photoUrl = null
                            )
                            database.userDao().insertUser(user)
                            Log.d("RegisterViewModel", "✅ User saved locally")

                            // ✅ 5. ИСПРАВЛЕНО: СРАЗУ показываем успех (НЕ ждем синхронизацию!)
                            _uiState.value = state.copy(
                                isLoading = false,
                                isSuccess = true
                            )
                            Log.d("RegisterViewModel", "✅ Registration successful - navigating to home")

                            // ✅ 6. Безопасная синхронизация В ФОНЕ (НЕ блокирует вход)
                            viewModelScope.launch {
                                try {
                                    Log.d("RegisterViewModel", "Starting background sync...")

                                    // Проверяем интернет перед синхронизацией
                                    if (supabaseAuth.isUserLoggedIn()) {
                                        syncRepo.safeInitialSync()
                                        Log.d("RegisterViewModel", "✅ Background sync completed")
                                    } else {
                                        Log.w("RegisterViewModel", "⚠️ User not logged in - skipping sync")
                                    }
                                } catch (e: Exception) {
                                    // ⚠️ Синхронизация не критична - просто логируем
                                    Log.e("RegisterViewModel", "❌ Background sync failed (non-critical)", e)
                                    // НЕ показываем ошибку пользователю - регистрация прошла успешно!
                                }
                            }
                        } else {
                            Log.e("RegisterViewModel", "❌ UserId is NULL after registration")
                            _uiState.value = state.copy(
                                isLoading = false,
                                errorMessage = "Не удалось получить данные пользователя"
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e("RegisterViewModel", "❌ Registration failed: ${error.message}")
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка регистрации"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "❌ Exception during registration", e)
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
}