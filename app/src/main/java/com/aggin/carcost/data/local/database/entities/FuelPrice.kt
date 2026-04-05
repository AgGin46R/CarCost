package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class FuelGradeType {
    AI92, AI95, AI98, DIESEL, GAS, ELECTRIC
}

@Entity(tableName = "fuel_prices")
data class FuelPrice(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val stationName: String,
    val fuelType: FuelGradeType,
    val pricePerLiter: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val recordedAt: Long = System.currentTimeMillis()
)
