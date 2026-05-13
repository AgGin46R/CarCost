package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class InsurancePolicyDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val type: String,
    val company: String = "",
    @SerialName("policy_number") val policyNumber: String = "",
    @SerialName("start_date") val startDate: Long,
    @SerialName("end_date") val endDate: Long,
    val cost: Double = 0.0,
    val notes: String? = null
)

private fun InsurancePolicy.toDto() = InsurancePolicyDto(
    id = id, carId = carId, type = type,
    company = company, policyNumber = policyNumber,
    startDate = startDate, endDate = endDate,
    cost = cost, notes = notes
)

private fun InsurancePolicyDto.toEntity() = InsurancePolicy(
    id = id, carId = carId, type = type,
    company = company, policyNumber = policyNumber,
    startDate = startDate, endDate = endDate,
    cost = cost, notes = notes
)

class SupabaseInsurancePolicyRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(policy: InsurancePolicy): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("insurance_policies").upsert(policy.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<InsurancePolicy>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("insurance_policies")
                .select { filter { eq("car_id", carId) } }
                .decodeList<InsurancePolicyDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("insurance_policies").delete { filter { eq("id", id) } }; Unit }
    }
}
