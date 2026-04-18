package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Emoji reaction on a chat message. Uniqueness is enforced at insertion time:
 * the client (and Supabase constraint) treats (messageId, userId, emoji) as a
 * composite key — a user can only have one of each emoji per message. Different
 * users may use the same emoji independently.
 */
@Entity(
    tableName = "chat_reactions",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("messageId"),
        Index(value = ["messageId", "userId", "emoji"], unique = true)
    ]
)
data class ChatReaction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val messageId: String,
    val userId: String,
    val userEmail: String,
    val emoji: String,
    val createdAt: Long = System.currentTimeMillis()
)
