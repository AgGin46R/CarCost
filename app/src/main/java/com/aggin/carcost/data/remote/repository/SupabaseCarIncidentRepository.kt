package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.IncidentType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CarIncidentDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val date: Long,
    val type: String,
    val description: String,
    @SerialName("damage_amount") val damageAmount: Double? = null,
    @SerialName("repair_cost") val repairCost: Double? = null,
    @SerialName("repair_date") val repairDate: Long? = null,
    val location: String? = null,
    @SerialName("insurance_claim_number") val insuranceClaimNumber: String? = null,
    @SerialName("photo_uri") val photoUri: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: Long
)

private fun CarIncident.toDto() = CarIncidentDto(
    id = id, carId = carId, date = date,
    type = type.name, description = description,
    damageAmount = damageAmount, repairCost = repairCost,
    repairDate = repairDate, location = location,
    insuranceClaimNumber = insuranceClaimNumber,
    photoUri = photoUri, notes = notes, createdAt = createdAt
)

private fun CarIncidentDto.toEntity() = CarIncident(
    id = id, carId = carId, date = date,
    type = try { IncidentType.valueOf(type) } catch (e: Exception) { IncidentType.OTHER },
    description = description, damageAmount = damageAmount,
    repairCost = repairCost, repairDate = repairDate,
    location = location, insuranceClaimNumber = insuranceClaimNumber,
    photoUri = null, // photoUri is device-local, don't restore
    notes = notes, createdAt = createdAt
)

class SupabaseCarIncidentRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(incident: CarIncident): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("car_incidents").upsert(incident.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<CarIncident>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("car_incidents")
                .select { filter { eq("car_id", carId) } }
                .decodeList<CarIncidentDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("car_incidents").delete { filter { eq("id", id) } }; Unit }
    }
}
