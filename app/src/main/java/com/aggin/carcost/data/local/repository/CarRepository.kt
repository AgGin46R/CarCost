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
    suspend fun getCarById(carId: String): Car? {
        return carDao.getCarById(carId)
    }

    fun getCarByIdFlow(carId: String): Flow<Car?> {
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

    suspend fun updateOdometer(carId: String, odometer: Int) {
        carDao.updateOdometer(carId, odometer)
    }

    suspend fun updateCarActiveStatus(carId: String, isActive: Boolean) {
        carDao.updateCarActiveStatus(carId, isActive)
    }

    suspend fun updateCarPhoto(carId: String, photoUri: String?) {
        carDao.updateCarPhoto(carId, photoUri)
    }

    // Delete
    suspend fun deleteCar(car: Car) {
        carDao.deleteCar(car)
    }

    suspend fun deleteCarById(carId: String) {
        carDao.deleteCarById(carId)
    }

    // Business logic
    suspend fun archiveCar(carId: String) {
        updateCarActiveStatus(carId, false)
    }

    suspend fun restoreCar(carId: String) {
        updateCarActiveStatus(carId, true)
    }
}