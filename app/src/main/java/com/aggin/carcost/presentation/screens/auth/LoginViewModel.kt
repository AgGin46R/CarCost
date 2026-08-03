package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.auth.VkSignInHelper
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.api.VkAuthApi
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.aggin.carcost.data.sync.SyncRepositoryFactory

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

    private val syncRepo = SyncRepositoryFactory.create(application, database, supabaseAuth)

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

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

        if (!emailRegex.matches(state.email.trim())) {
            _uiState.value = state.copy(errorMessage = "Введите корректный email")
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
                                // После синхронизации: пометить все чаты как прочитанные до текущего момента,
                                // чтобы сообщения до входа не показывались как непрочитанными
                                val settingsManager = com.aggin.carcost.data.local.settings.SettingsManager(getApplication())
                                val now = System.currentTimeMillis()
                                database.carDao().getAllActiveCarsSync().forEach { car ->
                                    val lastSeen = settingsManager.lastChatSeenFlow(car.id).firstOrNull() ?: 0L
                                    if (lastSeen == 0L) {
                                        settingsManager.setLastChatSeen(car.id, now)
                                    }
                                }
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


    fun signInWithVk(context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        // VK ID SDK показывает свой экран — стартуем с Main-потока
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val authResult = VkSignInHelper.authorize(context)

                if (authResult.isFailure) {
                    val msg = authResult.exceptionOrNull()?.message
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (msg == VkSignInHelper.CANCELLED_MESSAGE) null
                            else (msg ?: "Ошибка входа через VK")
                        )
                    }
                    return@launch
                }

                val vkAuth = authResult.getOrThrow()

                // Обмениваем VK-токен на сессию Supabase через Edge Function
                val exchange = VkAuthApi.exchangeToken(vkAuth.accessToken, vkAuth.deviceId)
                if (exchange.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exchange.exceptionOrNull()?.message ?: "Ошибка входа через VK"
                        )
                    }
                    return@launch
                }

                val result = supabaseAuth.signInWithVk(exchange.getOrThrow().hashedToken)

                result.fold(
                    onSuccess = { userInfo ->
                        Log.d("LoginViewModel", "Supabase VK sign-in success: ${userInfo.id}")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // Имя и фото берём из ТАБЛИЦЫ профиля, а не из метаданных VK.
                            // В метаданных всегда лежит вкшный аватар, и он затирал
                            // бы фото, выбранное пользователем в CarCost. В таблице
                            // же лежит актуальное: триггер подставляет туда данные из
                            // ВК, только пока человек не выбрал своё.
                            val profile = runCatching { fetchUserProfileFromSupabase(userInfo.id) }.getOrNull()
                            saveUserLocally(
                                userId = userInfo.id,
                                email = userInfo.email ?: "",
                                displayName = profile?.displayName
                                    ?: userInfo.userMetadata?.get("full_name")?.toString()?.trim('"'),
                                photoUrl = profile?.photoUrl
                                    ?: userInfo.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                            )
                        }
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        backgroundScope.launch {
                            try { syncRepo.fullSync() } catch (_: Exception) { }
                        }
                    },
                    onFailure = { e ->
                        Log.e("LoginViewModel", "Supabase VK sign-in failed", e)
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка входа через VK")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "signInWithVk exception", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Неизвестная ошибка") }
            }
        }
    }

    private suspend fun saveUserLocally(userId: String, email: String, displayName: String?, photoUrl: String?) {
        val user = com.aggin.carcost.data.local.database.entities.User(
            uid = userId,
            email = email,
            displayName = displayName ?: "Пользователь",
            photoUrl = photoUrl,
            lastLoginAt = System.currentTimeMillis()
        )
        database.userDao().insertUser(user)
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