package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "expense_tags")
data class ExpenseTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val expenseId: Long,
    val tagId: Long
)

// POJO для запроса, который возвращает тег с количеством расходов
data class TagWithExpenseCount(
    val id: Long,
    val name: String,
    val color: String,
    val userId: String,
    val createdAt: Long,
    val expenseCount: Int
)