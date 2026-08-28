package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: Car): Long

    /**
     * Запись автомобиля, пришедшего с сервера.
     *
     * НЕ через INSERT OR REPLACE. В SQLite REPLACE — это удаление строки и
     * вставка новой, а от cars каскадом висит всё: расходы, напоминания, чат,
     * документы, страховки, бюджеты, поездки. То есть каждое обновление
     * автомобиля с сервера сначала стирало локально всю его историю, а потом
     * синхронизация возвращала из облака то, что там уже есть. Всё, что не
     * успело уехать на сервер — запись, добавленная без сети, — исчезало
     * навсегда. Совладелец переименовал машину, и у второго пропадали расходы.
     *
     * Upsert обновляет строку на месте, ничего не удаляя.
     */
    @Upsert
    suspend fun upsertCar(car: Car)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCars(cars: List<Car>)

    // READ
    @Query("SELECT * FROM cars WHERE id = :carId")
    suspend fun getCarById(carId: String): Car?

    @Query("SELECT * FROM cars WHERE id = :carId")
    fun getCarByIdFlow(carId: String): Flow<Car?>

    @Query("SELECT * FROM cars WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllActiveCars(): Flow<List<Car>>

    @Query("SELECT * FROM cars WHERE isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getAllActiveCarsSync(): List<Car>

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
    suspend fun updateOdometer(carId: String, odometer: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cars SET isActive = :isActive, updatedAt = :timestamp WHERE id = :carId")
    suspend fun updateCarActiveStatus(carId: String, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cars SET photoUri = :photoUri, updatedAt = :timestamp WHERE id = :carId")
    suspend fun updateCarPhoto(carId: String, photoUri: String?, timestamp: Long = System.currentTimeMillis())

    // DELETE
    @Delete
    suspend fun deleteCar(car: Car)

    @Query("DELETE FROM cars WHERE id = :carId")
    suspend fun deleteCarById(carId: String)

    @Query("DELETE FROM cars")
    suspend fun deleteAllCars()
}