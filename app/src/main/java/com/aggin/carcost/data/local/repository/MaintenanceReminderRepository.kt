package com.aggin.carcost.data.local.repository

import android.util.Log
import com.aggin.carcost.data.local.database.dao.MaintenanceReminderDao
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import com.aggin.carcost.data.local.database.entities.ServiceType
import kotlinx.coroutines.flow.Flow

class MaintenanceReminderRepository(private val dao: MaintenanceReminderDao) {

    fun getActiveReminders(carId: String): Flow<List<MaintenanceReminder>> {
        return dao.getActiveReminders(carId)
    }

    suspend fun getReminderByType(carId: String, type: MaintenanceType): MaintenanceReminder? {
        return dao.getReminderByType(carId, type)
    }

    suspend fun insertReminder(reminder: MaintenanceReminder): String { // ✅ Возвращает String
        dao.insertReminder(reminder)
        return reminder.id // ✅ Возвращаем ID
    }

    suspend fun updateReminder(reminder: MaintenanceReminder) {
        dao.updateReminder(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteReminder(id: String) { // ✅ Принимает String
        dao.deleteReminder(id)
    }

    /**
     * ✅ Удалить напоминание определённого типа
     */
    suspend fun deleteReminderByType(carId: String, type: MaintenanceType) {
        Log.d("ReminderRepo", "🔴 deleteReminderByType called: carId=$carId, type=$type")

        try {
            val reminder = dao.getReminderByType(carId, type)
            Log.d("ReminderRepo", "Found reminder: $reminder")

            if (reminder != null) {
                Log.d("ReminderRepo", "Deleting reminder ID: ${reminder.id}")
                dao.deleteReminder(reminder.id)
                Log.d("ReminderRepo", "✅ Successfully deleted reminder: car=$carId, type=$type, id=${reminder.id}")
            } else {
                Log.w("ReminderRepo", "⚠️ No reminder found for car=$carId, type=$type")
            }
        } catch (e: Exception) {
            Log.e("ReminderRepo", "❌ Error in deleteReminderByType", e)
            e.printStackTrace()
        }
    }

    /**
     * Обновить напоминание после выполнения ТО
     */
    suspend fun updateAfterMaintenance(
        carId: String,
        type: MaintenanceType,
        currentOdometer: Int
    ) {
        Log.d("ReminderRepo", "updateAfterMaintenance: carId=$carId, type=$type, odometer=$currentOdometer")

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
            Log.d("ReminderRepo", "✅ Updated reminder: $type at $currentOdometer km → next: ${updated.nextChangeOdometer} km")
        } else {
            // Создаём новое напоминание
            val newReminder = MaintenanceReminder(
                carId = carId,
                type = type,
                lastChangeOdometer = currentOdometer,
                intervalKm = type.defaultInterval,
                nextChangeOdometer = currentOdometer + type.defaultInterval,
                isActive = true
            )
            val id = insertReminder(newReminder)
            Log.d("ReminderRepo", "✅ Created new reminder: $type at $currentOdometer km (ID: $id)")
        }
    }

    /**
     * ✅ Обновить напоминание при редактировании расхода ТО
     */
    suspend fun updateAfterExpenseEdit(
        carId: String,
        oldServiceType: ServiceType?,
        newServiceType: ServiceType?,
        newOdometer: Int
    ) {
        Log.d("ReminderRepo", "updateAfterExpenseEdit: carId=$carId")
        Log.d("ReminderRepo", "  Old: $oldServiceType → New: $newServiceType, odometer=$newOdometer")

        val oldType = oldServiceType?.let { convertServiceTypeToMaintenanceType(it) }
        val newType = newServiceType?.let { convertServiceTypeToMaintenanceType(it) }

        Log.d("ReminderRepo", "  Converted: Old=$oldType → New=$newType")

        if (oldType != newType) {
            Log.d("ReminderRepo", "  Service type CHANGED - deleting old, creating new")

            // Удаляем старое напоминание
            oldType?.let {
                Log.d("ReminderRepo", "  Deleting old reminder: $it")
                deleteReminderByType(carId, it)
            }

            // Создаём новое напоминание
            newType?.let {
                Log.d("ReminderRepo", "  Creating new reminder: $it")
                updateAfterMaintenance(carId, it, newOdometer)
            }
        } else if (newType != null) {
            // Тип не изменился - просто обновляем пробег
            Log.d("ReminderRepo", "  Service type UNCHANGED: $newType - updating odometer")
            updateAfterMaintenance(carId, newType, newOdometer)
        } else {
            Log.w("ReminderRepo", "  ⚠️ Both types are NULL - no action taken")
        }
    }

    /**
     * Конвертировать ServiceType в MaintenanceType
     */
    private fun convertServiceTypeToMaintenanceType(serviceType: ServiceType): MaintenanceType? {
        val result = when (serviceType) {
            ServiceType.OIL_CHANGE -> MaintenanceType.OIL_CHANGE
            ServiceType.OIL_FILTER -> MaintenanceType.OIL_FILTER
            ServiceType.AIR_FILTER -> MaintenanceType.AIR_FILTER
            ServiceType.CABIN_FILTER -> MaintenanceType.CABIN_FILTER
            ServiceType.FUEL_FILTER -> MaintenanceType.FUEL_FILTER
            ServiceType.SPARK_PLUGS -> MaintenanceType.SPARK_PLUGS
            ServiceType.BRAKE_PADS -> MaintenanceType.BRAKE_PADS
            ServiceType.TIMING_BELT -> MaintenanceType.TIMING_BELT
            ServiceType.TRANSMISSION_FLUID -> MaintenanceType.TRANSMISSION_FLUID
            ServiceType.COOLANT -> MaintenanceType.COOLANT
            ServiceType.BRAKE_FLUID -> MaintenanceType.BRAKE_FLUID
            else -> null
        }
        Log.d("ReminderRepo", "convertServiceTypeToMaintenanceType: $serviceType → $result")
        return result
    }

    /**
     * ✅ Публичный метод для конвертации
     */
    fun serviceTypeToMaintenanceType(serviceType: ServiceType): MaintenanceType? {
        return convertServiceTypeToMaintenanceType(serviceType)
    }
}
