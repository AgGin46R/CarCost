package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Основная информация
    val brand: String,
    val model: String,
    val year: Int,
    val licensePlate: String,

    // Дополнительная информация
    val vin: String? = null,
    val color: String? = null,
    val photoUri: String? = null,

    // Пробег
    val currentOdometer: Int, // в километрах
    val odometerUnit: OdometerUnit = OdometerUnit.KM,

    // Покупка
    val purchaseDate: Long, // timestamp
    val purchasePrice: Double? = null,
    val purchaseOdometer: Int? = null,

    // Топливо
    val fuelType: FuelType = FuelType.GASOLINE,
    val tankCapacity: Double? = null, // в литрах

    // Статус
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable

enum class OdometerUnit {
    KM,  // Километры
    MI   // Мили
}

enum class FuelType {
    GASOLINE,      // Бензин
    DIESEL,        // Дизель
    ELECTRIC,      // Электро
    HYBRID,        // Гибрид
    GAS,           // Газ
    OTHER
}