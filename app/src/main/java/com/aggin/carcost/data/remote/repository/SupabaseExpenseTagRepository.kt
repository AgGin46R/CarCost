package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ExpenseTagDto(
    val id: String, // ✅ String UUID
    val name: String,
    val color: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExpenseTagCrossRefDto(
    @SerialName("expense_id")
    val expenseId: String, // ✅ String UUID
    @SerialName("tag_id")
    val tagId: String // ✅ String UUID
)

class SupabaseExpenseTagRepository(private val authRepository: SupabaseAuthRepository) {

    suspend fun insertTag(tag: ExpenseTag): Result<ExpenseTag> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val tagDto = tag.toDto(userId)
            supabase.from("expense_tags").upsert(tagDto)
            Result.success(tag)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllTags(): Result<List<ExpenseTag>> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val tags = supabase.from("expense_tags")
                .select {
                    filter { eq("user_id", userId) }
                    order("name", Order.ASCENDING)
                }
                .decodeList<ExpenseTagDto>()

            Result.success(tags.map { it.toExpenseTag() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTagById(tagId: String): Result<ExpenseTag> = withContext(Dispatchers.IO) { // ✅ String
        try {
            val tags = supabase.from("expense_tags")
                .select { filter { eq("id", tagId) } }
                .decodeList<ExpenseTagDto>()

            val tag = tags.firstOrNull()
                ?: return@withContext Result.failure(Exception("Тег не найден"))

            Result.success(tag.toExpenseTag())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTagsForExpense(expenseId: String): Result<List<ExpenseTag>> = withContext(Dispatchers.IO) { // ✅ String
        try {
            val crossRefs = supabase.from("expense_tag_cross_ref")
                .select { filter { eq("expense_id", expenseId) } }
                .decodeList<ExpenseTagCrossRefDto>()

            if (crossRefs.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val tagIds = crossRefs.map { it.tagId }
            val tags = supabase.from("expense_tags")
                .select { filter { isIn("id", tagIds) } }
                .decodeList<ExpenseTagDto>()

            Result.success(tags.map { it.toExpenseTag() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTag(tag: ExpenseTag): Result<ExpenseTag> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val tagDto = tag.toDto(userId)

            supabase.from("expense_tags")
                .update(tagDto) {
                    filter {
                        eq("id", tag.id)
                        eq("user_id", userId)
                    }
                }

            Result.success(tag)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTag(tagId: String): Result<Unit> = withContext(Dispatchers.IO) { // ✅ String
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            supabase.from("expense_tags")
                .delete {
                    filter {
                        eq("id", tagId)
                        eq("user_id", userId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addTagToExpense(expenseId: String, tagId: String): Result<Unit> = withContext(Dispatchers.IO) { // ✅ String
        try {
            val crossRef = ExpenseTagCrossRefDto(expenseId = expenseId, tagId = tagId)
            supabase.from("expense_tag_cross_ref").insert(crossRef)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeTagFromExpense(expenseId: String, tagId: String): Result<Unit> = withContext(Dispatchers.IO) { // ✅ String
        try {
            supabase.from("expense_tag_cross_ref")
                .delete {
                    filter {
                        eq("expense_id", expenseId)
                        eq("tag_id", tagId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeAllTagsFromExpense(expenseId: String): Result<Unit> = withContext(Dispatchers.IO) { // ✅ String
        try {
            supabase.from("expense_tag_cross_ref")
                .delete { filter { eq("expense_id", expenseId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTagsForExpense(expenseId: String, tagIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) { // ✅ String
        try {
            removeAllTagsFromExpense(expenseId)

            if (tagIds.isNotEmpty()) {
                val crossRefs = tagIds.map { tagId ->
                    ExpenseTagCrossRefDto(expenseId = expenseId, tagId = tagId)
                }
                supabase.from("expense_tag_cross_ref").insert(crossRefs)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun ExpenseTag.toDto(userId: String) = ExpenseTagDto(
    id = id, // ✅ String UUID
    name = name,
    color = color,
    userId = userId,
    createdAt = createdAt
)

private fun ExpenseTagDto.toExpenseTag() = ExpenseTag(
    id = id, // ✅ String UUID
    name = name,
    color = color,
    userId = userId,
    createdAt = createdAt
)