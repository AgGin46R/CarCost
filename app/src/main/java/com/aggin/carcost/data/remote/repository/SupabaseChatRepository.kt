package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.data.local.database.entities.ChatMessage
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
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
    @SerialName("created_at") val createdAt: Long,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to_text") val replyToText: String? = null,
    @SerialName("is_edited") val isEdited: Boolean = false
)

fun ChatMessageDto.toChatMessage() = ChatMessage(
    id = id,
    carId = carId,
    userId = userId,
    userEmail = userEmail,
    message = message,
    createdAt = createdAt,
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    fileName = fileName,
    replyToId = replyToId,
    replyToText = replyToText,
    isEdited = isEdited
)

fun ChatMessage.toDto() = ChatMessageDto(
    id = id,
    carId = carId,
    userId = userId,
    userEmail = userEmail,
    message = message,
    createdAt = createdAt,
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    fileName = fileName,
    replyToId = replyToId,
    replyToText = replyToText,
    isEdited = isEdited
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

    /** Load messages with pagination (newest first, then reversed). */
    suspend fun getMessagesPaged(carId: String, limit: Int, offset: Int): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        try {
            val dtos = supabase.from("chat_messages")
                .select {
                    filter { eq("car_id", carId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<ChatMessageDto>()
            Result.success(dtos.reversed().map { it.toChatMessage() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class MessageUpdate(
        val message: String,
        @SerialName("is_edited") val isEdited: Boolean = true
    )

    /** Edit own message text in Supabase. */
    suspend fun updateMessage(messageId: String, newText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("chat_messages").update(MessageUpdate(newText)) {
                filter { eq("id", messageId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepo", "updateMessage FAILED: ${e::class.simpleName}: ${e.message}", e)
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

    /**
     * Upload any media file to Supabase Storage (chat_media bucket).
     * @param extension  File extension without dot: "jpg", "m4a", "pdf", etc.
     * @param mimeType   MIME type for the Content-Type header.
     * Returns the public URL of the uploaded file.
     */
    suspend fun uploadMedia(
        carId: String,
        messageId: String,
        bytes: ByteArray,
        extension: String = "jpg",
        mimeType: String = "image/jpeg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val path = "$carId/$messageId.$extension"
            val bucket = supabase.storage.from("chat_media")
            bucket.upload(path = path, data = bytes, upsert = true)
            val url = bucket.publicUrl(path)
            Result.success(url)
        } catch (e: Exception) {
            Log.e("ChatRepo", "uploadMedia FAILED: ${e::class.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
