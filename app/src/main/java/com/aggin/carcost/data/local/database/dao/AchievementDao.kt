package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.AchievementType
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY unlockedAt DESC")
    fun getAchievements(userId: String): Flow<List<Achievement>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE userId = :userId AND type = :type)")
    suspend fun hasAchievement(userId: String, type: AchievementType): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: Achievement)

    @Query("SELECT COUNT(*) FROM achievements WHERE userId = :userId")
    fun getCount(userId: String): Flow<Int>
}
