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
import com.aggin.carcost.domain.gamification.DriverScore
import com.aggin.carcost.domain.gamification.DriverScoreCalculator
import com.aggin.carcost.data.remote.fcm.FcmTokenManager
import com.aggin.carcost.data.remote.api.VkAuthApi
import com.aggin.carcost.data.remote.api.VkLinkResult
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.VkIdentity
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import com.aggin.carcost.data.sync.SyncRepositoryFactory

data class UserStatistics(
    val carsCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val totalOdometer: Int = 0
)

data class ProfileUiState(
    val user: User? = null,
    val statistics: UserStatistics = UserStatistics(),
    val driverScore: DriverScore? = null,
    val isLoading: Boolean = true,
    val isUploadingPhoto: Boolean = false,
    val errorMessage: String? = null,
    /** Аккаунт заведён через ВКонтакте: пароля нет, email может быть синтетическим */
    val isVkAccount: Boolean = false,
    /** Привязка ВКонтакте, если есть */
    val vkLink: VkIdentity? = null,
    val isLinkingVk: Boolean = false,
    /** Разовое сообщение о результате привязки/отвязки */
    val vkLinkMessage: String? = null,
    /** Идёт отправка данных перед выходом */
    val isSigningOut: Boolean = false,
    /** Выход остановлен: данные не удалось отправить на сервер */
    val logoutBlocked: Boolean = false,
    /** У аккаунта есть вход по паролю (нет у Google и ВКонтакте) */
    val hasPasswordLogin: Boolean = true,
    val passwordChangeState: PasswordChangeState = PasswordChangeState.Idle
)

sealed interface PasswordChangeState {
    object Idle : PasswordChangeState
    object InProgress : PasswordChangeState
    object Success : PasswordChangeState
    data class Error(val message: String) : PasswordChangeState
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val supabaseAuth = SupabaseAuthRepository()
    private val userDao = database.userDao()
    private val carDao = database.carDao()
    private val expenseDao = database.expenseDao()
    private val reminderDao = database.maintenanceReminderDao()
    private val budgetDao = database.categoryBudgetDao()
    private val settingsManager = SettingsManager(application)
    private val context = application.applicationContext

    private val syncRepo = SyncRepositoryFactory.create(application, database, supabaseAuth)

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

                        // Compute driver score from first car's data (or aggregate)
                        val driverScore = try {
                            val cal = java.util.Calendar.getInstance()
                            val month = cal.get(java.util.Calendar.MONTH) + 1
                            val year = cal.get(java.util.Calendar.YEAR)
                            val firstCar = cars.firstOrNull()
                            val reminders = if (firstCar != null)
                                reminderDao.getAllRemindersByCarId(firstCar.id).first()
                            else emptyList()
                            val budgets = if (firstCar != null)
                                budgetDao.getBudgetsByCarIdAndPeriod(firstCar.id, month, year).first()
                            else emptyList()
                            val odometer = cars.maxOfOrNull { it.currentOdometer } ?: 0
                            DriverScoreCalculator.calculate(allExpenses, reminders, budgets, odometer)
                        } catch (e: Exception) { null }

                        _uiState.value = ProfileUiState(
                            user = user,
                            statistics = UserStatistics(
                                carsCount = cars.size,
                                totalExpenses = allExpenses.sumOf { it.amount },
                                totalOdometer = cars.sumOf { it.currentOdometer }
                            ),
                            driverScore = driverScore,
                            isLoading = false,
                            isVkAccount = supabaseAuth.isVkAccount(),
                            vkLink = supabaseAuth.getVkLink(),
                            hasPasswordLogin = supabaseAuth.hasPasswordLogin()
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
        if (bitmap == null) throw IllegalStateException("Не удалось декодировать изображение. Попробуйте выбрать другой формат (JPEG/PNG)")

        // Определяем размер для сжатия (макс 1024px по большей стороне, не апскейлируем)
        val maxSize = 1024
        val ratio = minOf(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height,
            1.0f  // не увеличивать маленькие изображения
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

                // Берём userId из уже загруженного currentUser — auth.currentUserOrNull()
                // может вернуть null пока сессия грузится из storage
                val userId = currentUser.uid
                val fileName = "$userId/avatar.jpg"   // фиксированное имя → старый файл автоматически перезаписывается

                // ✅ Сжимаем изображение перед загрузкой
                val bytes = compressImage(uri)

                android.util.Log.d("ProfileViewModel", "Размер сжатого файла: ${bytes.size / 1024} KB")

                // Загружаем в Supabase Storage (upsert перезапишет предыдущий файл)
                val bucket = supabase.storage.from("avatars")
                bucket.upload(
                    path = fileName,
                    data = bytes,
                    upsert = true
                )

                // Получаем публичный URL с cache-buster чтобы Coil не показывал старое фото
                val photoUrl = bucket.publicUrl(fileName) + "?v=${System.currentTimeMillis()}"

                // Обновляем в Supabase таблице users (getOrThrow — чтобы ошибка всплыла в catch)
                supabaseAuth.updateProfile(photoUrl = photoUrl).getOrThrow()

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

                // Удаляем файл из Supabase Storage
                val userId = supabaseAuth.getUserId()
                if (userId != null) {
                    try {
                        supabase.storage.from("avatars").delete("$userId/avatar.jpg")
                    } catch (e: Exception) {
                        android.util.Log.w("ProfileViewModel", "Не удалось удалить файл из Storage: ${e.message}")
                    }
                }

                // Явно обнуляем photo_url в таблице users
                supabaseAuth.updateProfile(clearPhoto = true).getOrThrow()

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

    fun setAccent(accent: String) {
        viewModelScope.launch {
            settingsManager.saveAccent(accent)
        }
    }

    fun setNotifMaintenance(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotifMaintenance(enabled) }
    }

    fun setNotifInsurance(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotifInsurance(enabled) }
    }

