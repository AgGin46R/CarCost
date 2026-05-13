package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.Achievement
import com.aggin.carcost.data.local.database.entities.AchievementType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AchievementDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("unlocked_at") val unlockedAt: Long,
    val metadata: String? = null
)

private fun Achievement.toDto() = AchievementDto(
    id = id, userId = userId, type = type.name,
    unlockedAt = unlockedAt, metadata = metadata
)

private fun AchievementDto.toEntity() = Achievement(
    id = id, userId = userId,
    type = try { AchievementType.valueOf(type) } catch (e: Exception) { AchievementType.FIRST_EXPENSE },
    unlockedAt = unlockedAt, metadata = metadata
)

class SupabaseAchievementRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(achievement: Achievement): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("achievements").upsert(achievement.toDto()); Unit }
    }

    suspend fun getByUserId(userId: String): Result<List<Achievement>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("achievements")
                .select { filter { eq("user_id", userId) } }
                .decodeList<AchievementDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("achievements").delete { filter { eq("id", id) } }; Unit }
    }
}
