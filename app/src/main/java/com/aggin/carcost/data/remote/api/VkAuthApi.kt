package com.aggin.carcost.data.remote.api

import android.util.Log
import com.aggin.carcost.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "VkAuthApi"

@Serializable
private data class VkAuthRequest(
    @SerialName("vk_access_token") val vkAccessToken: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class VkAuthResponse(
    @SerialName("hashed_token") val hashedToken: String,
    @SerialName("is_new_user") val isNewUser: Boolean = false
)

/**
 * Клиент Edge Function `vk-auth`.
 *
 * Функция проверяет VK-токен на своей стороне и возвращает hashed_token
 * магической ссылки — его затем скармливаем supabase.auth.verifyEmailOtp(),
 * и GoTrue сам создаёт сессию.
 *
 * SQL и инструкция по деплою — в `supabase/vk_identities_setup.sql`.
 */
object VkAuthApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val endpoint get() = "${BuildConfig.SUPABASE_URL}/functions/v1/vk-auth"

    suspend fun exchangeToken(vkAccessToken: String, deviceId: String): Result<VkAuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val payload = json.encodeToString(
                    VkAuthRequest.serializer(),
                    VkAuthRequest(vkAccessToken, deviceId)
                )

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "vk-auth responded ${response.code}: $body")
                        val message = when (response.code) {
                            401 -> "ВКонтакте отклонил вход. Попробуйте ещё раз"
                            429 -> "Слишком много попыток входа. Подождите минуту"
                            404 -> "Вход через VK ещё не настроен на сервере"
                            else -> "Сервер вернул ошибку ${response.code}"
                        }
                        return@withContext Result.failure(Exception(message))
                    }

                    Result.success(json.decodeFromString(VkAuthResponse.serializer(), body))
                }
            } catch (e: Exception) {
                Log.e(TAG, "vk-auth call failed", e)
                Result.failure(Exception("Нет связи с сервером: ${e.message}"))
            }
        }
}
