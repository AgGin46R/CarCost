package com.aggin.carcost.data.remote.repository

import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MaintenanceReminderDto(
    val id: Long? = null,
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

/**
 * Репозиторий для работы с напоминаниями о техобслуживании через Supabase
 */
class SupabaseMaintenanceReminderRepository(private val authRepository: SupabaseAuthRepository) {

    /**
     * Создать напоминание
     */
    suspend fun insertReminder(reminder: MaintenanceReminder): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val reminderDto = reminder.toDto()

                val insertedReminder = supabase.from("maintenance_reminders")
                    .insert(reminderDto) {
                        select(Columns.ALL)
                    }
                    .decodeSingle<MaintenanceReminderDto>()

                Result.success(insertedReminder.toMaintenanceReminder())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получить все напоминания для автомобиля
     */
    suspend fun getRemindersByCarId(carId: Long): Result<List<MaintenanceReminder>> =
        withContext(Dispatchers.IO) {
            try {
                val reminders = supabase.from("maintenance_reminders")
                    .select {
                        filter {
                            eq("car_id", carId)
                        }
                        order("next_change_odometer", Order.DESCENDING)
                    }
                    .decodeList<MaintenanceReminderDto>()

                Result.success(reminders.map { it.toMaintenanceReminder() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получить активные напоминания для автомобиля
     */
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
                    .decodeList<MaintenanceReminderDto>()

                Result.success(reminders.map { it.toMaintenanceReminder() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получить напоминание по ID
     */
    suspend fun getReminderById(reminderId: Long): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val reminder = supabase.from("maintenance_reminders")
                    .select {
                        filter {
                            eq("id", reminderId)
                        }
                    }
                    .decodeSingle<MaintenanceReminderDto>()

                Result.success(reminder.toMaintenanceReminder())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получить напоминание по типу
     */
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
                .decodeList<MaintenanceReminderDto>()

            Result.success(reminders.firstOrNull()?.toMaintenanceReminder())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновить напоминание
     */
    suspend fun updateReminder(reminder: MaintenanceReminder): Result<MaintenanceReminder> =
        withContext(Dispatchers.IO) {
            try {
                val reminderDto = reminder.toDto().copy(
                    updatedAt = System.currentTimeMillis()
                )

                val updatedReminder = supabase.from("maintenance_reminders")
                    .update(reminderDto) {
                        filter {
                            eq("id", reminder.id)
                        }
                        select(Columns.ALL)
                    }
                    .decodeSingle<MaintenanceReminderDto>()

                Result.success(updatedReminder.toMaintenanceReminder())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Удалить напоминание
     */
    suspend fun deleteReminder(reminderId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("maintenance_reminders")
                .delete {
                    filter {
                        eq("id", reminderId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Удалить все напоминания для автомобиля
     */
    suspend fun deleteRemindersByCarId(carId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("maintenance_reminders")
                .delete {
                    filter {
                        eq("car_id", carId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить напоминания, обновленные после указанной временной метки
     */
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
                .decodeList<MaintenanceReminderDto>()

            Result.success(reminders.map { it.toMaintenanceReminder() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Extension functions для конвертации
private fun MaintenanceReminder.toDto() = MaintenanceReminderDto(
    id = if (id == 0L) null else id,
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
        MaintenanceType.OIL_CHANGE // fallback
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