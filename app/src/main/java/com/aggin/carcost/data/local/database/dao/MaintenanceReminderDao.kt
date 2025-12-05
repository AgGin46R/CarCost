package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: MaintenanceReminder) // ✅ Void - не возвращает ID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<MaintenanceReminder>)

    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId AND isActive = 1 ORDER BY nextChangeOdometer ASC")
    fun getActiveReminders(carId: String): Flow<List<MaintenanceReminder>>

    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId ORDER BY nextChangeOdometer ASC")
    fun getAllRemindersByCarId(carId: String): Flow<List<MaintenanceReminder>>

    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId AND type = :type AND isActive = 1 LIMIT 1")
    suspend fun getReminderByType(carId: String, type: MaintenanceType): MaintenanceReminder?

    @Update
    suspend fun updateReminder(reminder: MaintenanceReminder)

    @Query("DELETE FROM maintenance_reminders WHERE id = :id")
    suspend fun deleteReminder(id: String) // ✅ String UUID

    @Query("DELETE FROM maintenance_reminders WHERE carId = :carId")
    suspend fun deleteRemindersByCarId(carId: String)
}