package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ExpenseDto(
    val id: Long? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("car_id")
    val carId: Long,
    val category: String,
    val amount: Double,
    val currency: String = "RUB",
    val date: Long,
    val odometer: Int,
    val title: String? = null,
    val description: String? = null,
    @SerialName("receipt_photo_uri")
    val receiptPhotoUri: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("fuel_liters")
    val fuelLiters: Double? = null,
    @SerialName("fuel_type")
    val fuelType: String? = null,
    @SerialName("is_full_tank")
    val isFullTank: Boolean = false,
    @SerialName("service_type")
    val serviceType: String? = null,
    @SerialName("next_service_odometer")
    val nextServiceOdometer: Int? = null,
    @SerialName("next_service_date")
    val nextServiceDate: Long? = null,
    @SerialName("workshop_name")
    val workshopName: String? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

class SupabaseExpenseRepository(private val authRepository: SupabaseAuthRepository) {

    suspend fun insertExpense(expense: Expense): Result<Expense> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val expenseDto = expense.toDto(userId)

            val response = supabase.from("expenses")
                .insert(expenseDto)

            // Получаем вставленную запись через select
            val insertedList = supabase.from("expenses")
                .select {
                    filter { eq("id", expense.id) }
                }
                .decodeAs<List<ExpenseDto>>()

            val insertedExpense = insertedList.firstOrNull()
                ?: return@withContext Result.failure(Exception("Не удалось получить вставленную запись"))

            Result.success(insertedExpense.toExpense())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExpensesByCarId(carId: Long): Result<List<Expense>> = withContext(Dispatchers.IO) {
        try {
            val expenses = supabase.from("expenses")
                .select {
                    filter { eq("car_id", carId) }
                    order("date", Order.DESCENDING)
                }
                .decodeAs<List<ExpenseDto>>()

            Result.success(expenses.map { it.toExpense() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUserExpenses(): Result<List<Expense>> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val expenses = supabase.from("expenses")
                .select {
                    filter { eq("user_id", userId) }
                    order("date", Order.DESCENDING)
                }
                .decodeAs<List<ExpenseDto>>()

            Result.success(expenses.map { it.toExpense() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateExpense(expense: Expense): Result<Expense> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getUserId()
                ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

            val expenseDto = expense.toDto(userId).copy(updatedAt = System.currentTimeMillis())

            supabase.from("expenses")
                .update(expenseDto) {
                    filter { eq("id", expense.id) }
                }

            // Получаем обновленную запись
            val updatedList = supabase.from("expenses")
                .select {
                    filter { eq("id", expense.id) }
                }
                .decodeAs<List<ExpenseDto>>()

            val updatedExpense = updatedList.firstOrNull()
                ?: return@withContext Result.failure(Exception("Не удалось получить обновленную запись"))

            Result.success(updatedExpense.toExpense())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("expenses").delete { filter { eq("id", expenseId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ✅ КРИТИЧНО: userId добавлен в toDto!
private fun Expense.toDto(userId: String) = ExpenseDto(
    id = if (id == 0L) null else id,
    userId = userId,
    carId = carId,
    category = category.name,
    amount = amount,
    currency = currency,
    date = date,
    odometer = odometer,
    title = title,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    location = location,
    latitude = latitude,
    longitude = longitude,
    fuelLiters = fuelLiters,
    fuelType = fuelType,
    isFullTank = isFullTank,
    serviceType = serviceType?.name,
    nextServiceOdometer = nextServiceOdometer,
    nextServiceDate = nextServiceDate,
    workshopName = workshopName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ExpenseDto.toExpense() = Expense(
    id = id ?: 0L,
    carId = carId,
    category = try { ExpenseCategory.valueOf(category) } catch (e: Exception) { ExpenseCategory.OTHER },
    amount = amount,
    currency = currency,
    date = date,
    odometer = odometer,
    title = title,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    location = location,
    latitude = latitude,
    longitude = longitude,
    fuelLiters = fuelLiters,
    fuelType = fuelType,
    isFullTank = isFullTank,
    serviceType = serviceType?.let { try { ServiceType.valueOf(it) } catch (e: Exception) { null } },
    nextServiceOdometer = nextServiceOdometer,
    nextServiceDate = nextServiceDate,
    workshopName = workshopName,
    createdAt = createdAt,
    updatedAt = updatedAt
)