package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MemberRole {
    OWNER, DRIVER, MECHANIC
}

@Entity(
    tableName = "car_members",
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
data class CarMember(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val userId: String,
    val email: String,
    val role: MemberRole,
    val joinedAt: Long = System.currentTimeMillis()
)
