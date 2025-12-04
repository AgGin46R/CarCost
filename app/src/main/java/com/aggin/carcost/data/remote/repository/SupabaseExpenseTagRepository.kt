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
    val id: Long? = null,
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
    val expenseId: Long,
    @SerialName("tag_id")
    val tagId: Long
)

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
class SupabaseExpenseTagRepository(private val authRepository: SupabaseAuthRepository) {

    suspend fun insertTag(tag: ExpenseTag): Result<ExpenseTag> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val tagDto = tag.toDto(userId)
            supabase.from("expense_tags").insert(tagDto)
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
                .decodeAs<List<ExpenseTagDto>>()

            Result.success(tags.map { it.toExpenseTag() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTagById(tagId: Long): Result<ExpenseTag> = withContext(Dispatchers.IO) {
        try {
            val tags = supabase.from("expense_tags")
                .select { filter { eq("id", tagId) } }
                .decodeAs<List<ExpenseTagDto>>()

            val tag = tags.firstOrNull()
                ?: return@withContext Result.failure(Exception("Тег не найден"))

            Result.success(tag.toExpenseTag())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTagsForExpense(expenseId: Long): Result<List<ExpenseTag>> = withContext(Dispatchers.IO) {
        try {
            val crossRefs = supabase.from("expense_tag_cross_ref")
                .select { filter { eq("expense_id", expenseId) } }
                .decodeAs<List<ExpenseTagCrossRefDto>>()

            if (crossRefs.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val tagIds = crossRefs.map { it.tagId }
            val tags = supabase.from("expense_tags")
                .select { filter { isIn("id", tagIds) } }
                .decodeAs<List<ExpenseTagDto>>()

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

    suspend fun deleteTag(tagId: Long): Result<Unit> = withContext(Dispatchers.IO) {
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

    suspend fun addTagToExpense(expenseId: Long, tagId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val crossRef = ExpenseTagCrossRefDto(expenseId = expenseId, tagId = tagId)
            supabase.from("expense_tag_cross_ref").insert(crossRef)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeTagFromExpense(expenseId: Long, tagId: Long): Result<Unit> = withContext(Dispatchers.IO) {
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

    suspend fun removeAllTagsFromExpense(expenseId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("expense_tag_cross_ref")
                .delete { filter { eq("expense_id", expenseId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTagsForExpense(expenseId: Long, tagIds: List<Long>): Result<Unit> = withContext(Dispatchers.IO) {
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
    id = if (id == 0L) null else id,
    name = name,
    color = color,
    userId = userId,
    createdAt = createdAt
)

private fun ExpenseTagDto.toExpenseTag() = ExpenseTag(
    id = id ?: 0L,
    name = name,
    color = color,
    userId = userId,
    createdAt = createdAt
)