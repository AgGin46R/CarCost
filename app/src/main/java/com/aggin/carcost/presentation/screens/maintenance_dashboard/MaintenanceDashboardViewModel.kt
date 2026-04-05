package com.aggin.carcost.presentation.screens.maintenance_dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ReminderUrgency { OVERDUE, SOON, OK }

data class ReminderWithCar(
    val reminder: MaintenanceReminder,
    val car: Car?,
    val kmRemaining: Int,
    val urgency: ReminderUrgency
)

data class MaintenanceDashboardUiState(
    val items: List<ReminderWithCar> = emptyList(),
    val isLoading: Boolean = true
)

class MaintenanceDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.maintenanceReminderDao()
    private val carDao = db.carDao()

    private val _uiState = MutableStateFlow(MaintenanceDashboardUiState())
    val uiState: StateFlow<MaintenanceDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderDao.getAllActiveRemindersFlow(),
                carDao.getAllCars()
            ) { reminders, cars ->
                val carMap = cars.associateBy { it.id }
                reminders.map { reminder ->
                    val car = carMap[reminder.carId]
                    val currentOdometer = car?.currentOdometer ?: reminder.lastChangeOdometer
                    val kmRemaining = reminder.nextChangeOdometer - currentOdometer
                    val urgency = when {
                        kmRemaining <= 0 -> ReminderUrgency.OVERDUE
                        kmRemaining <= 500 -> ReminderUrgency.SOON
                        else -> ReminderUrgency.OK
                    }
                    ReminderWithCar(reminder, car, kmRemaining, urgency)
                }.sortedWith(compareBy({ it.urgency.ordinal }, { it.kmRemaining }))
            }.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }
}
