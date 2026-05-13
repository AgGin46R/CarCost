package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE carId = :carId ORDER BY createdAt ASC")
    fun getMessagesByCarId(carId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>)

    /** Insert new messages without touching existing ones — preserves chat_reactions cascade. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(messages: List<ChatMessage>)

    /** Update only the text content of an existing message (used to sync edits without cascade). */
    @Query("UPDATE chat_messages SET message = :message, isEdited = :isEdited WHERE id = :id")
    suspend fun updateContent(id: String, message: String, isEdited: Boolean)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE carId = :carId")
    suspend fun getCount(carId: String): Int

    @Query("SELECT * FROM chat_messages WHERE carId = :carId ORDER BY createdAt DESC LIMIT 1")
    fun getLastMessageByCarId(carId: String): Flow<ChatMessage?>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: String): ChatMessage?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE carId = :carId AND createdAt > :afterTimestamp AND userId != :currentUserId")
    fun getUnreadCount(carId: String, afterTimestamp: Long, currentUserId: String): Flow<Int>
}
