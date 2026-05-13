package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.CarIncident
import kotlinx.coroutines.flow.Flow

@Dao
interface CarIncidentDao {

    @Query("SELECT * FROM car_incidents WHERE carId = :carId ORDER BY date DESC")
    fun getIncidentsByCarId(carId: String): Flow<List<CarIncident>>

    @Query("SELECT * FROM car_incidents WHERE carId = :carId ORDER BY date DESC")
    suspend fun getIncidentsByCarIdSync(carId: String): List<CarIncident>

    @Query("SELECT * FROM car_incidents WHERE id = :id")
    suspend fun getIncidentById(id: String): CarIncident?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: CarIncident)

    @Update
    suspend fun updateIncident(incident: CarIncident)

    @Delete
    suspend fun deleteIncident(incident: CarIncident)
}
