package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.CarDocument
import com.aggin.carcost.data.local.database.entities.DocumentType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CarDocumentDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val type: String,
    val title: String,
    @SerialName("expiry_date") val expiryDate: Long? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
    // file_uri intentionally excluded — device-local path, meaningless for other users
)

private fun CarDocument.toDto() = CarDocumentDto(
    id = id, carId = carId, type = type.name, title = title,
    expiryDate = expiryDate, notes = notes,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun CarDocumentDto.toEntity() = CarDocument(
    id = id, carId = carId,
    type = try { DocumentType.valueOf(type) } catch (e: Exception) { DocumentType.OTHER },
    title = title, fileUri = null, // fileUri is device-local
    expiryDate = expiryDate, notes = notes,
    createdAt = createdAt, updatedAt = updatedAt
)

class SupabaseCarDocumentRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(document: CarDocument): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("car_documents").upsert(document.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<CarDocument>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("car_documents")
                .select { filter { eq("car_id", carId) } }
                .decodeList<CarDocumentDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("car_documents").delete { filter { eq("id", id) } }; Unit }
    }
}
