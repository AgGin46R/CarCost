package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.ChatMessage
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_email") val userEmail: String,
    val message: String,
    @SerialName("created_at") val createdAt: Long
)

fun ChatMessageDto.toChatMessage() = ChatMessage(
    id = id,
    carId = carId,
    userId = userId,
    userEmail = userEmail,
    message = message,
    createdAt = createdAt
)

fun ChatMessage.toDto() = ChatMessageDto(
    id = id,
    carId = carId,
    userId = userId,
    userEmail = userEmail,
    message = message,
    createdAt = createdAt
)

class SupabaseChatRepository {

    /** Send a message — insert to Supabase. */
    suspend fun sendMessage(message: ChatMessage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("chat_messages").insert(message.toDto())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Load all messages for a car (initial load / refresh). */
    suspend fun getMessages(carId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        try {
            val dtos = supabase.from("chat_messages")
                .select {
                    filter { eq("car_id", carId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<ChatMessageDto>()
            Result.success(dtos.map { it.toChatMessage() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete own message from Supabase. */
    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("chat_messages").delete {
                filter { eq("id", messageId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
