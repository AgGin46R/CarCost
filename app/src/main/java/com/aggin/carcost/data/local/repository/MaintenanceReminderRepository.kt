package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.MaintenanceReminderDao
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import kotlinx.coroutines.flow.Flow

class MaintenanceReminderRepository(private val dao: MaintenanceReminderDao) {

    fun getActiveReminders(carId: Long): Flow<List<MaintenanceReminder>> {
        return dao.getActiveReminders(carId)
    }

    suspend fun getReminderByType(carId: Long, type: MaintenanceType): MaintenanceReminder? {
        return dao.getReminderByType(carId, type)
    }

    suspend fun insertReminder(reminder: MaintenanceReminder): Long {
        return dao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: MaintenanceReminder) {
        dao.updateReminder(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteReminder(id: Long) {
        dao.deleteReminder(id)
    }

    // Обновить напоминание после выполнения ТО
    suspend fun updateAfterMaintenance(
        carId: Long,
        type: MaintenanceType,
        currentOdometer: Int
    ) {
        val reminder = getReminderByType(carId, type)
        if (reminder != null) {
            // Обновляем существующее напоминание
            val updated = reminder.copy(
                lastChangeOdometer = currentOdometer,
                nextChangeOdometer = currentOdometer + reminder.intervalKm,
                lastChangeDate = System.currentTimeMillis(),
                isActive = true
            )
            updateReminder(updated)
        } else {
            // Создаем новое напоминание
            val newReminder = MaintenanceReminder(
                carId = carId,
                type = type,
                lastChangeOdometer = currentOdometer,
                intervalKm = type.defaultInterval,
                nextChangeOdometer = currentOdometer + type.defaultInterval,
                isActive = true
            )
            insertReminder(newReminder)
        }
    }
}