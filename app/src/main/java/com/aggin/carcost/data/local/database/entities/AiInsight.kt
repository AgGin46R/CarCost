package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class InsightType {
    ANOMALY, SEASONAL_TIP, COST_SPIKE, BUDGET_ALERT, MAINTENANCE_PREDICTION,
    FUEL_EFFICIENCY, SAVINGS_OPPORTUNITY, GENERAL
}

enum class InsightSeverity {
    INFO, WARNING, CRITICAL
}

@Entity(
    tableName = "ai_insights",
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
data class AiInsight(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val type: InsightType,
    val title: String,
    val body: String,
    val severity: InsightSeverity = InsightSeverity.INFO,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
