package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.sync.SyncRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)
    private val supabaseTagRepo = SupabaseExpenseTagRepository(supabaseAuth)
    private val supabasePlannedExpenseRepo = SupabasePlannedExpenseRepository(supabaseAuth) // ✅ ДОБАВЛЕНО

    private val localCarRepo = CarRepository(database.carDao())
    private val localExpenseRepo = ExpenseRepository(database.expenseDao())
    private val localReminderRepo = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val localTagRepo = ExpenseTagRepository(database.expenseTagDao())
    private val localPlannedExpenseRepo = PlannedExpenseRepository(database.plannedExpenseDao()) // ✅ ДОБАВЛЕНО

    private val syncRepo = SyncRepository(
        localCarRepo = localCarRepo,
        localExpenseRepo = localExpenseRepo,
        localReminderRepo = localReminderRepo,
        localTagRepo = localTagRepo,
        localTagDao = database.expenseTagDao(),
        localPlannedExpenseRepo = localPlannedExpenseRepo, // ✅ ДОБАВЛЕНО
        supabaseAuthRepo = supabaseAuth,
        supabaseCarRepo = supabaseCarRepo,
        supabaseExpenseRepo = supabaseExpenseRepo,
        supabaseReminderRepo = supabaseReminderRepo,
        supabaseTagRepo = supabaseTagRepo,
        supabasePlannedExpenseRepo = supabasePlannedExpenseRepo // ✅ ДОБАВЛЕНО
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            try {
                val result = supabaseAuth.signIn(state.email, state.password)

                result.fold(
                    onSuccess = { userInfo ->
                        Log.d("Login", "✅ Login successful")
                        Log.d("Login", "UserInfo: id=${userInfo.id}, email=${userInfo.email}")
                        Log.d("Login", "UserMetadata: ${userInfo.userMetadata}")

                        // Загружаем профиль из Supabase таблицы users
                        viewModelScope.launch {
                            val userProfile = try {
                                fetchUserProfileFromSupabase(userInfo.id)
                            } catch (e: Exception) {
                                Log.e("Login", "❌ Error fetching user profile", e)
                                null
                            }

                            Log.d("Login", "Fetched profile: displayName=${userProfile?.displayName}, photoUrl=${userProfile?.photoUrl}")

                            // Сохраняем пользователя локально с данными из Supabase
                            val user = com.aggin.carcost.data.local.database.entities.User(
                                uid = userInfo.id,
                                email = userInfo.email ?: state.email,
                                displayName = userProfile?.displayName ?: "Пользователь",
                                photoUrl = userProfile?.photoUrl,  // ✅ ДОБАВЛЕНО
                                lastLoginAt = System.currentTimeMillis()
                            )

                            database.userDao().insertUser(user)
                            Log.d("Login", "✅ User saved locally with displayName: ${user.displayName}, photoUrl: ${user.photoUrl}")
                        }

                        // Сразу переходим на главный экран
                        _uiState.value = state.copy(
                            isLoading = false,
                            isSuccess = true
                        )

                        // Синхронизация в фоне
                        backgroundScope.launch {
                            try {
                                Log.d("Login", "Starting background sync...")
                                syncRepo.fullSync()
                                Log.d("Login", "✅ Background sync completed")
                            } catch (e: Exception) {
                                Log.e("Login", "Background sync failed", e)
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.e("Login", "❌ Login failed: ${error.message}")
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка входа"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("Login", "Exception during login", e)
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }

    // ✅ Получение профиля пользователя из таблицы users в Supabase
    private suspend fun fetchUserProfileFromSupabase(userId: String): UserResponse? {
        return try {
            val supabase = com.aggin.carcost.supabase

            val response = supabase.from("users")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserResponse>()

            response
        } catch (e: Exception) {
            Log.e("Login", "❌ Failed to fetch user profile from Supabase", e)
            null
        }
    }
}

// ✅ Data class с правильным именем поля
@Serializable
data class UserResponse(
    val id: String,
    val email: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null  // ✅ ДОБАВЛЕНО
)