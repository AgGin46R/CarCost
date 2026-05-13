package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.auth.GoogleSignInHelper
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseFluidLevelRepository
import com.aggin.carcost.data.remote.repository.SupabaseGpsTripRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarIncidentRepository
import com.aggin.carcost.data.remote.repository.SupabaseInsurancePolicyRepository
import com.aggin.carcost.data.remote.repository.SupabaseSavingsGoalRepository
import com.aggin.carcost.data.remote.repository.SupabaseCategoryBudgetRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarDocumentRepository
import com.aggin.carcost.data.remote.repository.SupabaseAchievementRepository
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val supabasePlannedExpenseRepo = SupabasePlannedExpenseRepository(supabaseAuth)

    private val localCarRepo = CarRepository(database.carDao())
    private val localExpenseRepo = ExpenseRepository(database.expenseDao())
    private val localReminderRepo = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val localTagRepo = ExpenseTagRepository(database.expenseTagDao())
    private val localPlannedExpenseRepo = PlannedExpenseRepository(database.plannedExpenseDao())

    private val syncRepo = SyncRepository(
        localCarRepo = localCarRepo,
        localExpenseRepo = localExpenseRepo,
        localReminderRepo = localReminderRepo,
        localTagRepo = localTagRepo,
        localTagDao = database.expenseTagDao(),
        localPlannedExpenseRepo = localPlannedExpenseRepo,
        supabaseAuthRepo = supabaseAuth,
        supabaseCarRepo = supabaseCarRepo,
        supabaseExpenseRepo = supabaseExpenseRepo,
        supabaseReminderRepo = supabaseReminderRepo,
        supabaseTagRepo = supabaseTagRepo,
        supabasePlannedExpenseRepo = supabasePlannedExpenseRepo,
        localDb = database,
        supabaseFluidLevelRepo = SupabaseFluidLevelRepository(supabaseAuth),
        supabaseGpsTripRepo = SupabaseGpsTripRepository(supabaseAuth),
        supabaseIncidentRepo = SupabaseCarIncidentRepository(supabaseAuth),
        supabaseInsuranceRepo = SupabaseInsurancePolicyRepository(supabaseAuth),
        supabaseSavingsGoalRepo = SupabaseSavingsGoalRepository(supabaseAuth),
        supabaseCategoryBudgetRepo = SupabaseCategoryBudgetRepository(supabaseAuth),
        supabaseCarDocumentRepo = SupabaseCarDocumentRepository(supabaseAuth),
        supabaseAchievementRepo = SupabaseAchievementRepository(supabaseAuth)
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name, errorMessage = null)
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun updateConfirmPassword(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, errorMessage = null)
    }

    fun register() {
        val state = _uiState.value

        // Валидация
        if (state.displayName.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите имя")
            return
        }

        if (state.email.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите email")
            return
        }

        if (!emailRegex.matches(state.email.trim())) {
            _uiState.value = state.copy(errorMessage = "Введите корректный email адрес")
            return
        }

        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите пароль")
            return
        }

        if (state.password.length < 6) {
            _uiState.value = state.copy(errorMessage = "Пароль должен быть не менее 6 символов")
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Пароли не совпадают")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // 1. Регистрируем пользователя
                val result = supabaseAuth.signUp(state.email, state.password)

                result.fold(
                    onSuccess = { userInfo ->
                        Log.d("RegisterViewModel", "✅ Registration successful: ${userInfo.id}")

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

                            // 5. Показываем успех (не ждем синхронизацию!)
                            _uiState.value = state.copy(
                                isLoading = false,
                                isSuccess = true
                            )
                            Log.d("RegisterViewModel", "✅ Registration successful - navigating to home")

                            // 6. Безопасная синхронизация в фоне
                            viewModelScope.launch {
                                try {
                                    Log.d("RegisterViewModel", "Starting background sync...")

                                    if (supabaseAuth.isUserLoggedIn()) {
                                        syncRepo.safeInitialSync()
                                        Log.d("RegisterViewModel", "✅ Background sync completed")
                                    } else {
                                        Log.w("RegisterViewModel", "⚠️ User not logged in - skipping sync")
                                    }
                                } catch (e: Exception) {
                                    Log.e("RegisterViewModel", "❌ Background sync failed (non-critical)", e)
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

    fun signInWithGoogle(context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val tokenResult = GoogleSignInHelper.getIdToken(context)

                if (tokenResult.isFailure) {
                    val msg = tokenResult.exceptionOrNull()?.message
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (msg == "Отменено пользователем") null else (msg ?: "Ошибка Google Sign-In")
                        )
                    }
                    return@launch
                }

                val token = tokenResult.getOrThrow()

                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    supabaseAuth.signInWithGoogle(token)
                }

                result.fold(
                    onSuccess = { userInfo ->
                        val displayName = userInfo.userMetadata?.get("full_name")?.toString()?.trim('"')
                        val photoUrl = userInfo.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                        val user = User(
                            uid = userInfo.id,
                            email = userInfo.email ?: "",
                            displayName = displayName ?: "Пользователь",
                            photoUrl = photoUrl,
                            lastLoginAt = System.currentTimeMillis()
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            database.userDao().insertUser(user)
                        }
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        backgroundScope.launch {
                            try { syncRepo.safeInitialSync() } catch (_: Exception) { }
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка регистрации через Google")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Неизвестная ошибка") }
            }
        }
    }
}