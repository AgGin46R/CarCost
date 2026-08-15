package com.aggin.carcost.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aggin.carcost.data.local.database.entities.ChatReaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatReactionDao {

    @Query("SELECT * FROM chat_reactions WHERE messageId IN (:messageIds)")
    fun getReactionsForMessages(messageIds: List<String>): Flow<List<ChatReaction>>

    @Query("SELECT * FROM chat_reactions WHERE messageId = :messageId")
    suspend fun getReactionsForMessageSync(messageId: String): List<ChatReaction>

    @Query("SELECT * FROM chat_reactions WHERE id = :id")
    suspend fun getById(id: String): ChatReaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reaction: ChatReaction)

    @Query("DELETE FROM chat_reactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_reactions WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji")
    suspend fun deleteByUserAndEmoji(messageId: String, userId: String, emoji: String)

    @Query("SELECT * FROM chat_reactions WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji LIMIT 1")
    suspend fun findByUserAndEmoji(messageId: String, userId: String, emoji: String): ChatReaction?

    /**
     * Убрать реакции, которых больше нет на сервере.
     *
     * Обновление с сервера раньше только добавляло строки. Реакция, снятая
     * кем-то другим, пока приложение было закрыто, оставалась на экране навсегда:
     * событие о её удалении пришло в момент, когда слушать было некому, а
     * повторная загрузка её не убирала — только досыпала недостающие.
     *
     * Список идентификаторов приходит с сервера и считается истиной.
     */
    @Query("DELETE FROM chat_reactions WHERE messageId IN (:messageIds) AND id NOT IN (:keepIds)")
    suspend fun deleteMissing(messageIds: List<String>, keepIds: List<String>)
}
