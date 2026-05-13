package com.aggin.carcost.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aggin.carcost.data.local.database.entities.FluidLevel
import com.aggin.carcost.data.local.database.entities.FluidType
import kotlinx.coroutines.flow.Flow

@Dao
interface FluidLevelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fluidLevel: FluidLevel)

    @Update
    suspend fun update(fluidLevel: FluidLevel)

    @Query("SELECT * FROM fluid_levels WHERE carId = :carId ORDER BY type, checkedAt DESC")
    fun getFluidLevelsByCarId(carId: String): Flow<List<FluidLevel>>

    @Query("SELECT * FROM fluid_levels WHERE carId = :carId ORDER BY type, checkedAt DESC")
    suspend fun getFluidLevelsByCarIdSync(carId: String): List<FluidLevel>

    /** Последняя запись для каждого типа жидкости (по одному на тип) */
    @Query("""
        SELECT * FROM fluid_levels
        WHERE carId = :carId
        AND checkedAt = (
            SELECT MAX(checkedAt) FROM fluid_levels fl2
            WHERE fl2.carId = fluid_levels.carId AND fl2.type = fluid_levels.type
        )
    """)
    fun getLatestFluidLevels(carId: String): Flow<List<FluidLevel>>

    @Query("""
        SELECT * FROM fluid_levels
        WHERE carId = :carId
        AND checkedAt = (
            SELECT MAX(checkedAt) FROM fluid_levels fl2
            WHERE fl2.carId = fluid_levels.carId AND fl2.type = fluid_levels.type
        )
    """)
    suspend fun getLatestFluidLevelsSync(carId: String): List<FluidLevel>

    @Query("SELECT * FROM fluid_levels WHERE carId = :carId AND type = :type ORDER BY checkedAt DESC LIMIT 1")
    suspend fun getLatestFluidLevel(carId: String, type: FluidType): FluidLevel?

    @Query("DELETE FROM fluid_levels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM fluid_levels WHERE carId = :carId")
    suspend fun deleteByCarId(carId: String)

    @Query("SELECT * FROM fluid_levels")
    suspend fun getAllSync(): List<FluidLevel>
}
