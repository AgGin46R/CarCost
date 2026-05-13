package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.CategoryBudget
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CategoryBudgetDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    val category: String,
    @SerialName("monthly_limit") val monthlyLimit: Double,
    val month: Int,
    val year: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

private fun CategoryBudget.toDto() = CategoryBudgetDto(
    id = id, carId = carId, category = category.name,
    monthlyLimit = monthlyLimit, month = month, year = year,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun CategoryBudgetDto.toEntity() = CategoryBudget(
    id = id, carId = carId,
    category = try { ExpenseCategory.valueOf(category) } catch (e: Exception) { ExpenseCategory.OTHER },
    monthlyLimit = monthlyLimit, month = month, year = year,
    createdAt = createdAt, updatedAt = updatedAt
)

class SupabaseCategoryBudgetRepository(private val auth: SupabaseAuthRepository) {

    suspend fun upsert(budget: CategoryBudget): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("category_budgets").upsert(budget.toDto()); Unit }
    }

    suspend fun getByCarId(carId: String): Result<List<CategoryBudget>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("category_budgets")
                .select { filter { eq("car_id", carId) } }
                .decodeList<CategoryBudgetDto>()
                .map { it.toEntity() }
        }
    }

    suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { supabase.from("category_budgets").delete { filter { eq("id", id) } }; Unit }
    }
}
