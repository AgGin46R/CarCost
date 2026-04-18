package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.data.local.database.entities.ChatReaction
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatReactionDto(
    val id: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_email") val userEmail: String,
    val emoji: String,
    @SerialName("created_at") val createdAt: Long
)

fun ChatReactionDto.toChatReaction() = ChatReaction(
    id = id,
    messageId = messageId,
    userId = userId,
    userEmail = userEmail,
    emoji = emoji,
    createdAt = createdAt
)

fun ChatReaction.toDto() = ChatReactionDto(
    id = id,
    messageId = messageId,
    userId = userId,
    userEmail = userEmail,
    emoji = emoji,
    createdAt = createdAt
)

/**
 * Supabase `chat_reactions` table schema (run once in SQL editor):
 *
 *   CREATE TABLE IF NOT EXISTS chat_reactions (
 *       id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 *       message_id  uuid NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
 *       user_id     uuid NOT NULL,
 *       user_email  text NOT NULL,
 *       emoji       text NOT NULL,
 *       created_at  bigint NOT NULL DEFAULT (extract(epoch from now())*1000)::bigint,
 *       UNIQUE (message_id, user_id, emoji)
 *   );
 *   ALTER TABLE chat_reactions ENABLE ROW LEVEL SECURITY;
 *   CREATE POLICY "reactions_select" ON chat_reactions FOR SELECT USING (true);
 *   CREATE POLICY "reactions_insert" ON chat_reactions FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
 *   CREATE POLICY "reactions_delete" ON chat_reactions FOR DELETE USING (auth.uid()::text = user_id::text);
 */
class SupabaseChatReactionsRepository {

    private val TAG = "ChatReactionsRepo"

    suspend fun addReaction(reaction: ChatReaction): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("chat_reactions").insert(reaction.toDto())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addReaction failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun removeReaction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("chat_reactions").delete {
                filter { eq("id", id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeReaction failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Fetch reactions for all messages of a car. Used on chat open to populate local DB. */
    suspend fun getReactionsForCar(carId: String): Result<List<ChatReaction>> = withContext(Dispatchers.IO) {
        try {
            // Join through chat_messages — fetch via a Postgres RPC or two-step query.
            // For simplicity, first fetch message IDs then reactions in one IN-filter.
            val msgIds = supabase.from("chat_messages")
                .select {
                    filter { eq("car_id", carId) }
                }
                .decodeList<ChatMessageDto>()
                .map { it.id }
            if (msgIds.isEmpty()) return@withContext Result.success(emptyList())

            val dtos = supabase.from("chat_reactions")
                .select {
                    filter { isIn("message_id", msgIds) }
                }
                .decodeList<ChatReactionDto>()
            Result.success(dtos.map { it.toChatReaction() })
        } catch (e: Exception) {
            Log.e(TAG, "getReactionsForCar failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
