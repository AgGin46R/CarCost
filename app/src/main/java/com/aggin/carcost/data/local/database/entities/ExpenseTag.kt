package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(tableName = "expense_tags")
data class ExpenseTag(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // ✅ String UUID
    val name: String,
    val color: String, // e.g., "#FF5733"
    val userId: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Промежуточная таблица для связи "многие ко многим"
@Entity(
    tableName = "expense_tag_cross_ref",
    primaryKeys = ["expenseId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["expenseId"]),
        Index(value = ["tagId"])
    ]
)
data class ExpenseTagCrossRef(
    val expenseId: String, // ✅ String UUID
    val tagId: String      // ✅ String UUID
)

// POJO для запроса, который возвращает тег с количеством расходов
data class TagWithExpenseCount(
    val id: String, // ✅ String UUID
    val name: String,
    val color: String,
    val userId: String,
    val createdAt: Long,
    val expenseCount: Int
)