package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.AiInsight
import kotlinx.coroutines.flow.Flow

@Dao
interface AiInsightDao {
    @Query("SELECT * FROM ai_insights WHERE carId = :carId ORDER BY createdAt DESC")
    fun getInsightsByCarId(carId: String): Flow<List<AiInsight>>

    @Query("SELECT * FROM ai_insights WHERE carId = :carId AND isRead = 0 ORDER BY createdAt DESC")
    fun getUnreadInsights(carId: String): Flow<List<AiInsight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsights(insights: List<AiInsight>)

    @Query("UPDATE ai_insights SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("DELETE FROM ai_insights WHERE carId = :carId")
    suspend fun deleteByCarId(carId: String)

    @Query("SELECT COUNT(*) FROM ai_insights WHERE carId = :carId AND isRead = 0")
    fun getUnreadCount(carId: String): Flow<Int>
}
