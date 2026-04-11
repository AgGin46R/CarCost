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

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE carId = :carId")
    suspend fun getCount(carId: String): Int

    @Query("SELECT * FROM chat_messages WHERE carId = :carId ORDER BY createdAt DESC LIMIT 1")
    fun getLastMessageByCarId(carId: String): Flow<ChatMessage?>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE carId = :carId AND createdAt > :afterTimestamp")
    fun getUnreadCount(carId: String, afterTimestamp: Long): Flow<Int>
}
