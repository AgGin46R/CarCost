package com.aggin.carcost.presentation.screens.maintenance_dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class EditMaintenanceReminderUiState(
    val cars: List<Car> = emptyList(),
    val selectedCarId: String = "",
    val selectedType: MaintenanceType = MaintenanceType.OIL_CHANGE,
    val lastChangeOdometer: String = "",
    val intervalKm: String = "",
    val notes: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val nextChangeOdometer: Int
        get() {
            val last = lastChangeOdometer.toIntOrNull() ?: 0
            val interval = intervalKm.toIntOrNull() ?: selectedType.defaultInterval
            return last + interval
        }

    val canSave: Boolean
        get() = selectedCarId.isNotBlank()
                && lastChangeOdometer.toIntOrNull() != null
                && intervalKm.toIntOrNull() != null
}

class EditMaintenanceReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.maintenanceReminderDao()
    private val carDao = db.carDao()

    private val _uiState = MutableStateFlow(EditMaintenanceReminderUiState())
    val uiState: StateFlow<EditMaintenanceReminderUiState> = _uiState.asStateFlow()

    private var editingId: String? = null

    init {
        viewModelScope.launch {
            val cars = carDao.getAllActiveCarsSync()
            _uiState.update {
                it.copy(
                    cars = cars,
                    selectedCarId = cars.firstOrNull()?.id ?: "",
                    isLoading = false
                )
            }
        }
    }

    fun initForCreate(preselectedCarId: String?) {
        if (preselectedCarId != null && preselectedCarId.isNotBlank()) {
            _uiState.update { it.copy(selectedCarId = preselectedCarId) }
        }
    }

    fun loadReminder(reminderId: String) {
        viewModelScope.launch {
            // Search through all car reminders to find by id
            val cars = _uiState.value.cars.ifEmpty { carDao.getAllActiveCarsSync() }
            var found: MaintenanceReminder? = null
            for (car in cars) {
                val reminders = reminderDao.getRemindersByCarIdSync(car.id)
                found = reminders.firstOrNull { it.id == reminderId }
                if (found != null) break
            }
            found?.let { r ->
                editingId = r.id
                _uiState.update {
                    it.copy(
                        selectedCarId = r.carId,
                        selectedType = r.type,
                        lastChangeOdometer = r.lastChangeOdometer.toString(),
                        intervalKm = r.intervalKm.toString(),
                        notes = r.notes ?: "",
                        isEditMode = true
                    )
                }
            }
        }
    }

    fun updateCar(carId: String) = _uiState.update { it.copy(selectedCarId = carId) }
    fun updateType(type: MaintenanceType) {
        _uiState.update {
            it.copy(
                selectedType = type,
                intervalKm = type.defaultInterval.toString()
            )
        }
    }
    fun updateLastOdometer(value: String) = _uiState.update { it.copy(lastChangeOdometer = value.filter { c -> c.isDigit() }) }
    fun updateIntervalKm(value: String) = _uiState.update { it.copy(intervalKm = value.filter { c -> c.isDigit() }) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val reminder = MaintenanceReminder(
                    id = editingId ?: UUID.randomUUID().toString(),
                    carId = state.selectedCarId,
                    type = state.selectedType,
                    lastChangeOdometer = state.lastChangeOdometer.toInt(),
                    intervalKm = state.intervalKm.toInt(),
                    nextChangeOdometer = state.nextChangeOdometer,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isActive = true,
                    updatedAt = System.currentTimeMillis()
                )
                reminderDao.insertReminder(reminder)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteReminder(reminderId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminderId)
            onDone()
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