    fun setNotifDigest(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotifDigest(enabled) }
    }

    fun setNotifFuel(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotifFuel(enabled) }
    }

    fun setNotifBudgetAlert(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotifBudgetAlert(enabled) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setQuietHoursEnabled(enabled) }
    }

    fun setQuietHoursStart(hour: Int) {
        viewModelScope.launch { settingsManager.setQuietHoursStart(hour) }
    }

    fun setQuietHoursEnd(hour: Int) {
        viewModelScope.launch { settingsManager.setQuietHoursEnd(hour) }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            try {
                val currentUser = _uiState.value.user ?: return@launch

                // Обновляем в Supabase
                supabaseAuth.updateProfile(displayName = newName)

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

    /**
     * Смена пароля с проверкой текущего.
     *
     * Раньше `oldPassword` принимался и не использовался вообще: любой, кому
     * попал в руки разблокированный телефон, менял пароль и забирал аккаунт.
     * Плюс результат `updatePassword` не проверялся, и при неудаче пользователь
     * видел «успешно».
     *
     * Изолированной ре-аутентификации в supabase-kt нет, поэтому старый пароль
     * проверяется повторным `signIn` на том же клиенте: при верном пароле это
     * просто ротирует сессию, при неверном — возвращает failure, не трогая её.
     */
    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(passwordChangeState = PasswordChangeState.InProgress) }

            val email = supabaseAuth.getCurrentUserEmail()
            if (email.isNullOrBlank()) {
                _uiState.update {
                    it.copy(passwordChangeState = PasswordChangeState.Error("Не удалось определить email аккаунта"))
                }
                return@launch
            }

            val reauth = supabaseAuth.signIn(email, oldPassword)
            if (reauth.isFailure) {
                android.util.Log.w("ProfileViewModel", "Re-auth before password change failed")
                _uiState.update {
                    it.copy(passwordChangeState = PasswordChangeState.Error("Текущий пароль неверен"))
                }
                return@launch
            }

            val result = supabaseAuth.updatePassword(newPassword)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(passwordChangeState = PasswordChangeState.Success)
                } else {
                    it.copy(
                        passwordChangeState = PasswordChangeState.Error(
                            "Не удалось сменить пароль: ${result.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
                        )
                    )
                }
            }
        }
    }

    fun resetPasswordChangeState() {
        _uiState.update { it.copy(passwordChangeState = PasswordChangeState.Idle) }
    }

    /**
     * Привязывает ВКонтакте к текущему аккаунту, чтобы дальше можно было
     * входить любым способом и попадать в одни и те же данные.
     */
    fun linkVk(context: Context) {
        _uiState.update { it.copy(isLinkingVk = true, vkLinkMessage = null) }
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val authResult = com.aggin.carcost.data.auth.VkSignInHelper.authorize(context)
                if (authResult.isFailure) {
                    val msg = authResult.exceptionOrNull()?.message
                    _uiState.update {
                        it.copy(
                            isLinkingVk = false,
                            vkLinkMessage = if (msg == com.aggin.carcost.data.auth.VkSignInHelper.CANCELLED_MESSAGE) null else msg
                        )
                    }
                    return@launch
                }

                val vkAuth = authResult.getOrThrow()
                val jwt = com.aggin.carcost.supabase.auth.currentAccessTokenOrNull()
                if (jwt == null) {
                    _uiState.update {
                        it.copy(isLinkingVk = false, vkLinkMessage = "Сессия истекла — войдите заново")
                    }
                    return@launch
                }

                when (val result = VkAuthApi.linkAccount(vkAuth.accessToken, vkAuth.deviceId, jwt)) {
                    is VkLinkResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLinkingVk = false,
                                vkLink = supabaseAuth.getVkLink(),
                                vkLinkMessage = "ВКонтакте привязан"
                            )
                        }
                    }
                    is VkLinkResult.VkTakenByOtherAccount -> _uiState.update {
                        it.copy(
                            isLinkingVk = false,
                            vkLinkMessage = "Этот аккаунт ВКонтакте уже привязан к другому профилю CarCost"
                        )
                    }
                    is VkLinkResult.AccountAlreadyLinked -> _uiState.update {
                        it.copy(
                            isLinkingVk = false,
                            vkLinkMessage = "К этому профилю уже привязан другой аккаунт ВКонтакте"
                        )
                    }
                    is VkLinkResult.Failure -> _uiState.update {
                        it.copy(isLinkingVk = false, vkLinkMessage = result.message)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "linkVk failed", e)
                _uiState.update {
                    it.copy(isLinkingVk = false, vkLinkMessage = e.message ?: "Не удалось привязать ВКонтакте")
                }
            }
        }
    }

    /**
     * Отвязывает ВКонтакте. Для аккаунтов, созданных через VK, вызывать нельзя —
     * у них не останется способа войти. Экран такую кнопку не показывает.
     */
    fun unlinkVk() {
        if (_uiState.value.isVkAccount) return

        viewModelScope.launch {
            val result = supabaseAuth.unlinkVk()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(vkLink = null, vkLinkMessage = "ВКонтакте отвязан")
                } else {
                    it.copy(vkLinkMessage = "Не удалось отвязать: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun clearVkLinkMessage() {
        _uiState.update { it.copy(vkLinkMessage = null) }
    }

    /** Инвалидирует токен VK ID. Ошибки не критичны — из приложения мы всё равно выходим. */
    private suspend fun logoutFromVk() {
        try {
            com.vk.id.VKID.instance.logout(object : com.vk.id.logout.VKIDLogoutCallback {
                override fun onSuccess() {
                    android.util.Log.d("ProfileViewModel", "VK ID logout ok")
                }

                override fun onFail(fail: com.vk.id.logout.VKIDLogoutFail) {
                    android.util.Log.w("ProfileViewModel", "VK ID logout failed: ${fail.description}")
                }
            })
        } catch (e: Exception) {
            android.util.Log.w("ProfileViewModel", "VK ID logout threw", e)
        }
    }

    /**
     * Выход из аккаунта.
     *
     * Выход стирает локальную базу целиком, поэтому сначала обязательно нужно
     * отправить всё на сервер. Раньше результат отправки не проверялся вовсе:
     * `fullSync()` не бросает исключение, а возвращает Result, и при отсутствии
     * сети выход молча уничтожал несинхронизированные записи.
     *
     * Теперь при неудачной отправке ничего не разрушается — пользователь видит
     * предупреждение и решает сам.
     *
     * @param force пропустить проверку синхронизации (осознанный выбор пользователя
     *              в диалоге: «выйти, потеряв несохранённое»)
     */
    fun signOut(navController: NavController, force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!force) {
                    _uiState.update { it.copy(isSigningOut = true, logoutBlocked = false) }

                    val syncResult = syncRepo.fullSync()

                    if (syncResult.isFailure) {
                        android.util.Log.w(
                            "ProfileViewModel",
                            "Pre-logout sync failed — выход остановлен",
                            syncResult.exceptionOrNull()
                        )
                        // Ничего не трогаем: ни сессию, ни базу. Решение за пользователем.
                        _uiState.update { it.copy(isSigningOut = false, logoutBlocked = true) }
                        return@launch
                    }

                    android.util.Log.d("ProfileViewModel", "✅ Pre-logout sync completed")
                }

                // Дальше — разрушительная часть, она же точка невозврата
                _uiState.update { it.copy(isSigningOut = true, logoutBlocked = false) }

                // Удаляем FCM токен чтобы не получать чужие уведомления после выхода
                FcmTokenManager.deleteCurrentToken()

                // Сессию VK нужно сбросить отдельно: иначе следующий вход через VK
                // молча вернёт того же пользователя, без выбора аккаунта
                val wasVkAccount = supabaseAuth.isVkAccount()
                supabaseAuth.signOut()
                if (wasVkAccount) logoutFromVk()

                try {
                    database.clearAllTables()
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Error clearing tables", e)
                }

                withContext(Dispatchers.Main) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error signing out", e)
                // Сюда попадаем уже после точки невозврата — сессия и база могли
                // быть частично очищены, оставлять пользователя в аккаунте нельзя
                withContext(Dispatchers.Main) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    fun dismissLogoutBlocked() {
        _uiState.update { it.copy(logoutBlocked = false) }
    }
}