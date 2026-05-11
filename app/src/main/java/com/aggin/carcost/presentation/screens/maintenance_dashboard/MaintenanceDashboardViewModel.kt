package com.aggin.carcost.presentation.screens.maintenance_dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.domain.maintenance.MaintenancePredictionEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReminderUrgency { OVERDUE, SOON, OK }

data class ReminderWithCar(
    val reminder: MaintenanceReminder,
    val car: Car?,
    val kmRemaining: Int,
    val urgency: ReminderUrgency,
    val predictedDate: LocalDate? = null
)

data class MaintenanceDashboardUiState(
    val items: List<ReminderWithCar> = emptyList(),
    val isLoading: Boolean = true
)

class MaintenanceDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.maintenanceReminderDao()
    private val carDao = db.carDao()
    private val gpsTripDao = db.gpsTripDao()

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
                    val kmRemaining = (reminder.nextChangeOdometer ?: currentOdometer) - currentOdometer

                    // Date-based urgency check
                    val daysRemaining = reminder.nextChangeDate?.let { nextDate ->
                        ((nextDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                    }

                    val urgency = when {
                        kmRemaining <= 0 || daysRemaining != null && daysRemaining <= 0 -> ReminderUrgency.OVERDUE
                        kmRemaining <= 500 || daysRemaining != null && daysRemaining <= 7 -> ReminderUrgency.SOON
                        else -> ReminderUrgency.OK
                    }
                    val predicted = if (car != null && reminder.nextChangeOdometer != null) {
                        try {
                            val since = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                            val trips = gpsTripDao.getTripsSince(car.id, since).firstOrNull() ?: emptyList()
                            MaintenancePredictionEngine.predictNextServiceDate(car, reminder, trips)
                        } catch (_: Exception) { null }
                    } else null
                    ReminderWithCar(reminder, car, kmRemaining, urgency, predicted)
                }.sortedWith(compareBy({ it.urgency.ordinal }, { it.kmRemaining }))
            }.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }
}
