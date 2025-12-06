package com.aggin.carcost.presentation.screens.edit_expense

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditExpenseUiState(
    val expenseId: String = "",
    val carId: String = "",
    val category: ExpenseCategory = ExpenseCategory.FUEL,
    val amount: String = "",
    val odometer: String = "",
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val location: String = "",

    // Для топлива
    val fuelLiters: String = "",
    val isFullTank: Boolean = false,

    // Для обслуживания и ремонта
    val serviceType: ServiceType? = null,
    val workshopName: String = "",

    // Сохраняем старый serviceType для отслеживания изменений
    val originalServiceType: ServiceType? = null,

    // Геолокация (сохраняем старые значения)
    val latitude: Double? = null,
    val longitude: Double? = null,

    // ✅ ДОБАВЛЕНО: Теги
    val availableTags: List<ExpenseTag> = emptyList(),
    val selectedTags: List<ExpenseTag> = emptyList(),

    val amountError: String? = null,
    val odometerError: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

class EditExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val expenseRepository = ExpenseRepository(database.expenseDao())
    private val reminderRepository = MaintenanceReminderRepository(database.maintenanceReminderDao())
    private val tagRepository = ExpenseTagRepository(database.expenseTagDao())
    private val tagDao = database.expenseTagDao()  // ✅ ДОБАВЛЕНО

    // ✅ ДОБАВЛЕНО: Supabase репозитории
    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseExpenseRepo = SupabaseExpenseRepository(supabaseAuth)
    private val supabaseReminderRepo = SupabaseMaintenanceReminderRepository(supabaseAuth)
    private val supabaseTagRepo = SupabaseExpenseTagRepository(supabaseAuth)  // ✅ ДОБАВЛЕНО

    private val _uiState = MutableStateFlow(EditExpenseUiState())
    val uiState: StateFlow<EditExpenseUiState> = _uiState.asStateFlow()

    fun loadExpense(carId: String, expenseId: String) { // ✅ String UUID
        viewModelScope.launch {
            try {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense != null) {
                    _uiState.value = EditExpenseUiState(
                        expenseId = expense.id,
                        carId = expense.carId,
                        category = expense.category,
                        amount = expense.amount.toString(),
                        odometer = expense.odometer.toString(),
                        date = expense.date,
                        description = expense.description ?: "",
                        location = expense.location ?: "",
                        fuelLiters = expense.fuelLiters?.toString() ?: "",
                        isFullTank = expense.isFullTank,
                        serviceType = expense.serviceType,
                        originalServiceType = expense.serviceType, // ✅ Сохраняем оригинальный тип
                        workshopName = expense.workshopName ?: "",
                        latitude = expense.latitude,
                        longitude = expense.longitude,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Расход не найден",
                        isLoading = false
                    )
                }

                // ✅ ДОБАВЛЕНО: Загружаем доступные теги
                val userId = supabaseAuth.getUserId()
                if (userId != null) {
                    tagRepository.getTagsByUser(userId).collect { tags ->
                        _uiState.value = _uiState.value.copy(availableTags = tags)
                    }
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка загрузки: ${e.message}",
                    isLoading = false
                )
            }
        }

        // ✅ ДОБАВЛЕНО: Загружаем теги этого расхода
        viewModelScope.launch {
            try {
                tagDao.getTagsForExpense(expenseId).collect { tags ->
                    _uiState.value = _uiState.value.copy(selectedTags = tags)
                    Log.d("EditExpense", "✅ Loaded ${tags.size} tags for expense")
                }
            } catch (e: Exception) {
                Log.e("EditExpense", "Error loading tags", e)
            }
        }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.value = _uiState.value.copy(category = category, errorMessage = null)
    }

    fun updateAmount(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(
                amount = amount,
                amountError = null,
                errorMessage = null
            )
        }
    }

    fun updateOdometer(odometer: String) {
        if (odometer.isEmpty() || odometer.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(
                odometer = odometer,
                odometerError = null,
                errorMessage = null
            )
        }
    }

    fun updateDate(date: Long) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(location = location)
    }

    fun updateFuelLiters(fuelLiters: String) {
        if (fuelLiters.isEmpty() || fuelLiters.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.value = _uiState.value.copy(fuelLiters = fuelLiters)
        }
    }

    fun updateIsFullTank(isFullTank: Boolean) {
        _uiState.value = _uiState.value.copy(isFullTank = isFullTank)
    }

    fun updateServiceType(serviceType: ServiceType?) {
        _uiState.value = _uiState.value.copy(serviceType = serviceType)
    }

    fun updateWorkshopName(workshopName: String) {
        _uiState.value = _uiState.value.copy(workshopName = workshopName)
    }

    // ✅ ДОБАВЛЕНО: Методы для работы с тегами
    fun addTag(tag: ExpenseTag) {
        val currentTags = _uiState.value.selectedTags
        if (!currentTags.contains(tag)) {
            _uiState.value = _uiState.value.copy(selectedTags = currentTags + tag)
            Log.d("EditExpense", "✅ Added tag: ${tag.name}")
        }
    }

    fun removeTag(tag: ExpenseTag) {
        _uiState.value = _uiState.value.copy(
            selectedTags = _uiState.value.selectedTags.filter { it.id != tag.id }
        )
        Log.d("EditExpense", "✅ Removed tag: ${tag.name}")
    }

    fun saveExpense(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Валидация
        val amount = state.amount.toDoubleOrNull()
        val odometer = state.odometer.toIntOrNull()

        var hasError = false

        if (amount == null || amount <= 0) {
            _uiState.value = state.copy(
                amountError = "Введите корректную сумму",
                errorMessage = "Проверьте введенные данные"
            )
            hasError = true
        }

        if (odometer == null || odometer < 0) {
            _uiState.value = _uiState.value.copy(
                odometerError = "Введите корректный пробег",
                errorMessage = "Проверьте введенные данные"
            )
            hasError = true
        }

        if (hasError) return

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val updatedExpense = Expense(
                    id = state.expenseId,
                    carId = state.carId,
                    category = state.category,
                    amount = amount!!,
                    currency = "RUB",
                    date = state.date,
                    odometer = odometer!!,
                    description = state.description.ifBlank { null },
                    location = state.location.ifBlank { null },
                    latitude = state.latitude,
                    longitude = state.longitude,
                    fuelLiters = if (state.category == ExpenseCategory.FUEL) {
                        state.fuelLiters.toDoubleOrNull()
                    } else null,
                    isFullTank = state.category == ExpenseCategory.FUEL && state.isFullTank,
                    serviceType = if (state.category == ExpenseCategory.MAINTENANCE) {
                        state.serviceType
                    } else null,
                    workshopName = if (state.category == ExpenseCategory.MAINTENANCE ||
                        state.category == ExpenseCategory.REPAIR) {
                        state.workshopName.ifBlank { null }
                    } else null
                )

                // 1. Обновляем локально
                expenseRepository.updateExpense(updatedExpense)
                Log.d("EditExpense", "✅ Expense updated locally")

                // ✅ 2. ДОБАВЛЕНО: Обновляем теги
                try {
                    // Удаляем старые связи
                    tagDao.deleteExpenseTagsByExpenseId(state.expenseId)
                    Log.d("EditExpense", "✅ Deleted old tag links")

                    // Сохраняем новые связи локально
                    state.selectedTags.forEach { tag ->
                        tagDao.insertExpenseTagCrossRef(
                            ExpenseTagCrossRef(
                                expenseId = state.expenseId,
                                tagId = tag.id
                            )
                        )
                    }
                    Log.d("EditExpense", "✅ Tags updated locally: ${state.selectedTags.size} tags")

                    // Синхронизируем с Supabase
                    if (state.selectedTags.isNotEmpty()) {
                        // Сначала убедимся что теги существуют в Supabase
                        for (tag in state.selectedTags) {
                            val tagResult = supabaseTagRepo.insertTag(tag)
                            tagResult.fold(
                                onSuccess = {
                                    Log.d("EditExpense", "✅ Tag ensured in Supabase: ${tag.name}")
                                },
                                onFailure = { error ->
                                    // Тег уже существует - это нормально
                                    Log.d("EditExpense", "Tag already exists: ${error.message}")
                                }
                            )
                        }

                        // Синхронизируем связи
                        val tagIds = state.selectedTags.map { it.id }
                        val result = supabaseTagRepo.setTagsForExpense(state.expenseId, tagIds)

                        result.fold(
                            onSuccess = {
                                Log.d("EditExpense", "✅ Tag links synced to Supabase: ${tagIds.size} tags")
                            },
                            onFailure = { error ->
                                Log.e("EditExpense", "❌ Failed to sync tag links: ${error.message}")
                            }
                        )
                    } else {
                        // Если тегов нет - удаляем все связи из Supabase
                        supabaseTagRepo.removeAllTagsFromExpense(state.expenseId)
                        Log.d("EditExpense", "✅ Removed all tag links from Supabase")
                    }
                } catch (e: Exception) {
                    Log.e("EditExpense", "❌ Exception updating tags", e)
                    // Не критично - продолжаем
                }

                // 3. НОВЫЙ КОД: Обновляем напоминание при изменении типа ТО
                if (state.category == ExpenseCategory.MAINTENANCE) {
                    Log.d("EditExpense", "Checking if reminder needs update...")
                    Log.d("EditExpense", "Original: ${state.originalServiceType}, New: ${state.serviceType}")

                    if (state.originalServiceType != state.serviceType) {
                        Log.d("EditExpense", "ServiceType CHANGED - updating reminder")

                        // Локально
                        reminderRepository.updateAfterExpenseEdit(
                            carId = state.carId,
                            oldServiceType = state.originalServiceType,
                            newServiceType = state.serviceType,
                            newOdometer = odometer
                        )
                        Log.d("EditExpense", "✅ Reminder updated locally")

                        // С Supabase
                        try {
                            // Удаляем старое напоминание
                            if (state.originalServiceType != null) {
                                val oldMaintenanceType = reminderRepository.serviceTypeToMaintenanceType(state.originalServiceType)
                                oldMaintenanceType?.let { type ->
                                    val oldReminderResult = supabaseReminderRepo.getReminderByType(state.carId, type)
                                    oldReminderResult.getOrNull()?.let { reminder ->
                                        supabaseReminderRepo.deleteReminder(reminder.id)
                                        Log.d("EditExpense", "✅ Old reminder deleted from Supabase")
                                    }
                                }
                            }

                            // Создаем новое напоминание
                            if (state.serviceType != null) {
                                val newMaintenanceType = reminderRepository.serviceTypeToMaintenanceType(state.serviceType)
                                newMaintenanceType?.let { type ->
                                    val newReminder = reminderRepository.getReminderByType(state.carId, type)
                                    if (newReminder != null) {
                                        supabaseReminderRepo.insertReminder(newReminder)
                                        Log.d("EditExpense", "✅ New reminder created on Supabase")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EditExpense", "❌ Failed to sync reminder to Supabase", e)
                            // Не критично - продолжаем
                        }
                    } else if (state.serviceType != null) {
                        // Тип не изменился - просто обновляем пробег
                        Log.d("EditExpense", "ServiceType UNCHANGED - updating odometer only")

                        val maintenanceType = reminderRepository.serviceTypeToMaintenanceType(state.serviceType)
                        maintenanceType?.let { type ->
                            // Локально
                            reminderRepository.updateAfterMaintenance(state.carId, type, odometer)

                            // С Supabase
                            try {
                                val reminder = reminderRepository.getReminderByType(state.carId, type)
                                if (reminder != null) {
                                    supabaseReminderRepo.updateReminder(reminder)
                                    Log.d("EditExpense", "✅ Reminder updated on Supabase")
                                }
                            } catch (e: Exception) {
                                Log.e("EditExpense", "❌ Failed to update reminder on Supabase", e)
                            }
                        }
                    }
                }

                // 4. Синхронизируем расход с Supabase
                try {
                    val result = supabaseExpenseRepo.updateExpense(updatedExpense)
                    result.fold(
                        onSuccess = {
                            Log.d("EditExpense", "✅ Expense synced to Supabase")
                        },
                        onFailure = { error ->
                            Log.e("EditExpense", "❌ Failed to sync expense to Supabase: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("EditExpense", "❌ Exception syncing expense to Supabase", e)
                }

                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()

            } catch (e: Exception) {
                Log.e("EditExpense", "❌ Error saving expense", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка сохранения: ${e.message}",
                    isSaving = false
                )
            }
        }
    }
}
