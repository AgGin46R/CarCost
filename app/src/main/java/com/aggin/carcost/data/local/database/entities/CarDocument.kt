package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "car_documents",
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
data class CarDocument(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val type: DocumentType,
    val title: String,
    val fileUri: String?,
    val expiryDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DocumentType(val displayName: String) {
    INSURANCE("Страховка (ОСАГО/КАСКО)"),
    REGISTRATION("Свидетельство о регистрации (СТС)"),
    TITLE("ПТС"),
    DIAGNOSTIC_CARD("Диагностическая карта"),
    WARRANTY("Гарантийный талон"),
    PURCHASE_AGREEMENT("Договор купли-продажи"),
    OTHER("Другое")
}
