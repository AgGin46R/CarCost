package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MaintenanceReminderDto(
    val id: Long? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("car_id")
    val carId: Long,
    val type: String,
    @SerialName("last_change_odometer")
    val lastChangeOdometer: Int,
    @SerialName("last_change_date")
    val lastChangeDate: Long = System.currentTimeMillis(),
    @SerialName("interval_km")
    val intervalKm: Int,
    @SerialName("next_change_odometer")
    val nextChangeOdometer: Int,
    @SerialName("is_active")
    val isActive: Boolean = true,
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
class SupabaseMaintenanceReminderRepository(private val authRepository: SupabaseAuthRepository) {

    suspend fun insertReminder(reminder: MaintenanceReminder): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val userId = authRepository.getUserId()
                    ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

                val reminderDto = reminder.toDto(userId)
                supabase.from("maintenance_reminders").insert(reminderDto)
                Result.success(reminder)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getRemindersByCarId(carId: Long): Result<List<MaintenanceReminder>> =
        withContext(Dispatchers.IO) {
            try {
                val reminders = supabase.from("maintenance_reminders")
                    .select {
                        filter { eq("car_id", carId) }
                        order("next_change_odometer", Order.DESCENDING)
                    }
                    .decodeAs<List<MaintenanceReminderDto>>()

                Result.success(reminders.map { it.toMaintenanceReminder() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getActiveReminders(carId: Long): Result<List<MaintenanceReminder>> =
        withContext(Dispatchers.IO) {
            try {
                val reminders = supabase.from("maintenance_reminders")
                    .select {
                        filter {
                            eq("car_id", carId)
                            eq("is_active", true)
                        }
                        order("next_change_odometer", Order.DESCENDING)
                    }
                    .decodeAs<List<MaintenanceReminderDto>>()

                Result.success(reminders.map { it.toMaintenanceReminder() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getReminderById(reminderId: Long): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val reminders = supabase.from("maintenance_reminders")
                    .select { filter { eq("id", reminderId) } }
                    .decodeAs<List<MaintenanceReminderDto>>()

                val reminder = reminders.firstOrNull()
                    ?: return@withContext Result.failure(Exception("Напоминание не найдено"))

                Result.success(reminder.toMaintenanceReminder())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getReminderByType(
        carId: Long,
        type: MaintenanceType
    ): Result<MaintenanceReminder?> = withContext(Dispatchers.IO) {
        try {
            val reminders = supabase.from("maintenance_reminders")
                .select {
                    filter {
                        eq("car_id", carId)
                        eq("type", type.name)
                        eq("is_active", true)
                    }
                    limit(1)
                }
                .decodeAs<List<MaintenanceReminderDto>>()

            Result.success(reminders.firstOrNull()?.toMaintenanceReminder())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReminder(reminder: MaintenanceReminder): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val userId = authRepository.getUserId()
                    ?: return@withContext Result.failure(Exception("Пользователь не аутентифицирован"))

                val reminderDto = reminder.toDto(userId).copy(
                    updatedAt = System.currentTimeMillis()
                )

                supabase.from("maintenance_reminders")
                    .update(reminderDto) {
                        filter { eq("id", reminder.id) }
                    }

                Result.success(reminder)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteReminder(reminderId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("maintenance_reminders")
                .delete { filter { eq("id", reminderId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRemindersByCarId(carId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("maintenance_reminders")
                .delete { filter { eq("car_id", carId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRemindersUpdatedAfter(
        carId: Long,
        timestamp: Long
    ): Result<List<MaintenanceReminder>> = withContext(Dispatchers.IO) {
        try {
            val reminders = supabase.from("maintenance_reminders")
                .select {
                    filter {
                        eq("car_id", carId)
                        gt("updated_at", timestamp)
                    }
                }
                .decodeAs<List<MaintenanceReminderDto>>()

            Result.success(reminders.map { it.toMaintenanceReminder() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun MaintenanceReminder.toDto(userId: String) = MaintenanceReminderDto(
    id = if (id == 0L) null else id,
    userId = userId,
    carId = carId,
    type = type.name,
    lastChangeOdometer = lastChangeOdometer,
    lastChangeDate = lastChangeDate,
    intervalKm = intervalKm,
    nextChangeOdometer = nextChangeOdometer,
    isActive = isActive,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun MaintenanceReminderDto.toMaintenanceReminder() = MaintenanceReminder(
    id = id ?: 0L,
    carId = carId,
    type = try {
        MaintenanceType.valueOf(type)
    } catch (e: Exception) {
        MaintenanceType.OIL_CHANGE
    },
    lastChangeOdometer = lastChangeOdometer,
    lastChangeDate = lastChangeDate,
    intervalKm = intervalKm,
    nextChangeOdometer = nextChangeOdometer,
    isActive = isActive,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)