package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "fluid_levels",
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
data class FluidLevel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val type: FluidType,
    val level: Float,           // 0.0 – 1.0
    val checkedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
