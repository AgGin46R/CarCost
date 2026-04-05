package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.FuelPrice
import com.aggin.carcost.data.local.database.entities.FuelGradeType
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelPriceDao {
    @Query("SELECT * FROM fuel_prices ORDER BY recordedAt DESC")
    fun getAllFuelPrices(): Flow<List<FuelPrice>>

    @Query("SELECT * FROM fuel_prices WHERE fuelType = :type ORDER BY recordedAt DESC LIMIT 50")
    fun getPricesByType(type: FuelGradeType): Flow<List<FuelPrice>>

    @Query("SELECT AVG(pricePerLiter) FROM fuel_prices WHERE fuelType = :type AND recordedAt > :since")
    suspend fun getAveragePrice(type: FuelGradeType, since: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: FuelPrice)

    @Delete
    suspend fun delete(price: FuelPrice)
}
