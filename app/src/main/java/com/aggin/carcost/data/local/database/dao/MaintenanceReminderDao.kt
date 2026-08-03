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

    @Query("SELECT * FROM maintenance_reminders WHERE isActive = 1")
    suspend fun getAllActiveReminders(): List<MaintenanceReminder>

    @Query("SELECT * FROM maintenance_reminders WHERE isActive = 1 ORDER BY nextChangeOdometer ASC")
    fun getAllActiveRemindersFlow(): Flow<List<MaintenanceReminder>>

    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId ORDER BY nextChangeOdometer ASC")
    fun getAllRemindersByCarId(carId: String): Flow<List<MaintenanceReminder>>

    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId AND type = :type AND isActive = 1 LIMIT 1")
    suspend fun getReminderByType(carId: String, type: MaintenanceType): MaintenanceReminder?

    /**
     * Напоминание по идентификатору.
     *
     * Экран правки раньше искал его перебором: брал все автомобили и по каждому
     * запрашивал список напоминаний, пока не найдёт нужное. Хуже перебора было
     * то, ЧЕМ он это делал — `getRemindersByCarIdSync`, обычной несуспендящей
     * функцией. Room выполняет такую прямо на вызывающем потоке, а вызов шёл из
     * `viewModelScope.launch`, то есть с главного, — и Room честно бросал
     * «Cannot access database on the main thread». Приложение падало при каждой
     * попытке открыть созданное ТО.
     */
    @Query("SELECT * FROM maintenance_reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: String): MaintenanceReminder?

    @Update
    suspend fun updateReminder(reminder: MaintenanceReminder)

    @Query("DELETE FROM maintenance_reminders WHERE id = :id")
    suspend fun deleteReminder(id: String) // ✅ String UUID

    @Query("DELETE FROM maintenance_reminders WHERE carId = :carId")
    suspend fun deleteRemindersByCarId(carId: String)

    /** Synchronous query for use in Glance widget (runs on a background thread). */
    @Query("SELECT * FROM maintenance_reminders WHERE carId = :carId AND isActive = 1 ORDER BY nextChangeOdometer ASC")
    fun getRemindersByCarIdSync(carId: String): List<MaintenanceReminder>
}