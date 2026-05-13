package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.FluidLevel
import com.aggin.carcost.data.local.database.entities.FluidType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class FluidLevelDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val type: String,
    val level: Float,
    @SerialName("checked_at") val checkedAt: Long,
    val notes: String? = null,
    @SerialName("updated_at") val updatedAt: Long
)

private fun FluidLevel.toDto() = FluidLevelDto(
    id = id, carId = carId, type = type.name,
    level = level, checkedAt = checkedAt, notes = notes, updatedAt = updatedAt
)

private fun FluidLevelDto.toEntity() = FluidLevel(
    id = id, carId = carId,
    type = try { FluidType.valueOf(type) } catch (e: Exception) { FluidType.ENGINE_OIL },
    level = level, checkedAt = checkedAt, notes = notes, updatedAt = updatedAt
)

class SupabaseFluidLevelRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsertFluidLevel(level: FluidLevel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fluid_levels").upsert(level.toDto())
            Unit
        }
    }

    suspend fun getFluidLevelsByCarId(carId: String): Result<List<FluidLevel>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fluid_levels")
                .select { filter { eq("car_id", carId) } }
                .decodeList<FluidLevelDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fluid_levels").delete { filter { eq("id", id) } }
            Unit
        }
    }

    suspend fun getAllSync(): Result<List<FluidLevel>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fluid_levels")
                .select()
                .decodeList<FluidLevelDto>()
                .map { it.toEntity() }
        }
    }
}
