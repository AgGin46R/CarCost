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

@Serializable
private data class VkErrorResponse(val error: String? = null)

/** Результат привязки VK к уже существующему аккаунту */
sealed class VkLinkResult {
    data class Success(val vkUserId: Long, val displayName: String) : VkLinkResult()

    /** Этот VK уже привязан к другому аккаунту */
    object VkTakenByOtherAccount : VkLinkResult()

    /** К текущему аккаунту уже привязан другой VK */
    object AccountAlreadyLinked : VkLinkResult()

    data class Failure(val message: String) : VkLinkResult()
}

@Serializable
private data class VkLinkResponse(
    val linked: Boolean = false,
    @SerialName("already_linked") val alreadyLinked: Boolean = false,
    @SerialName("vk_user_id") val vkUserId: Long = 0,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = ""
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

    private val authEndpoint get() = "${BuildConfig.SUPABASE_URL}/functions/v1/vk-auth"
    private val linkEndpoint get() = "${BuildConfig.SUPABASE_URL}/functions/v1/vk-link"

    suspend fun exchangeToken(vkAccessToken: String, deviceId: String): Result<VkAuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val payload = json.encodeToString(
                    VkAuthRequest.serializer(),
                    VkAuthRequest(vkAccessToken, deviceId)
                )

                val request = Request.Builder()
                    .url(authEndpoint)
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

    /**
     * Привязывает VK к текущему аккаунту.
     *
     * В отличие от [exchangeToken] уходит с JWT текущей сессии, а не с anon-ключом:
     * функция vk-link по нему определяет, к КОМУ привязывать.
     */
    suspend fun linkAccount(
        vkAccessToken: String,
        deviceId: String,
        accessToken: String
    ): VkLinkResult = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                VkAuthRequest.serializer(),
                VkAuthRequest(vkAccessToken, deviceId)
            )

            val request = Request.Builder()
                .url(linkEndpoint)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val code = runCatching {
                        json.decodeFromString(VkErrorResponse.serializer(), body).error
                    }.getOrNull()

                    Log.w(TAG, "vk-link responded ${response.code}: $code")

                    return@withContext when (code) {
                        "vk_already_linked" -> VkLinkResult.VkTakenByOtherAccount
                        "account_already_linked" -> VkLinkResult.AccountAlreadyLinked
                        "vk_auth_failed" -> VkLinkResult.Failure("ВКонтакте отклонил вход. Попробуйте ещё раз")
                        "unauthorized" -> VkLinkResult.Failure("Сессия истекла — войдите заново")
                        else -> VkLinkResult.Failure("Сервер вернул ошибку ${response.code}")
                    }
                }

                val parsed = json.decodeFromString(VkLinkResponse.serializer(), body)
                VkLinkResult.Success(
                    vkUserId = parsed.vkUserId,
                    displayName = "${parsed.firstName} ${parsed.lastName}".trim()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "vk-link call failed", e)
            VkLinkResult.Failure("Нет связи с сервером: ${e.message}")
        }
    }
}
