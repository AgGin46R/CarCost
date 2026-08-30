package com.aggin.carcost.presentation.screens.auth

import android.app.Application
import com.aggin.carcost.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Шаг восстановления пароля */
enum class RecoveryStep { EMAIL, CODE, NEW_PASSWORD, DONE }

data class ForgotPasswordUiState(
    val step: RecoveryStep = RecoveryStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val passwordRepeat: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Восстановление пароля по коду из письма.
 *
 * Три шага: почта → код → новый пароль. После успеха пользователь возвращается
 * на экран входа с уже подставленной почтой.
 *
 * Почему не входим сразу, хотя после проверки кода сессия уже есть: весь запуск
 * после входа (профиль из таблицы users, локальная запись пользователя,
 * первичная синхронизация, отметка чатов прочитанными) живёт в LoginViewModel.
 * Продублировать его здесь — значит завести вторую копию, которая со временем
 * разойдётся с первой. В этом проекте так уже было с расчётом расхода топлива:
 * два экрана показывали разные числа для одной машины. Один лишний ввод пароля
 * дешевле.
 */
/**
 * Наследуется от AndroidViewModel ради контекста: сообщения об ошибках лежат
 * в ресурсах, а достать их без контекста нельзя.
 */
class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /**
         * Длину кода задаёт сервер (Authentication → Email OTP Length), и она
         * не обязана быть шестизначной.
         *
         * Здесь стояло жёсткое «6», и ввод молча обрезал лишние цифры: при
         * восьмизначном коде человек вводил его целиком, а до сервера доезжали
         * первые шесть — и код «не подходил» без всякого объяснения. Поэтому
         * рамки широкие, а решает по-прежнему сервер.
         */
        const val MIN_CODE_LENGTH = 4
        const val MAX_CODE_LENGTH = 12
    }

    private val auth = SupabaseAuthRepository()

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun updateEmail(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }

    fun updateCode(value: String) =
        _uiState.update {
            it.copy(code = value.filter { c -> c.isDigit() }.take(MAX_CODE_LENGTH), errorMessage = null)
        }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun updatePasswordRepeat(value: String) =
        _uiState.update { it.copy(passwordRepeat = value, errorMessage = null) }

    fun back() {
        _uiState.update {
            when (it.step) {
                RecoveryStep.CODE -> it.copy(step = RecoveryStep.EMAIL, code = "", errorMessage = null)
                RecoveryStep.NEW_PASSWORD -> it.copy(step = RecoveryStep.CODE, errorMessage = null)
                else -> it
            }
        }
    }

    /** Шаг 1 → 2 */
    fun sendCode() {
        val email = _uiState.value.email.trim()
        if (!email.contains("@") || !email.contains(".")) {
            _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_korrektnyy_email)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            auth.sendPasswordResetCode(email)
                .onSuccess {
                    // Переходим к вводу кода независимо от того, есть ли такой
                    // аккаунт: сказать «пользователь не найден» — значит позволить
                    // перебором выяснить, кто здесь зарегистрирован
                    _uiState.update { it.copy(isLoading = false, step = RecoveryStep.CODE) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyError(e, getApplication<Application>().getString(R.string.auth_ne_udalos_otpravit_pismo))
                        )
                    }
                }
        }
    }

    /** Шаг 2 → 3. Проверка кода даёт сессию, без неё пароль сменить нельзя */
    fun verifyCode() {
        val state = _uiState.value
        if (state.code.length < MIN_CODE_LENGTH) {
            _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.auth_vvedite_kod_iz_pisma_polnostyu)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            auth.verifyPasswordResetCode(state.email, state.code)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, step = RecoveryStep.NEW_PASSWORD) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyError(e, getApplication<Application>().getString(R.string.auth_kod_ne_podoshel_proverte_ego_ili))
                        )
                    }
                }
        }
    }

    /** Шаг 3 → готово */
    fun savePassword() {
        val state = _uiState.value
        when {
            state.password.length < 6 -> {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.profile_parol_dolzhen_byt_ne_menee_6_simvolov)) }
                return
            }
            state.password != state.passwordRepeat -> {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.profile_paroli_ne_sovpadayut)) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            auth.updatePassword(state.password)
                .onSuccess {
                    // Сессия, полученная по коду, дальше не нужна: вход пойдёт
                    // обычным путём, уже с новым паролем
                    auth.signOut()
                    _uiState.update { it.copy(isLoading = false, step = RecoveryStep.DONE) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyError(e, getApplication<Application>().getString(R.string.auth_ne_udalos_sohranit_parol))
                        )
                    }
                }
        }
    }

    /**
     * Уход с экрана на полпути не должен оставлять человека внутри приложения.
     *
     * Проверка кода уже создаёт сессию — то есть после верного кода пользователь
     * фактически вошёл, ещё не задав пароль. Если на этом месте закрыть экран,
     * он остался бы залогинен со старым паролем, которого не помнит: следующий
     * запуск открыл бы приложение, а причина была бы совершенно неочевидна.
     */
    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.step == RecoveryStep.NEW_PASSWORD) {
            // Область видимости ViewModel уже закрыта, поэтому отдельная
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { auth.signOut() }
            }
        }
    }

    /**
     * Сообщения сервера приходят по-английски и техническим языком.
     * Разбираем только те, что человек может исправить сам.
     */
    private fun friendlyError(e: Throwable, fallback: String): String {
        val raw = e.message?.lowercase().orEmpty()
        return when {
            "expired" in raw || "invalid" in raw ->
                getApplication<Application>().getString(R.string.auth_kod_ne_podoshel_ili_ustarel_zaprosite)
            "rate limit" in raw || "too many" in raw || "429" in raw ->
                getApplication<Application>().getString(R.string.auth_slishkom_mnogo_popytok_podozhdite_minutu)
            "weak" in raw || "password" in raw && "short" in raw ->
                getApplication<Application>().getString(R.string.auth_parol_slishkom_prostoy)
            "network" in raw || "host" in raw || "timeout" in raw ->
                getApplication<Application>().getString(R.string.auth_net_svyazi_s_serverom)
            else -> fallback
        }
    }
}
