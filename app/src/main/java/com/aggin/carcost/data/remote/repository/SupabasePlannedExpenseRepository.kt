package com.aggin.carcost.data.remote.repository

import android.util.Log
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.PlannedExpensePriority
import com.aggin.carcost.data.local.database.entities.PlannedExpenseStatus
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlannedExpenseDto(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String? = null,
    val category: String,
    @SerialName("estimated_amount") val estimatedAmount: Double? = null,
    @SerialName("actual_amount") val actualAmount: Double? = null,
    @SerialName("target_date") val targetDate: Long? = null,
    @SerialName("completed_date") val completedDate: Long? = null,
    val priority: String,
    val status: String,
    @SerialName("target_odometer") val targetOdometer: Int? = null,
    val notes: String? = null,
    @SerialName("shop_url") val shopUrl: String? = null,
    @SerialName("linked_expense_id") val linkedExpenseId: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

class SupabasePlannedExpenseRepository(
    private val authRepository: SupabaseAuthRepository
) {

    companion object {
        private const val TAG = "SupabasePlannedExpense"
    }

    /**
     * Добавить запланированную покупку
     */
    suspend fun insertPlannedExpense(plannedExpense: PlannedExpense): Result<PlannedExpense> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = authRepository.getUserId()
                    ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

                val dto = plannedExpense.toDto(userId)

                Log.d(TAG, "🔄 UPSERT planned expense: ${plannedExpense.id}")

                val upserted = supabase.from("planned_expenses")
                    .upsert(dto) {
                        select(Columns.ALL)
                    }
                    .decodeSingle<PlannedExpenseDto>()

                Log.d(TAG, "✅ UPSERT successful: ${upserted.id}")
                Result.success(upserted.toPlannedExpense())
            } catch (e: Exception) {
                Log.e(TAG, "❌ INSERT failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Обновить запланированную покупку
     */
    suspend fun updatePlannedExpense(plannedExpense: PlannedExpense): Result<PlannedExpense> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = authRepository.getUserId()
                    ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

                val dto = plannedExpense.toDto(userId)

                Log.d(TAG, "🔄 UPDATE planned expense: ${plannedExpense.id}")

                val updated = supabase.from("planned_expenses")
                    .update(dto) {
                        filter {
                            eq("id", plannedExpense.id)
                        }
                        select(Columns.ALL)
                    }
                    .decodeSingleOrNull<PlannedExpenseDto>()

                if (updated != null) {
                    Log.d(TAG, "✅ UPDATE successful: ${updated.id}")
                    Result.success(updated.toPlannedExpense())
                } else {
                    Log.w(TAG, "⚠️ UPDATE returned null, using UPSERT")
                    insertPlannedExpense(plannedExpense)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ UPDATE failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Получить запланированную покупку по ID
     */
    suspend fun getPlannedExpenseById(id: String): Result<PlannedExpense?> {
        return withContext(Dispatchers.IO) {
            try {
                val plannedExpense = supabase.from("planned_expenses")
                    .select {
                        filter {
                            eq("id", id)
                        }
                    }
                    .decodeSingleOrNull<PlannedExpenseDto>()

                Result.success(plannedExpense?.toPlannedExpense())
            } catch (e: Exception) {
                Log.e(TAG, "❌ GET by ID failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Получить все запланированные покупки для автомобиля
     */
    suspend fun getPlannedExpensesByCarId(carId: String): Result<List<PlannedExpense>> {
        return withContext(Dispatchers.IO) {
            try {
                val plannedExpenses = supabase.from("planned_expenses")
                    .select {
                        filter {
                            eq("car_id", carId)
                        }
                    }
                    .decodeList<PlannedExpenseDto>()
                    .sortedWith(compareByDescending<PlannedExpenseDto> { it.priority }
                        .thenBy { it.targetDate ?: Long.MAX_VALUE })

                Result.success(plannedExpenses.map { it.toPlannedExpense() })
            } catch (e: Exception) {
                Log.e(TAG, "❌ GET by car ID failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Получить запланированные покупки по статусу
     */
    suspend fun getPlannedExpensesByStatus(
        carId: String,
        status: PlannedExpenseStatus
    ): Result<List<PlannedExpense>> {
        return withContext(Dispatchers.IO) {
            try {
                val plannedExpenses = supabase.from("planned_expenses")
                    .select {
                        filter {
                            eq("car_id", carId)
                            eq("status", status.name)
                        }
                    }
                    .decodeList<PlannedExpenseDto>()
                    .sortedWith(compareByDescending<PlannedExpenseDto> { it.priority }
                        .thenBy { it.targetDate ?: Long.MAX_VALUE })

                Result.success(plannedExpenses.map { it.toPlannedExpense() })
            } catch (e: Exception) {
                Log.e(TAG, "❌ GET by status failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Удалить запланированную покупку
     */
    suspend fun deletePlannedExpense(id: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.from("planned_expenses")
                    .delete {
                        filter {
                            eq("id", id)
                        }
                    }

                Log.d(TAG, "✅ DELETE successful: $id")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "❌ DELETE failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Получить обновленные записи после определенной даты
     */
    suspend fun getPlannedExpensesUpdatedAfter(
        carId: String,
        timestamp: Long
    ): Result<List<PlannedExpense>> {
        return withContext(Dispatchers.IO) {
            try {
                val plannedExpenses = supabase.from("planned_expenses")
                    .select {
                        filter {
                            eq("car_id", carId)
                            gt("updated_at", timestamp)
                        }
                    }
                    .decodeList<PlannedExpenseDto>()

                Result.success(plannedExpenses.map { it.toPlannedExpense() })
            } catch (e: Exception) {
                Log.e(TAG, "❌ GET updated after failed", e)
                Result.failure(e)
            }
        }
    }
}

// Extension functions
private fun PlannedExpense.toDto(userId: String) = PlannedExpenseDto(
    id = id,
    carId = carId,
    userId = userId,
    title = title,
    description = description,
    category = category.name,
    estimatedAmount = estimatedAmount,
    actualAmount = actualAmount,
    targetDate = targetDate,
    completedDate = completedDate,
    priority = priority.name,
    status = status.name,
    targetOdometer = targetOdometer,
    notes = notes,
    shopUrl = shopUrl,
    linkedExpenseId = linkedExpenseId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun PlannedExpenseDto.toPlannedExpense() = PlannedExpense(
    id = id,
    carId = carId,
    userId = userId,
    title = title,
    description = description,
    category = ExpenseCategory.valueOf(category),
    estimatedAmount = estimatedAmount,
    actualAmount = actualAmount,
    targetDate = targetDate,
    completedDate = completedDate,
    priority = PlannedExpensePriority.valueOf(priority),
    status = PlannedExpenseStatus.valueOf(status),
    targetOdometer = targetOdometer,
    notes = notes,
    shopUrl = shopUrl,
    linkedExpenseId = linkedExpenseId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = true
)