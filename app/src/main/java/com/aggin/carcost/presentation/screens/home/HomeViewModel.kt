package com.aggin.carcost.presentation.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.repository.CarInvitationDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val cars: List<Car> = emptyList(),
    val remindersByCarId: Map<String, List<MaintenanceReminder>> = emptyMap(),
    val monthlyExpensePerCar: Map<String, Double> = emptyMap(),
    val unreadChatCountPerCar: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val pendingInvitations: List<CarInvitationDto> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val settingsManager = SettingsManager(application)

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseCarRepo = SupabaseCarRepository(supabaseAuth)
    private val supabaseMembers = SupabaseCarMembersRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)

    private val _pendingInvitations = MutableStateFlow<List<CarInvitationDto>>(emptyList())
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)

    init {
        checkPendingInvitations()
        syncRemindersForAllCars()
        syncCarsIfLocalEmpty()
    }

    /**
     * Один батч-запрос для всех машин вместо N+1.
     */
    private fun syncRemindersForAllCars() {
        viewModelScope.launch {
            try {
                val cars = carRepository.getAllActiveCars().first()
                if (cars.isEmpty()) return@launch
                val carIds = cars.map { it.id }
                supabaseReminderRepo.getRemindersByCarIds(carIds)
                    .onSuccess { reminders ->
                        reminders.forEach { reminder ->
                            try {
                                database.maintenanceReminderDao().insertReminder(reminder)
                            } catch (_: Exception) {}
                        }
                    }
            } catch (e: Exception) {
                Log.d("HomeViewModel", "Reminder sync skipped: ${e.message}")
            }
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            try {
                syncCarsFromSupabase()
                syncRemindersForAllCars()
                checkPendingInvitations()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun syncCarsFromSupabase() {
        if (!supabaseAuth.isUserLoggedIn()) return
        try {
            // 1. Собственные машины
            val ownedCars = mutableListOf<com.aggin.carcost.data.local.database.entities.Car>()
            supabaseCarRepo.getAllCars()
                .onSuccess { ownedCars.addAll(it) }
                .onFailure { Log.e("HomeViewModel", "Failed to pull owned cars", it) }

            // 2. Shared машины из car_members
            val sharedCarIds = mutableListOf<String>()
            supabaseMembers.getMyMemberCarIds()
                .onSuccess { ids ->
                    sharedCarIds.addAll(ids.filter { id -> ownedCars.none { it.id == id } })
                }
                .onFailure { Log.e("HomeViewModel", "Failed to get member car ids", it) }

            val sharedCars = mutableListOf<com.aggin.carcost.data.local.database.entities.Car>()
            sharedCarIds.forEach { carId ->
                supabaseCarRepo.fetchSharedCar(carId)
                    .onSuccess { sharedCars.add(it) }
                    .onFailure { Log.w("HomeViewModel", "Could not fetch shared car $carId") }
            }

            // 3. Вставляем машины в локальную БД
            val allCars = ownedCars + sharedCars
            allCars.forEach { car ->
                try { carRepository.insertCar(car) } catch (e: Exception) {
                    Log.w("HomeViewModel", "Failed to insert car ${car.id}: ${e.message}")
                }
            }

            // 4. Синхронизируем членства текущего пользователя — критично для проверки ролей
            supabaseMembers.getMyMemberships()
                .onSuccess { memberships ->
                    memberships.forEach { member ->
                        try { database.carMemberDao().insert(member) } catch (e: Exception) {
                            Log.w("HomeViewModel", "Failed to insert membership ${member.id}: ${e.message}")
                        }
                    }
                    Log.d("HomeViewModel", "✅ Synced ${memberships.size} memberships")
                }
                .onFailure { Log.w("HomeViewModel", "Failed to sync memberships: ${it.message}") }

            Log.d("HomeViewModel", "✅ Pulled ${ownedCars.size} owned + ${sharedCars.size} shared cars")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "syncCarsFromSupabase failed", e)
            _syncError.value = e.message ?: "Ошибка синхронизации"
        }
    }

    private fun syncCarsIfLocalEmpty() {
        viewModelScope.launch {
            try {
                val localCars = carRepository.getAllActiveCars().first()
                if (localCars.isEmpty()) {
                    _isSyncing.value = true
                    _syncError.value = null
                    try {
                        syncCarsFromSupabase()
                    } finally {
                        _isSyncing.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "syncCarsIfLocalEmpty failed", e)
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncError() {
        _syncError.value = null
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
                    combine(
                        reminderRepository.getActiveReminders(car.id),
                        expenseRepository.getMonthlyExpenses(car.id),
                        settingsManager.lastChatSeenFlow(car.id).flatMapLatest { lastSeen ->
                            database.chatMessageDao().getUnreadCount(car.id, lastSeen)
                        }
                    ) { reminders, monthly, unread ->
                        listOf(car.id, reminders, monthly, unread)
                    }
                }) { rows ->
                    @Suppress("UNCHECKED_CAST")
                    HomeUiState(
                        cars = cars,
                        remindersByCarId = rows.associate { it[0] as String to it[1] as List<MaintenanceReminder> },
                        monthlyExpensePerCar = rows.associate { it[0] as String to it[2] as Double },
                        unreadChatCountPerCar = rows.associate { it[0] as String to it[3] as Int },
                        isLoading = false
                    )
                }
            }
        },
        _pendingInvitations,
        _isSyncing,
        _syncError
    ) { baseState, invitations, syncing, error ->
        baseState.copy(
            pendingInvitations = invitations,
            isSyncing = syncing,
            syncError = error
        )
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