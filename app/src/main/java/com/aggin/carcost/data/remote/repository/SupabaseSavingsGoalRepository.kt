package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.SavingsGoal
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SavingsGoalDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val title: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    val deadline: Long? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

private fun SavingsGoal.toDto() = SavingsGoalDto(
    id = id, carId = carId, title = title,
    targetAmount = targetAmount, currentAmount = currentAmount,
    deadline = deadline, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun SavingsGoalDto.toEntity() = SavingsGoal(
    id = id, carId = carId, title = title,
    targetAmount = targetAmount, currentAmount = currentAmount,
    deadline = deadline, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt
)

class SupabaseSavingsGoalRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(goal: SavingsGoal): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("savings_goals").upsert(goal.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<SavingsGoal>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("savings_goals")
                .select { filter { eq("car_id", carId) } }
                .decodeList<SavingsGoalDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("savings_goals").delete { filter { eq("id", id) } }; Unit }
    }
}
