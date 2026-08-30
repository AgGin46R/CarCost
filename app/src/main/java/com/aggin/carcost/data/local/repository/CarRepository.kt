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

    /**
     * Запись, пришедшая С СЕРВЕРА, — без перештамповки updatedAt.
     *
     * Обычный update ставит updatedAt = сейчас. Для загрузки это ломало всё:
     * скачанная запись немедленно становилась «новее» серверной, на следующей
     * синхронизации уходила обратно UPDATE'ом, сервер ставил своё «сейчас» —
     * и так по кругу, вечно. Каждая хоть раз синхронизированная запись давала
     * лишний запрос при КАЖДОМ разворачивании приложения: у совладельца с 300
     * расходами это 300 запросов на ровном месте.
     *
     * Здесь метка времени сохраняется серверная — она и есть источник истины
     * для сравнения "кто новее".
     */
    suspend fun saveFromServer(car: Car) {
        carDao.upsertCar(mergeOdometer(car))
    }

    /**
     * Пробег с сервера не должен опускать локальный.
     *
     * Пробег выводится из записей расходов, а не вводится отдельно. Запись
     * уезжает на сервер сразу, а пробег автомобиля — только следующей
     * синхронизацией, и в промежутке на сервере лежит старое значение. Без
     * этой проверки загрузка автомобиля возвращала его обратно поверх верного:
     * человек вносил расход на больший пробег, обновлял экран и видел прежнее
     * число.
     *
     * Берём большее. Пробег только растёт — это то же правило, по которому он
     * считается из записей.
     */
    private suspend fun mergeOdometer(fromServer: Car): Car {
        val local = carDao.getCarById(fromServer.id) ?: return fromServer
        return if (local.currentOdometer > fromServer.currentOdometer) {
            // Метку времени ставим свою, а не серверную: иначе локальная запись
            // навсегда останется «старее» серверной и верный пробег никогда не
            // уедет наверх — на сервере так и будет лежать прежнее число.
            // С новой меткой ближайшая синхронизация его туда отправит, после
            // чего значения сойдутся и слияние больше не сработает.
            fromServer.copy(
                currentOdometer = local.currentOdometer,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            fromServer
        }
    }

    suspend fun updateCar(car: Car) {
        carDao.updateCar(car.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateOdometer(carId: String, odometer: Int) {
        carDao.updateOdometer(carId, odometer)
    }

    /**
     * Подтягивает пробег автомобиля к наибольшему из его расходов.
     *
     * Вызывается после любого изменения расходов — своего, совладельца или
     * пришедшего синхронизацией. Пробег только растёт: указанный вручную на
     * карточке не сбрасывается вниз, если записи отстают от него.
     *
     * @return true, если значение изменилось
     */
    suspend fun refreshOdometerFromExpenses(carId: String, expenseDao: com.aggin.carcost.data.local.database.dao.ExpenseDao): Boolean {
        val car = carDao.getCarById(carId) ?: return false
        val fromExpenses = expenseDao.getMaxOdometer(carId) ?: 0
        if (fromExpenses <= car.currentOdometer) return false
        carDao.updateOdometer(carId, fromExpenses)
        return true
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