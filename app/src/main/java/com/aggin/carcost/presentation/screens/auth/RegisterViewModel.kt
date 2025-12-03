package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
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
                val result = supabaseAuth.signUp(
                    email = state.email,
                    password = state.password
                )

                result.fold(
                    onSuccess = {
                        // Обновляем профиль пользователя
                        supabaseAuth.updateProfile(
                            displayName = state.displayName,
                            photoUrl = null
                        )

                        // Синхронизация данных после регистрации
                        syncRepo.fullSync()

                        _uiState.value = state.copy(
                            isLoading = false,
                            isSuccess = true
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка регистрации"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
}