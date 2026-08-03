package com.aggin.carcost

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.settings.SettingsManager
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AppStartup"

/**
 * Готовность приложения к первому кадру.
 *
 * Смысл в том, ЧЕГО именно ждать. Ждать имеет смысл только местное и быстрое:
 * прочитанные настройки и восстановленную из хранилища сессию. От них зависит,
 * какой экран показать первым, и без них выбор сделать нельзя — раньше по этой
 * причине AppNavigation просто ничего не рисовал, и человек видел пустоту.
 *
 * Ждать здесь сеть НЕЛЬЗЯ. Синхронизация — это порядка десяти запросов на каждый
 * автомобиль; держать заставку до её конца значит наказывать за плохую связь
 * и намертво вешать запуск в офлайне. Данные и так лежат локально и показываются
 * сразу, а свежие подъезжают следом.
 */
object AppStartup {

    /**
     * Страховка от бесконечной заставки. DataStore и восстановление сессии
     * укладываются в десятки миллисекунд; если за две секунды не уложились —
     * что-то не так, и лучше показать приложение с тем, что есть, чем держать
     * человека перед логотипом.
     */
    private const val MAX_WAIT_MS = 2_000L

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Флаг онбординга, прочитанный на старте — чтобы не читать его повторно */
    @Volatile
    var onboardingDone: Boolean = false
        private set

    suspend fun prepare(context: Context) {
        if (_isReady.value) return

        val elapsed = withTimeoutOrNull(MAX_WAIT_MS) {
            onboardingDone = SettingsManager(context).onboardingDoneFlow.first()

            // Сессия читается с диска и расшифровывается; пока статус
            // LoadingFromStorage, отличить «не входил» от «ещё не загрузилось»
            // невозможно, а ошибиться здесь — значит выкинуть вошедшего на вход
            supabase.auth.sessionStatus.first { it !is SessionStatus.LoadingFromStorage }
            true
        }

        if (elapsed == null) {
            Log.w(TAG, "Готовность не наступила за ${MAX_WAIT_MS} мс — показываем приложение как есть")
        }
        _isReady.value = true
    }
}
