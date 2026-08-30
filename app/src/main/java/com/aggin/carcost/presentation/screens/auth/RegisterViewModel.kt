package com.aggin.carcost.presentation.screens.auth

import com.aggin.carcost.R
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.auth.VkSignInHelper
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.User
import com.aggin.carcost.data.remote.api.VkAuthApi
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aggin.carcost.data.sync.SyncRepositoryFactory
import io.github.jan.supabase.postgrest.from

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

    private val syncRepo = SyncRepositoryFactory.create(application, database, supabaseAuth)

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
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_imya))
            return
        }

        if (state.email.isBlank()) {
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_email))
            return
        }

        if (!emailRegex.matches(state.email.trim())) {
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_korrektnyy_email))
            return
        }

        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_parol))
            return
        }

        if (state.password.length < 6) {
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.profile_parol_dolzhen_byt_ne_menee_6_simvolov))
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = getApplication<Application>().getString(R.string.profile_paroli_ne_sovpadayut))
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

                            com.aggin.carcost.data.analytics.Analytics.registration("email")

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
                                errorMessage = getApplication<Application>().getString(R.string.auth_ne_udalos_poluchit_dannye_polzovatelya)
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e("RegisterViewModel", "❌ Registration failed: ${error.message}")
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: getApplication<Application>().getString(R.string.auth_oshibka_registratsii)
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "❌ Exception during registration", e)
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: getApplication<Application>().getString(R.string.carmembers_neizvestnaya_oshibka)
                )
            }
        }
    }


    fun signInWithVk(context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val authResult = VkSignInHelper.authorize(context)

                if (authResult.isFailure) {
                    val msg = authResult.exceptionOrNull()?.message
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (msg == VkSignInHelper.CANCELLED_MESSAGE) null
                            else (msg ?: getApplication<Application>().getString(R.string.auth_oshibka_vhoda_cherez_vk))
                        )
                    }
                    return@launch
                }

                val vkAuth = authResult.getOrThrow()

                val exchange = VkAuthApi.exchangeToken(vkAuth.accessToken, vkAuth.deviceId)
                if (exchange.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exchange.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.auth_oshibka_vhoda_cherez_vk)
                        )
                    }
                    return@launch
                }

                val result = supabaseAuth.signInWithVk(exchange.getOrThrow().hashedToken)

                result.fold(
                    onSuccess = { userInfo ->
                        // Кнопкой «Зарегистрироваться через VK» пользуется и тот,
                        // кто уже заводил аккаунт, — поток тот же. Поэтому фото
                        // берём из таблицы профиля: там лежит выбранное человеком,
                        // если он его менял. Метаданные VK — только запасной вариант
                        // для действительно первого входа.
                        val profile = runCatching {
                            com.aggin.carcost.supabase
                                .from("users")
                                .select { filter { eq("id", userInfo.id) } }
                                .decodeSingleOrNull<com.aggin.carcost.data.remote.repository.SupabaseUserDto>()
                        }.getOrNull()

                        val displayName = profile?.displayName
                            ?: userInfo.userMetadata?.get("full_name")?.toString()?.trim('"')
                        val photoUrl = profile?.photoUrl
                            ?: userInfo.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                        val user = User(
                            uid = userInfo.id,
                            email = userInfo.email ?: "",
                            displayName = displayName ?: getApplication<Application>().getString(R.string.profile_polzovatel),
                            photoUrl = photoUrl,
                            lastLoginAt = System.currentTimeMillis()
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            database.userDao().insertUser(user)
                        }
                        com.aggin.carcost.data.analytics.Analytics.registration("vk")
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        backgroundScope.launch {
                            try { syncRepo.safeInitialSync() } catch (_: Exception) { }
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = e.message ?: getApplication<Application>().getString(R.string.auth_oshibka_registratsii_cherez_vk))
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: getApplication<Application>().getString(R.string.carmembers_neizvestnaya_oshibka)) }
            }
        }
    }
}