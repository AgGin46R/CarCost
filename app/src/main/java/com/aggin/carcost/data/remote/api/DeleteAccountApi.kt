package com.aggin.carcost.data.remote.api

import android.util.Log
import com.aggin.carcost.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

private const val TAG = "DeleteAccountApi"

@Serializable
private data class DeleteAccountResponse(
    val deleted: Boolean = false,
    val cars: Int = 0,
    val files: Int = 0
)

@Serializable
private data class DeleteAccountError(val error: String = "", val message: String = "")

/** Итог удаления — числа нужны, чтобы честно сказать, что именно снесли */
data class DeleteAccountResult(val carsDeleted: Int, val filesDeleted: Int)

/**
 * Клиент Edge Function `delete-account`.
 *
 * Тело запроса пустое намеренно: кого удалять, функция берёт из JWT и только из
 * него. Передавать id пользователя было бы дырой — любой авторизованный смог бы
 * снести чужой аккаунт.
 *
 * Исходник функции — `supabase/functions/delete-account/index.ts`.
 */
object DeleteAccountApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Удаление проходит по всем таблицам и шести бакетам хранилища:
            // у аккаунта с длинной историей это заметно дольше обычного запроса
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val endpoint get() = "${BuildConfig.SUPABASE_URL}/functions/v1/delete-account"

    suspend fun deleteAccount(accessToken: String): Result<DeleteAccountResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val code = runCatching {
                            json.decodeFromString(DeleteAccountError.serializer(), body).error
                        }.getOrNull()

                        Log.w(TAG, "delete-account responded ${response.code}: $body")

                        val message = when {
                            response.code == 401 || code == "unauthorized" ->
                                "Сессия истекла. Войдите заново и повторите удаление"
                            response.code == 404 ->
                                "Удаление аккаунта ещё не настроено на сервере"
                            else -> "Не удалось удалить аккаунт (ошибка ${response.code})"
                        }
                        return@withContext Result.failure(Exception(message))
                    }

                    val parsed = json.decodeFromString(DeleteAccountResponse.serializer(), body)
                    if (!parsed.deleted) {
                        return@withContext Result.failure(Exception("Сервер не подтвердил удаление"))
                    }
                    Result.success(DeleteAccountResult(parsed.cars, parsed.files))
                }
            } catch (e: Exception) {
                Log.e(TAG, "delete-account call failed", e)
                Result.failure(Exception("Нет связи с сервером. Попробуйте позже"))
            }
        }
}
