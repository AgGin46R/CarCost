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

/** Виды документов. Подпись — ключ ресурса, как и у остальных справочников */
enum class DocumentType(@StringRes val displayNameRes: Int) {
    INSURANCE(R.string.doc_insurance),
    REGISTRATION(R.string.doc_registration),
    TITLE(R.string.doc_title),
    DIAGNOSTIC_CARD(R.string.doc_diagnostic),
    WARRANTY(R.string.doc_warranty),
    PURCHASE_AGREEMENT(R.string.doc_purchase),
    OTHER(R.string.doc_other)
}
