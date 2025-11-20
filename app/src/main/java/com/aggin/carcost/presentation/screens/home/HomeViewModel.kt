package com.aggin.carcost.presentation.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val cars: List<Car> = emptyList(),
    val remindersByCarId: Map<Long, List<MaintenanceReminder>> = emptyMap(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())

    val uiState: StateFlow<HomeUiState> = carRepository.getAllActiveCars()
        .flatMapLatest { cars ->
            if (cars.isEmpty()) {
                flowOf(HomeUiState(cars = emptyList(), isLoading = false))
            } else {
                combine(cars.map { car ->
                    reminderRepository.getActiveReminders(car.id).map { reminders ->
                        car.id to reminders
                    }
                }) { remindersArray ->
                    HomeUiState(
                        cars = cars,
                        remindersByCarId = remindersArray.toMap(),
                        isLoading = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    fun deleteCar(car: Car) {
        viewModelScope.launch {
            carRepository.deleteCar(car)
        }
    }
}