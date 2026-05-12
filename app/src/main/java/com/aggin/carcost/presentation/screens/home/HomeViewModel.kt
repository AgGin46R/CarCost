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
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.repository.CarInvitationDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SmartHint(val message: String, val carId: String? = null)

data class HomeUiState(
    val cars: List<Car> = emptyList(),
    val remindersByCarId: Map<String, List<MaintenanceReminder>> = emptyMap(),
    val monthlyExpensePerCar: Map<String, Double> = emptyMap(),
    val unreadChatCountPerCar: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val pendingInvitations: List<CarInvitationDto> = emptyList(),
    val smartHints: List<SmartHint> = emptyList()
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
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)

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
                syncExpensesForAllCars()
                checkPendingInvitations()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun syncExpensesForAllCars() {
        try {
            val cars = carRepository.getAllActiveCars().first()
            if (cars.isEmpty()) return
            cars.forEach { car ->
                supabaseExpenseRepo.getExpensesByCarId(car.id).onSuccess { expenses ->
                    expenses.forEach { expense ->
                        try { database.expenseDao().insertExpense(expense) } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HomeViewModel", "Expense sync skipped: ${e.message}")
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

    private val _smartHints = MutableStateFlow<List<SmartHint>>(emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        carRepository.getAllActiveCars().flatMapLatest { cars ->
            if (cars.isEmpty()) {
                flowOf(HomeUiState(cars = emptyList(), isLoading = false))
            } else {
                // Trigger smart hints computation once when cars are loaded
                viewModelScope.launch { computeSmartHints(cars.map { it.id }) }

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
        _syncError,
        _smartHints
    ) { baseState, invitations, syncing, error, hints ->
        baseState.copy(
            pendingInvitations = invitations,
            isSyncing = syncing,
            syncError = error,
            smartHints = hints
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    // ── Smart Hints ──────────────────────────────────────────────────────────

    /** Call once after cars are loaded to compute contextual smart hints. */
    fun computeSmartHints(carIds: List<String>) {
        viewModelScope.launch {
            val hints = mutableListOf<SmartHint>()
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()

            for (carId in carIds) {
                val expenses = database.expenseDao().getExpensesByCarIdSync(carId)
                if (expenses.size < 5) continue

                // Hint 1: Most frequent refuel day of week
                val fuelExpenses = expenses.filter { it.category == com.aggin.carcost.data.local.database.entities.ExpenseCategory.FUEL }
                if (fuelExpenses.size >= 3) {
                    val dayFreq = fuelExpenses.groupBy { exp ->
                        cal.timeInMillis = exp.date
                        cal.get(java.util.Calendar.DAY_OF_WEEK)
                    }.maxByOrNull { it.value.size }
                    dayFreq?.let { (day, _) ->
                        val dayName = when (day) {
                            java.util.Calendar.MONDAY -> "понедельник"
                            java.util.Calendar.TUESDAY -> "вторник"
                            java.util.Calendar.WEDNESDAY -> "среда"
                            java.util.Calendar.THURSDAY -> "четверг"
                            java.util.Calendar.FRIDAY -> "пятницу"
                            java.util.Calendar.SATURDAY -> "субботу"
                            else -> "воскресенье"
                        }
                        hints.add(SmartHint("⛽ Обычно вы заправляетесь в $dayName — следующая заправка скоро?", carId))
                    }
                }

                // Hint 2: Overdue oil change
                val reminders = database.maintenanceReminderDao().getRemindersByCarIdSync(carId)
                val car = database.carDao().getCarById(carId)
                if (car != null) {
                    val overdueOil = reminders.firstOrNull {
                        it.type == com.aggin.carcost.data.local.database.entities.MaintenanceType.OIL_CHANGE &&
                        car.currentOdometer >= it.nextChangeOdometer
                    }
                    if (overdueOil != null) {
                        hints.add(SmartHint("🔧 Просрочена замена масла (${car.brand} ${car.model}) — пора в сервис!", carId))
                    }
                }

                // Hint 3: Monthly spend spike vs previous 2 months
                val monthlyTotals = expenses.groupBy { exp ->
                    cal.timeInMillis = exp.date
                    "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
                }.mapValues { it.value.sumOf { e -> e.amount } }.values.toList().sortedDescending()
                if (monthlyTotals.size >= 3) {
                    val currentMonth = monthlyTotals.first()
                    val avgPrev = monthlyTotals.drop(1).take(2).average()
                    if (avgPrev > 0 && currentMonth > avgPrev * 1.4) {
                        hints.add(SmartHint("📈 Расходы в этом месяце на ${((currentMonth / avgPrev - 1) * 100).toInt()}% выше обычного", carId))
                    }
                }
            }
            _smartHints.value = hints.take(3) // Show at most 3 hints
        }
    }

    fun dismissHint(hint: SmartHint) {
        _smartHints.value = _smartHints.value - hint
    }

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