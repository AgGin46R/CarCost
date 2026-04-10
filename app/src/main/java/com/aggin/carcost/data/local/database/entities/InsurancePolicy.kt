package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "insurance_policies",
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
data class InsurancePolicy(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val type: String,          // "OSAGO" | "KASKO" | "OTHER"
    val company: String = "",
    val policyNumber: String = "",
    val startDate: Long,
    val endDate: Long,
    val cost: Double = 0.0,
    val notes: String? = null
)
