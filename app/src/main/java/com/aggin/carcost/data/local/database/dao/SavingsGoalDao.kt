package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.SavingsGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE carId = :carId ORDER BY createdAt DESC")
    fun getGoalsByCarId(carId: String): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE carId = :carId ORDER BY createdAt DESC")
    suspend fun getGoalsByCarIdSync(carId: String): List<SavingsGoal>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: String): SavingsGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal)

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)
}
