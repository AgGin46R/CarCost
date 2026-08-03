package com.aggin.carcost.presentation.screens.car_detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.sync.OfflineQueue
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.CarMember
import com.aggin.carcost.data.local.database.entities.MemberRole
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.domain.health.CarHealthCalculator
import com.aggin.carcost.domain.health.CarHealthScore
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.aggin.carcost.domain.fuel.FuelConsumptionCalculator

data class ExpenseFilter(
    val categories: Set<ExpenseCategory> = emptySet(),
    val tags: Set<String> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    fun isActive(): Boolean {
        return categories.isNotEmpty() || tags.isNotEmpty() || startDate != null || endDate != null || minAmount != null || maxAmount != null
    }
}

data class CarDetailUiState(
    val car: Car? = null,
    val expenses: List<Expense> = emptyList(),
    val expensesWithTags: Map<String, List<ExpenseTag>> = emptyMap(),
    val totalExpenses: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val expenseCount: Int = 0,
    val currentFilter: ExpenseFilter = ExpenseFilter(),
    val availableTags: List<ExpenseTag> = emptyList(),
    val estimatedFuelLiters: Double? = null,
    val fuelLevelPct: Float? = null,
    val fuelConsumptionPerFill: Map<String, Double> = emptyMap(), // expenseId → L/100km
    val healthScore: CarHealthScore? = null,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isOwner: Boolean = true, // default true to avoid UI flicker while loading
    val userRole: MemberRole? = null,  // null = owner who hasn't added members yet
    val hasMoreExpenses: Boolean = false,  // есть ли ещё записи за пределами текущего лимита
    val totalExpenseCount: Int = 0         // полное кол-во после фильтрации
)

class CarDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: ""

    private val database = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(database.carDao())
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val tagDao = database.expenseTagDao()
    private val tagRepository = ExpenseTagRepository(tagDao)
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)
    private val supabaseMembers = SupabaseCarMembersRepository(supabaseAuth)

    private val _currentFilter = MutableStateFlow(ExpenseFilter())
    private val _expensesWithTags = MutableStateFlow<Map<String, List<ExpenseTag>>>(emptyMap())
    private val _availableTags = MutableStateFlow<List<ExpenseTag>>(emptyList())
    private val _isOwner = MutableStateFlow(true)
    private val _userRole = MutableStateFlow<MemberRole?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _displayLimit = MutableStateFlow(60)

    // Sources for health score — each emits lists from local DB
    private val _remindersFlow = database.maintenanceReminderDao().getAllRemindersByCarId(carId)
    private val _incidentsFlow = database.carIncidentDao().getIncidentsByCarId(carId)
    private val _policiesFlow = database.insurancePolicyDao().getPoliciesForCar(carId)

    // Pack reminders / incidents / policies into a single triple so the main
    // `combine` block doesn't exceed the 5-flow overload limit.
    private data class HealthInputs(
        val reminders: List<com.aggin.carcost.data.local.database.entities.MaintenanceReminder>,
        val incidents: List<com.aggin.carcost.data.local.database.entities.CarIncident>,
        val policies: List<com.aggin.carcost.data.local.database.entities.InsurancePolicy>
    )

    private val _healthInputsFlow: Flow<HealthInputs> = combine(
        _remindersFlow, _incidentsFlow, _policiesFlow
    ) { r, i, p -> HealthInputs(r, i, p) }

    // Pair up the tag flows so the main combine fits the 5-flow typed overload.
    private val _tagsBundle: Flow<Pair<Map<String, List<ExpenseTag>>, List<ExpenseTag>>> =
        combine(_expensesWithTags, _availableTags) { m, l -> m to l }

    private val _baseState = combine(
        carRepository.getCarByIdFlow(carId),
        expenseRepository.getExpensesByCarId(carId),
        _currentFilter,
        _tagsBundle,
        _healthInputsFlow
    ) { car, expenses, filter, tagsPair, health ->
        val (expensesWithTagsMap, availableTags) = tagsPair
        val filteredExpenses = applyFilters(expenses, filter, expensesWithTagsMap)
        val (estimatedFuel, fuelPct) = estimateFuelLevel(car, expenses)
        val healthScore = car?.let {
            CarHealthCalculator.calculate(
                currentOdometer = it.currentOdometer,
                reminders = health.reminders,
                incidents = health.incidents,
                policies = health.policies
            )
        }
        CarDetailUiState(
            car = car,
            expenses = filteredExpenses,
            expensesWithTags = expensesWithTagsMap,
            totalExpenses = filteredExpenses.sumOf { it.amount },
            monthlyExpenses = calculateMonthlyExpenses(filteredExpenses),
            expenseCount = filteredExpenses.size,
            currentFilter = filter,
            availableTags = availableTags,
            estimatedFuelLiters = estimatedFuel,
            fuelLevelPct = fuelPct,
            fuelConsumptionPerFill = calculateFuelConsumptionPerFill(expenses),
            healthScore = healthScore,
            isLoading = false
        )
    }

    val uiState: StateFlow<CarDetailUiState> = combine(_baseState, _isOwner, _userRole, _isSyncing, _displayLimit) { state, isOwner, role, syncing, displayLimit ->
        val displayed = state.expenses.take(displayLimit)
        state.copy(
            isOwner = isOwner,
            userRole = role,
            isSyncing = syncing,
            expenses = displayed,
            hasMoreExpenses = state.expenses.size > displayLimit,
            totalExpenseCount = state.expenses.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CarDetailUiState(isLoading = true)
    )

    init {
        syncData()
        loadTags()
        loadAvailableTags()
        loadOwnerRole()
    }

    private fun loadOwnerRole() {
        viewModelScope.launch {
            val userId = supabaseAuth.getUserId() ?: return@launch

            // 1. Сначала смотрим в локальной БД (быстро)
            var role = database.carMemberDao().getRoleForUser(carId, userId)

            // 2. Если записи нет локально — проверяем Supabase (авторитетный источник)
            //    Это защита от ситуации когда car_members не синхронизированы локально
            if (role == null) {
                supabaseMembers.getMyRoleForCar(carId)
                    .onSuccess { remoteRole ->
                        if (remoteRole != null) {
                            // Сохраняем в локальную БД чтобы следующий запрос был быстрым
                            val email = supabaseAuth.getCurrentUserEmail() ?: ""
                            database.carMemberDao().insert(
                                CarMember(
                                    carId = carId,
                                    userId = userId,
                                    email = email,
                                    role = remoteRole
                                )
                            )
                            role = remoteRole
                        }
                        // Если remoteRole == null → пользователь не в car_members → он владелец
                    }
                    .onFailure { e ->
                        Log.w("CarDetail", "Could not verify role from Supabase: ${e.message}")
                        // Оставляем role = null (трактуем как владелец при ошибке сети)
                    }
            }

            _userRole.value = role
            _isOwner.value = role == null || role == MemberRole.OWNER
        }
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            val userId = supabaseAuth.getUserId()
            if (userId != null) {
                tagRepository.getTagsByUser(userId).collect { tags ->
                    _availableTags.value = tags
                }
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            // Загружаем теги для всех расходов
            expenseRepository.getExpensesByCarId(carId).collect { expenses ->
                // Один запрос на все расходы вместо запроса на каждый.
                // Было: 300 расходов — 300 обращений к базе, каждое через Flow
                // (постановка и снятие наблюдателя), и весь цикл повторялся
                // заново на каждую вставку во время синхронизации.
                _expensesWithTags.value = try {
                    if (expenses.isEmpty()) {
                        emptyMap()
                    } else {
                        tagDao.getTagsForExpenses(expenses.map { it.id })
                            .groupBy { it.expenseId }
                            .mapValues { (_, rows) ->
                                rows.map { row ->
                                    ExpenseTag(
                                        id = row.tagId,
                                        name = row.name,
                                        color = row.color,
                                        userId = row.userId
                                    )
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.e("CarDetail", "Error loading tags", e)
                    emptyMap()
                }
            }
        }
    }

    private fun syncData() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // 1. Синхронизация расходов
                val result = supabaseExpenseRepo.getExpensesByCarId(carId)
                result.fold(
                    onSuccess = { expenses ->
                        // Одной транзакцией. Раньше запись шла по одной, и Room
                        // объявлял таблицу изменённой после каждой — экран
                        // пересобирался столько раз, сколько приехало записей.
                        // Внутри транзакции оповещение откладывается до коммита.
                        database.withTransaction {
                            expenses.forEach { expense ->
                                // Use @Update for existing expenses to avoid triggering ON DELETE CASCADE
                                // on expense_tag_cross_ref (which @Insert(REPLACE) would cause via DELETE+INSERT).
                                // Only insert (REPLACE) when the expense doesn't exist locally yet.
                                val local = expenseRepository.getExpenseById(expense.id)
                                when {
                                    local == null -> expenseRepository.insertExpense(expense)
                                    // saveFromServer, а не updateExpense: обычный
                                    // update штампует updatedAt = сейчас, и запись
                                    // тут же уезжала бы обратно на сервер
                                    expense.updatedAt > local.updatedAt -> expenseRepository.saveFromServer(expense)
                                    // Equal or local is newer — skip, no-op
                                }
                            }
                        }
                        Log.d("CarDetail", "Synced ${expenses.size} expenses")
                        // Tag cross-refs are synced globally by SyncRepository.syncTagLinks()
                        // (batch call via getAllCrossRefs). No per-expense N+1 sync here.
                    },
                    onFailure = { error ->
                        Log.e("CarDetail", "Sync failed", error)
                    }
                )

                // 3. Синхронизация напоминаний
                val reminderResult = supabaseReminderRepo.getRemindersByCarId(carId)
                reminderResult.fold(
                    onSuccess = { reminders ->
                        reminders.forEach { reminder ->
                            reminderRepository.insertReminder(reminder)
                        }
                    },
                    onFailure = { }
                )
            } catch (e: Exception) {
                Log.e("CarDetail", "Sync exception", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Удаление расхода — локально и на сервере.
     *
     * Результат серверного удаления раньше просто отбрасывался, а внутри
     * репозитория всё обёрнуто в try/catch — то есть внешний catch не срабатывал
     * никогда. При отказе (нет сети, отклонение по правам) запись исчезала
     * локально, но оставалась на сервере, и следующая синхронизация возвращала
     * её обратно: человек удалял ошибочную заправку в метро, а через час она
     * появлялась снова.
     *
     * Теперь неудача кладётся в очередь отложенных записей. Очередь и её воркер
     * с повторными попытками в проекте были написаны, но не вызывались ниоткуда —
     * `enqueue` встречался только в примере в комментарии.
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expense)

                supabaseExpenseRepo.deleteExpense(expense.id)
                    .onSuccess { Log.d("CarDetail", "Expense deleted: ${expense.id}") }
                    .onFailure { error ->
                        Log.w("CarDetail", "Удаление на сервере не прошло — в очередь", error)
                        OfflineQueue.enqueue(
                            context = getApplication(),
                            tableName = "expenses",
                            operation = "DELETE",
                            payload = """{"id":"${expense.id}"}"""
                        )
                    }
            } catch (e: Exception) {
                Log.e("CarDetail", "Delete failed", e)
            }
        }
    }

    fun applyFilter(filter: ExpenseFilter) {
        _displayLimit.value = 60  // сброс при новом фильтре
        _currentFilter.value = filter
    }

    fun clearFilter() {
        _displayLimit.value = 60
        _currentFilter.value = ExpenseFilter()
    }

    fun loadMoreExpenses() {
        _displayLimit.update { it + 30 }
    }

    private fun applyFilters(
        expenses: List<Expense>,
        filter: ExpenseFilter,
        tagsMap: Map<String, List<ExpenseTag>>
    ): List<Expense> {
        var filtered = expenses

        if (filter.categories.isNotEmpty()) {
            filtered = filtered.filter { it.category in filter.categories }
        }

        if (filter.tags.isNotEmpty()) {
            filtered = filtered.filter { expense ->
                // Проверяем есть ли хоть один из выбранных тегов у расхода
                val expenseTags = tagsMap[expense.id] ?: emptyList()
                expenseTags.any { it.id in filter.tags }
            }
        }

        filter.startDate?.let { start ->
            filtered = filtered.filter { it.date >= start }
        }

        filter.endDate?.let { end ->
            filtered = filtered.filter { it.date <= end }
        }

        filter.minAmount?.let { min ->
            filtered = filtered.filter { it.amount >= min }
        }

        filter.maxAmount?.let { max ->
            filtered = filtered.filter { it.amount <= max }
        }

        return filtered.sortedByDescending { it.date }
    }

    private fun calculateMonthlyExpenses(expenses: List<Expense>): Double {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        return expenses
            .filter { it.date >= thirtyDaysAgo }
            .sumOf { it.amount }
    }

    private fun estimateFuelLevel(car: Car?, expenses: List<Expense>): Pair<Double?, Float?> {
        val tankCapacity = car?.tankCapacity ?: return null to null
        val fuelExpenses = expenses
            .filter { it.category == ExpenseCategory.FUEL }
            .sortedByDescending { it.date }
        if (fuelExpenses.isEmpty()) return null to null

        val lastFull = fuelExpenses.firstOrNull { it.isFullTank } ?: return null to null
        val litersAtLastFull = lastFull.fuelLiters ?: return null to null

        val partialAfter = fuelExpenses
            .filter { it.date > lastFull.date && !it.isFullTank }
            .sumOf { it.fuelLiters ?: 0.0 }

        val kmSinceFull = (car.currentOdometer - lastFull.odometer).coerceAtLeast(0)
        val avgConsumption = calcAvgConsumption(fuelExpenses) ?: 10.0

        val consumed = kmSinceFull * avgConsumption / 100.0
        val remaining = (tankCapacity + partialAfter - consumed).coerceIn(0.0, tankCapacity)
        val pct = (remaining / tankCapacity).toFloat()

        return remaining to pct
    }

    // Расчёт переехал в domain/fuel: тот же метод «от полного до полного» теперь
    // используется и на экране аналитики, где раньше была другая, неверная формула
    private fun calcAvgConsumption(fuelExpenses: List<Expense>): Double? =
        FuelConsumptionCalculator.average(fuelExpenses)

    private fun calculateFuelConsumptionPerFill(expenses: List<Expense>): Map<String, Double> {
        val fullTanks = expenses
            .filter { it.category == ExpenseCategory.FUEL && it.isFullTank && it.fuelLiters != null && it.odometer > 0 }
            .sortedBy { it.date }
        if (fullTanks.size < 2) return emptyMap()
        val result = mutableMapOf<String, Double>()
        for (i in 1 until fullTanks.size) {
            val km = fullTanks[i].odometer - fullTanks[i - 1].odometer
            val liters = fullTanks[i].fuelLiters!!
            if (km > 0) {
                val consumption = liters / km * 100.0
                if (consumption in 3.0..40.0) {
                    result[fullTanks[i].id] = consumption
                }
            }
        }
        return result
    }
}