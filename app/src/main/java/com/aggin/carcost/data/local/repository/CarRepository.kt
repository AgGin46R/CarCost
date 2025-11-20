package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.CarDao
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.flow.Flow

class CarRepository(private val carDao: CarDao) {

    // Create
    suspend fun insertCar(car: Car): Long {
        return carDao.insertCar(car)
    }

    // Read
    suspend fun getCarById(carId: Long): Car? {
        return carDao.getCarById(carId)
    }

    fun getCarByIdFlow(carId: Long): Flow<Car?> {
        return carDao.getCarByIdFlow(carId)
    }

    fun getAllActiveCars(): Flow<List<Car>> {
        return carDao.getAllActiveCars()
    }

    fun getAllCars(): Flow<List<Car>> {
        return carDao.getAllCars()
    }

    fun getLastActiveCar(): Flow<Car?> {
        return carDao.getLastActiveCar()
    }

    fun getActiveCarCount(): Flow<Int> {
        return carDao.getActiveCarCount()
    }

    // Update
    suspend fun updateCar(car: Car) {
        carDao.updateCar(car.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateOdometer(carId: Long, odometer: Int) {
        carDao.updateOdometer(carId, odometer)
    }

    suspend fun updateCarActiveStatus(carId: Long, isActive: Boolean) {
        carDao.updateCarActiveStatus(carId, isActive)
    }

    suspend fun updateCarPhoto(carId: Long, photoUri: String?) {
        carDao.updateCarPhoto(carId, photoUri)
    }

    // Delete
    suspend fun deleteCar(car: Car) {
        carDao.deleteCar(car)
    }

    suspend fun deleteCarById(carId: Long) {
        carDao.deleteCarById(carId)
    }

    // Business logic
    suspend fun archiveCar(carId: Long) {
        updateCarActiveStatus(carId, false)
    }

    suspend fun restoreCar(carId: Long) {
        updateCarActiveStatus(carId, true)
    }
}