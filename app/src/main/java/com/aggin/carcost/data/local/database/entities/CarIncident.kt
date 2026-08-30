package com.aggin.carcost.data.local.database.entities

import androidx.annotation.StringRes
import com.aggin.carcost.R
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
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

/** Виды происшествий. Подпись — ключ ресурса, как и у остальных справочников */
enum class IncidentType(@StringRes val displayNameRes: Int, val emoji: String) {
    ACCIDENT(R.string.incident_accident, "🚗"),
    SCRATCH(R.string.incident_scratch, "🔧"),
    THEFT(R.string.incident_theft, "🔓"),
    VANDALISM(R.string.incident_vandalism, "⚠️"),
    FLOOD(R.string.incident_flood, "💧"),
    FIRE(R.string.incident_fire, "🔥"),
    OTHER(R.string.incident_other, "📋")
}
