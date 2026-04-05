package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "category_budgets",
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
data class CategoryBudget(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val category: ExpenseCategory,
    val monthlyLimit: Double,
    val month: Int,  // 1-12
    val year: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
