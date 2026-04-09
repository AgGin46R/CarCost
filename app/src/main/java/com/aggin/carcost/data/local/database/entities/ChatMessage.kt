package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId"), Index("createdAt")]
)
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val carId: String,
    val userId: String,
    val userEmail: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null,
    // "image" | "audio" | "file" | null (null = совместимость со старыми записями = image)
    val mediaType: String? = null,
    // Оригинальное имя файла/длительность голосового (напр. "voice_12s.m4a", "document.pdf")
    val fileName: String? = null
)
