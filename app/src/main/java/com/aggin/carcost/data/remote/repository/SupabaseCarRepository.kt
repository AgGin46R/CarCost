package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.OdometerUnit
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class CarDto(
    val id: Long? = null,
    @SerialName("user_id")
    val userId: String,
    val brand: String,
    val model: String,
    val year: Int,
    @SerialName("license_plate")
    val licensePlate: String,
    val vin: String? = null,
    val color: String? = null,
    @SerialName("photo_uri")
    val photoUri: String? = null,
    @SerialName("current_odometer")
    val currentOdometer: Int,
    @SerialName("odometer_unit")
    val odometerUnit: String = "KM",
    @SerialName("purchase_date")
    val purchaseDate: Long,
    @SerialName("purchase_price")
    val purchasePrice: Double? = null,
    @SerialName("purchase_odometer")
    val purchaseOdometer: Int? = null,
    @SerialName("fuel_type")
    val fuelType: String = "GASOLINE",
    @SerialName("tank_capacity")
    val tankCapacity: Double? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Репозиторий для работы с автомобилями через Supabase
 */
class SupabaseCarRepository(private val authRepository: SupabaseAuthRepository) {

    /**
     * Создать автомобиль
     */
    suspend fun insertCar(car: Car): Result<Car> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val carDto = car.toDto(userId)

            val insertedCar = supabase.from("cars")
                .insert(carDto) {
                    select(Columns.ALL)
                }
                .decodeSingle<CarDto>()

            Result.success(insertedCar.toCar())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить все автомобили пользователя
     */
    suspend fun getAllCars(): Result<List<Car>> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val cars = supabase.from("cars")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("updated_at", Order.DESCENDING)
                }
                .decodeList<CarDto>()

            Result.success(cars.map { it.toCar() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить активные автомобили
     */
    suspend fun getActiveCars(): Result<List<Car>> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val cars = supabase.from("cars")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                    order("updated_at", Order.DESCENDING)
                }
                .decodeList<CarDto>()

            Result.success(cars.map { it.toCar() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить автомобиль по ID
     */
    suspend fun getCarById(carId: Long): Result<Car> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val car = supabase.from("cars")
                .select {
                    filter {
                        eq("id", carId)
                        eq("user_id", userId)
                    }
                }
                .decodeSingle<CarDto>()

            Result.success(car.toCar())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновить автомобиль
     */
    suspend fun updateCar(car: Car): Result<Car> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val carDto = car.toDto(userId).copy(
                updatedAt = System.currentTimeMillis()
            )

            val updatedCar = supabase.from("cars")
                .update(carDto) {
                    filter {
                        eq("id", car.id)
                        eq("user_id", userId)
                    }
                    select(Columns.ALL)
                }
                .decodeSingle<CarDto>()

            Result.success(updatedCar.toCar())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Удалить автомобиль
     */
    suspend fun deleteCar(carId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            supabase.from("cars")
                .delete {
                    filter {
                        eq("id", carId)
                        eq("user_id", userId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить автомобили, обновленные после указанной временной метки
     */
    suspend fun getCarsUpdatedAfter(timestamp: Long): Result<List<Car>> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val cars = supabase.from("cars")
                .select {
                    filter {
                        eq("user_id", userId)
                        gt("updated_at", timestamp)
                    }
                }
                .decodeList<CarDto>()

            Result.success(cars.map { it.toCar() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Extension functions для конвертации между Car и CarDto
private fun Car.toDto(userId: String) = CarDto(
    id = if (id == 0L) null else id,
    userId = userId,
    brand = brand,
    model = model,
    year = year,
    licensePlate = licensePlate,
    vin = vin,
    color = color,
    photoUri = photoUri,
    currentOdometer = currentOdometer,
    odometerUnit = odometerUnit.name,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    purchaseOdometer = purchaseOdometer,
    fuelType = fuelType.name,
    tankCapacity = tankCapacity,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun CarDto.toCar() = Car(
    id = id ?: 0L,
    brand = brand,
    model = model,
    year = year,
    licensePlate = licensePlate,
    vin = vin,
    color = color,
    photoUri = photoUri,
    currentOdometer = currentOdometer,
    odometerUnit = try { OdometerUnit.valueOf(odometerUnit) } catch (e: Exception) { OdometerUnit.KM },
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    purchaseOdometer = purchaseOdometer,
    fuelType = try { FuelType.valueOf(fuelType) } catch (e: Exception) { FuelType.GASOLINE },
    tankCapacity = tankCapacity,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)