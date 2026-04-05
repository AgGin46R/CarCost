package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "gps_trips",
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
data class GpsTrip(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceKm: Double = 0.0,
    val routeJson: String? = null,  // JSON array of {lat, lng} points
    val avgSpeedKmh: Double? = null
)
