package com.aggin.carcost.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aggin.carcost.data.local.database.entities.PendingWrite
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingWriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(write: PendingWrite)

    @Update
    suspend fun update(write: PendingWrite)

    @Query("DELETE FROM pending_writes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM pending_writes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingWrite>>

    @Query("SELECT * FROM pending_writes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingWrite>

    @Query("SELECT COUNT(*) FROM pending_writes")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_writes WHERE retryCount >= :maxRetries")
    suspend fun deleteExhausted(maxRetries: Int = 5)
}
