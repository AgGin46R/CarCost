package com.aggin.carcost.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.vk.id.AccessToken
import com.vk.id.VKID
import com.vk.id.VKIDAuthFail
import com.vk.id.auth.AuthCodeData
import com.vk.id.auth.VKIDAuthParams
import com.vk.id.auth.VKIDAuthCallback
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "VkSignInHelper"

/**
 * Результат авторизации во ВКонтакте.
 *
 * Наружу отдаём только то, что нужно Edge Function: сам токен и device_id
 * (VK требует его в запросе user_info). Личные данные из [AccessToken.userData]
 * не используем — серверу нельзя доверять тому, что прислал клиент, профиль он
 * запрашивает у VK сам.
 */
data class VkAuthResult(
    val accessToken: String,
    val deviceId: String
)

/**
 * Обёртка над VK ID SDK по образцу [GoogleSignInHelper].
 *
 * VKID.init() вызывается в App.onCreate(); если он упал (например, приложение
 * ещё не заведено на dev.vk.com), обращение к VKID.instance бросит исключение —
 * ловим и отдаём внятную ошибку.
 */
object VkSignInHelper {

    /** Совпадает с сообщением, которое ViewModel трактуют как «пользователь передумал» */
    const val CANCELLED_MESSAGE = "Отменено пользователем"

    private fun Context.findActivity(): Activity {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        error("Activity не найдена в контексте")
    }

    suspend fun authorize(context: Context): Result<VkAuthResult> {
        val activity = try {
            context.findActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось получить Activity", e)
            return Result.failure(Exception("Внутренняя ошибка: нет Activity"))
        }

        val lifecycleOwner = activity as? LifecycleOwner
            ?: return Result.failure(Exception("Внутренняя ошибка: Activity без lifecycle"))

        val vkid = try {
            VKID.instance
        } catch (e: Exception) {
            Log.e(TAG, "VK ID не инициализирован", e)
            return Result.failure(Exception("Вход через VK недоступен: SDK не инициализирован"))
        }

        return suspendCancellableCoroutine { continuation ->
            // onAuthCode приходит раньше onAuth и несёт device_id, который нужен
            // серверу для проверки токена в VK.
            var deviceId = ""

            val callback = object : VKIDAuthCallback {
                override fun onAuthCode(data: AuthCodeData, isCompletion: Boolean) {
                    deviceId = data.deviceId
                }

                override fun onAuth(accessToken: AccessToken) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.success(VkAuthResult(accessToken.token, deviceId))
                        )
                    }
                }

                override fun onFail(fail: VKIDAuthFail) {
                    if (!continuation.isActive) return
                    val error = when (fail) {
                        is VKIDAuthFail.Canceled -> Exception(CANCELLED_MESSAGE)
                        is VKIDAuthFail.NoBrowserAvailable ->
                            Exception("Не найден браузер для входа через VK")
                        else -> {
                            Log.e(TAG, "VKIDAuthFail: ${fail::class.simpleName} — ${fail.description}")
                            Exception("Ошибка входа через VK: ${fail.description}")
                        }
                    }
                    continuation.resume(Result.failure(error))
                }
            }

            try {
                vkid.authorize(
                    lifecycleOwner = lifecycleOwner,
                    callback = callback,
                    params = VKIDAuthParams {
                        // email нужен, чтобы пользователя можно было пригласить
                        // в чужой автомобиль по адресу почты
                        scopes = setOf("email")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить авторизацию VK", e)
                if (continuation.isActive) continuation.resume(Result.failure(e))
            }
        }
    }
}
