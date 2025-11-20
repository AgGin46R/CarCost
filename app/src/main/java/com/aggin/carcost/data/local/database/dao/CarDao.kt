package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: Car): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCars(cars: List<Car>)

    // READ
    @Query("SELECT * FROM cars WHERE id = :carId")
    suspend fun getCarById(carId: Long): Car?

    @Query("SELECT * FROM cars WHERE id = :carId")
    fun getCarByIdFlow(carId: Long): Flow<Car?>

    @Query("SELECT * FROM cars WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllActiveCars(): Flow<List<Car>>

    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    fun getAllCars(): Flow<List<Car>>

    @Query("SELECT * FROM cars WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun getLastActiveCar(): Flow<Car?>

    @Query("SELECT COUNT(*) FROM cars WHERE isActive = 1")
    fun getActiveCarCount(): Flow<Int>

    // UPDATE
    @Update
    suspend fun updateCar(car: Car)

    @Query("UPDATE cars SET currentOdometer = :odometer, updatedAt = :timestamp WHERE id = :carId")
    suspend fun updateOdometer(carId: Long, odometer: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cars SET isActive = :isActive, updatedAt = :timestamp WHERE id = :carId")
    suspend fun updateCarActiveStatus(carId: Long, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cars SET photoUri = :photoUri, updatedAt = :timestamp WHERE id = :carId")
    suspend fun updateCarPhoto(carId: Long, photoUri: String?, timestamp: Long = System.currentTimeMillis())

    // DELETE
    @Delete
    suspend fun deleteCar(car: Car)

    @Query("DELETE FROM cars WHERE id = :carId")
    suspend fun deleteCarById(carId: Long)

    @Query("DELETE FROM cars")
    suspend fun deleteAllCars()
}