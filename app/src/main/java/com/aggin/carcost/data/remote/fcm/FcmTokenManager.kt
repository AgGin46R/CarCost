package com.aggin.carcost.data.remote.fcm

import android.util.Log
import com.aggin.carcost.supabase
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "FcmTokenManager"

@Serializable
private data class PushTokenDto(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
)

/**
 * Получает текущий FCM токен и сохраняет его в Supabase `user_push_tokens`.
 * Вызывается при логине и при обновлении токена системой.
 */
object FcmTokenManager {

    suspend fun registerCurrentToken() = withContext(Dispatchers.IO) {
        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                Log.d(TAG, "User not authenticated, skipping token registration")
                return@withContext
            }

            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Registering FCM token for user $userId")

            supabase.from("user_push_tokens").upsert(
                PushTokenDto(userId = userId, token = token),
                onConflict = "user_id,token"
            )

            Log.d(TAG, "FCM token registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    suspend fun deleteCurrentToken() = withContext(Dispatchers.IO) {
        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            val token = FirebaseMessaging.getInstance().token.await()

            supabase.from("user_push_tokens").delete {
                filter {
                    eq("user_id", userId)
                    eq("token", token)
                }
            }
            Log.d(TAG, "FCM token deleted on logout")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete FCM token: ${e.message}")
        }
    }
}
