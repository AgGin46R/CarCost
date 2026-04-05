package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.VinCache

@Dao
interface VinCacheDao {
    @Query("SELECT * FROM vin_cache WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VinCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: VinCache)

    @Query("DELETE FROM vin_cache WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
