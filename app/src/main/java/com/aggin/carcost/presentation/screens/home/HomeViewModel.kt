package com.aggin.carcost.presentation.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.CarInvitationDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val cars: List<Car> = emptyList(),
    val remindersByCarId: Map<String, List<MaintenanceReminder>> = emptyMap(),
    val isLoading: Boolean = true,
    val pendingInvitations: List<CarInvitationDto> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseMembers = SupabaseCarMembersRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)

    private val _pendingInvitations = MutableStateFlow<List<CarInvitationDto>>(emptyList())

    init {
        checkPendingInvitations()
        syncRemindersForAllCars()
        syncCarsIfLocalEmpty()
    }

    /**
     * Pulls maintenance reminders from Supabase for every car the user can see locally.
     * This ensures the car card badge is correct for shared cars where reminders were
     * created by the owner and never written to the driver's local Room DB.
     */
    private fun syncRemindersForAllCars() {
        viewModelScope.launch {
            try {
                val cars = carRepository.getAllActiveCars().first()
                cars.forEach { car ->
                    supabaseReminderRepo.getRemindersByCarId(car.id)
                        .onSuccess { reminders ->
                            reminders.forEach { reminder ->
                                try {
                                    database.maintenanceReminderDao().insertReminder(reminder)
                                } catch (_: Exception) {}
                            }
                        }
                }
            } catch (e: Exception) {
                Log.d("HomeViewModel", "Reminder sync skipped: ${e.message}")
            }
        }
    }

    private fun syncCarsIfLocalEmpty() {
        viewModelScope.launch {
            try {
                val localCars = carRepository.getAllActiveCars().first()
                if (localCars.isEmpty() && supabaseAuth.isUserLoggedIn()) {
                    Log.d("HomeViewModel", "Local cars empty — pulling from Supabase...")

                    // 1. Собственные машины
                    val ownedCars = mutableListOf<com.aggin.carcost.data.local.database.entities.Car>()
                    supabaseCarRepo.getAllCars()
                        .onSuccess { ownedCars.addAll(it) }
                        .onFailure { Log.e("HomeViewModel", "Failed to pull owned cars", it) }

                    // 2. Shared машины из car_members
                    val sharedCarIds = mutableListOf<String>()
                    supabaseMembers.getMyMemberCarIds()
                        .onSuccess { ids ->
                            // только те, которых нет среди собственных
                            sharedCarIds.addAll(ids.filter { id -> ownedCars.none { it.id == id } })
                        }
                        .onFailure { Log.e("HomeViewModel", "Failed to get member car ids", it) }

                    val sharedCars = mutableListOf<com.aggin.carcost.data.local.database.entities.Car>()
                    sharedCarIds.forEach { carId ->
                        supabaseCarRepo.fetchSharedCar(carId)
                            .onSuccess { sharedCars.add(it) }
                            .onFailure { Log.w("HomeViewModel", "Could not fetch shared car $carId") }
                    }

                    // 3. Вставляем всё в локальную БД
                    val allCars = ownedCars + sharedCars
                    allCars.forEach { car ->
                        try { carRepository.insertCar(car) } catch (e: Exception) {
                            Log.w("HomeViewModel", "Failed to insert car ${car.id}: ${e.message}")
                        }
                    }
                    Log.d("HomeViewModel", "✅ Pulled ${ownedCars.size} owned + ${sharedCars.size} shared cars")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "syncCarsIfLocalEmpty failed", e)
            }
        }
    }

    fun checkPendingInvitations() {
        viewModelScope.launch {
            supabaseMembers.getPendingInvitationsForMe()
                .onSuccess { _pendingInvitations.value = it }
        }
    }

    fun dismissInvitation(token: String) {
        _pendingInvitations.value = _pendingInvitations.value.filter { it.token != token }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        carRepository.getAllActiveCars().flatMapLatest { cars ->
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
        },
        _pendingInvitations
    ) { baseState, invitations ->
        baseState.copy(pendingInvitations = invitations)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun deleteCar(car: Car) {
        viewModelScope.launch {
            // 1. Удаляем локально
            carRepository.deleteCar(car)
            Log.d("HomeViewModel", "Car deleted locally: ${car.id}")

            // 2. ✅ Синхронизируем удаление с Supabase
            try {
                val result = supabaseCarRepo.deleteCar(car.id)
                result.fold(
                    onSuccess = {
                        Log.d("HomeViewModel", "✅ Car deleted from Supabase: ${car.id}")
                    },
                    onFailure = { error ->
                        Log.e("HomeViewModel", "❌ Failed to delete car from Supabase: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ Exception deleting car from Supabase", e)
            }
        }
    }
}