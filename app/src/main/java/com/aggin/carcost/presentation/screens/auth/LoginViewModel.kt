package com.aggin.carcost.presentation.screens.auth

import android.app.Application
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    // Инициализация Supabase репозиториев
    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)
    private val supabaseTagRepo = SupabaseExpenseTagRepository(supabaseAuth)

    // Инициализация локальных репозиториев
    private val localCarRepo = CarRepository(database.carDao())
    private val localExpenseRepo = ExpenseRepository(database.expenseDao())
    private val localReminderRepo = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val localTagRepo = ExpenseTagRepository(database.expenseTagDao())

    // SyncRepository с полным набором репозиториев
    private val syncRepo = SyncRepository(
        localCarRepo = localCarRepo,
        localExpenseRepo = localExpenseRepo,
        localReminderRepo = localReminderRepo,
        localTagRepo = localTagRepo,
        supabaseAuthRepo = supabaseAuth,
        supabaseCarRepo = supabaseCarRepo,
        supabaseExpenseRepo = supabaseExpenseRepo,
        supabaseReminderRepo = supabaseReminderRepo,
        supabaseTagRepo = supabaseTagRepo
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun signIn() {
        val state = _uiState.value

        // Валидация
        if (state.email.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите email")
            return
        }

        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите пароль")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            // Retry логика при timeout
            var attempt = 0
            val maxAttempts = 3
            var lastError: Throwable? = null

            while (attempt < maxAttempts) {
                try {
                    attempt++
                    android.util.Log.d("Login", "Attempt $attempt of $maxAttempts")

                    // 1. Входим через Supabase
                    val result = supabaseAuth.signIn(state.email, state.password)

                    result.fold(
                        onSuccess = {
                            // 2. Получаем userId
                            val userId = supabaseAuth.getUserId()

                            if (userId != null) {
                                // 3. Создаем и сохраняем пользователя в локальной БД
                                val user = User(
                                    uid = userId,
                                    email = state.email,
                                    displayName = state.email.substringBefore('@'),
                                    photoUrl = null
                                )
                                database.userDao().insertUser(user)

                                // 4. НЕМЕДЛЕННО показываем успех
                                _uiState.value = state.copy(
                                    isLoading = false,
                                    isSuccess = true
                                )

                                // 5. Безопасная синхронизация В ФОНЕ (не блокирует UI)
                                viewModelScope.launch {
                                    try {
                                        syncRepo.safeInitialSync()
                                        android.util.Log.d("Login", "Safe sync completed")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Login", "Sync failed", e)
                                    }
                                }

                                return@launch // Успех - выходим
                            } else {
                                _uiState.value = state.copy(
                                    isLoading = false,
                                    errorMessage = "Не удалось получить данные пользователя"
                                )
                                return@launch
                            }
                        },
                        onFailure = { error ->
                            lastError = error

                            // Если НЕ timeout - не retry
                            if (error.message?.contains("timed out", ignoreCase = true) != true) {
                                _uiState.value = state.copy(
                                    isLoading = false,
                                    errorMessage = error.message ?: "Ошибка входа"
                                )
                                return@launch
                            }

                            // Если timeout - пробуем еще раз
                            android.util.Log.w("Login", "Timeout on attempt $attempt, retrying...")
                            if (attempt < maxAttempts) {
                                delay(2000) // Ждем 2 секунды перед retry
                            }
                        }
                    )
                } catch (e: Exception) {
                    lastError = e
                    android.util.Log.e("Login", "Error on attempt $attempt", e)

                    if (attempt < maxAttempts) {
                        delay(2000)
                    }
                }
            }

            // Если все попытки исчерпаны
            _uiState.value = state.copy(
                isLoading = false,
                errorMessage = "Не удалось подключиться к серверу. Проверьте интернет."
            )
        }
    }
}