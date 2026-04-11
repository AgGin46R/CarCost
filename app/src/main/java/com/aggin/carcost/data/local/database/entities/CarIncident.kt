package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "car_incidents",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId")]
)
data class CarIncident(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val date: Long,
    val type: IncidentType,
    val description: String,
    val damageAmount: Double? = null,
    val repairCost: Double? = null,
    val repairDate: Long? = null,
    val location: String? = null,
    val insuranceClaimNumber: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class IncidentType(val displayName: String, val emoji: String) {
    ACCIDENT("ДТП", "🚗"),
    SCRATCH("Царапина/Вмятина", "🔧"),
    THEFT("Угон/Кража", "🔓"),
    VANDALISM("Вандализм", "⚠️"),
    FLOOD("Затопление", "💧"),
    FIRE("Пожар", "🔥"),
    OTHER("Другое", "📋")
}
