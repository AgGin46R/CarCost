package com.aggin.carcost.data.remote.rustore

import android.util.Log
import com.aggin.carcost.BuildConfig
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.rustore.sdk.pushclient.RuStorePushClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "RuStorePushToken"

@Serializable
private data class PushTokenDto(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    /**
     * Каким транспортом слать. Сервер выбирает по этому полю: fcm — через
     * Firebase, rustore — через серверный API RuStore. Значение по умолчанию в
     * базе — fcm, поэтому все ранее сохранённые токены остались корректными.
     */
    val provider: String = "rustore",
)

/**
 * Регистрация пуш-токена RuStore в таблице `user_push_tokens`.
 *
 * Устроено так же, как [com.aggin.carcost.data.remote.fcm.FcmTokenManager], и
 * вызывается рядом с ним: у одного пользователя может быть одновременно и токен
 * Firebase, и токен RuStore — например, на разных устройствах. Уникальность в
 * базе по паре (user_id, token), поэтому они не конфликтуют.
 */
object RuStorePushTokenManager {

    /** Пуши RuStore не настроены — идентификатор проекта не задан при сборке. */
    private val isConfigured: Boolean
        get() = BuildConfig.RUSTORE_PUSH_PROJECT_ID.isNotBlank()

    suspend fun registerCurrentToken() = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext

        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                Log.d(TAG, "Пользователь не авторизован, токен не регистрируем")
                return@withContext
            }

            val token = awaitToken() ?: return@withContext
            Log.d(TAG, "Регистрирую токен RuStore для $userId")

            supabase.from("user_push_tokens").upsert(
                PushTokenDto(userId = userId, token = token),
                onConflict = "user_id,token"
            )

            Log.d(TAG, "Токен RuStore зарегистрирован")
        } catch (e: Exception) {
            // Молча: на устройстве может не быть приложения RuStore, и это
            // нормальное состояние, а не ошибка. Доставку возьмёт на себя Firebase.
            Log.w(TAG, "Не удалось зарегистрировать токен RuStore: ${e.message}")
        }
    }

    suspend fun deleteCurrentToken() = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext

        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            val token = awaitToken() ?: return@withContext

            supabase.from("user_push_tokens").delete {
                filter {
                    eq("user_id", userId)
                    eq("token", token)
                }
            }
            Log.d(TAG, "Токен RuStore удалён при выходе")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось удалить токен RuStore: ${e.message}")
        }
    }

    /**
     * SDK отдаёт токен через собственный Task с колбэками — переводим в suspend.
     *
     * Возвращает null, если токен получить не удалось: на устройстве нет
     * приложения RuStore, оно устарело или ему запрещена фоновая работа.
     */
    private suspend fun awaitToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            RuStorePushClient.getToken()
                .addOnSuccessListener { token -> cont.resume(token) }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Токен RuStore недоступен: ${error.message}")
                    cont.resume(null)
                }
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}
