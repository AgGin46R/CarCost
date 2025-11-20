package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.repository.AuthRepository
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

    private val authRepository = AuthRepository(
        userDao = AppDatabase.getDatabase(application).userDao()
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
            val result = authRepository.signUp(
                email = state.email,
                password = state.password,
                displayName = state.displayName
            )

            _uiState.value = if (result.isSuccess) {
                state.copy(isLoading = false, isSuccess = true)
            } else {
                state.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Ошибка регистрации"
                )
            }
        }
    }
}