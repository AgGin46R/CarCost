package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.GpsTrip
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTripDao {
    @Query("SELECT * FROM gps_trips WHERE carId = :carId ORDER BY startTime DESC")
    fun getTripsByCarId(carId: String): Flow<List<GpsTrip>>

    @Query("SELECT SUM(distanceKm) FROM gps_trips WHERE carId = :carId")
    suspend fun getTotalDistance(carId: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: GpsTrip): Long

    @Update
    suspend fun update(trip: GpsTrip)

    @Delete
    suspend fun delete(trip: GpsTrip)

    @Query("SELECT * FROM gps_trips WHERE id = :id")
    suspend fun getById(id: String): GpsTrip?

    @Query("SELECT * FROM gps_trips WHERE carId = :carId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastTripForCar(carId: String): GpsTrip?

    @Query("SELECT * FROM gps_trips WHERE carId = :carId AND startTime >= :since ORDER BY startTime DESC")
    fun getTripsSince(carId: String, since: Long): Flow<List<GpsTrip>>

    @Query("SELECT MAX(distanceKm) FROM gps_trips WHERE carId = :carId")
    suspend fun getLongestTripDistance(carId: String): Double?

    @Query("SELECT AVG(distanceKm) FROM gps_trips WHERE carId = :carId")
    suspend fun getAvgTripDistance(carId: String): Double?
}
