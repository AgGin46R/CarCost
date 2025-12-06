package com.aggin.carcost.presentation.screens.planned_expenses

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.*
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditPlannedExpenseUiState(
    val plannedExpense: PlannedExpense? = null,

    val title: String = "",
    val description: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val estimatedAmount: String = "",
    val actualAmount: String = "",
    val priority: PlannedExpensePriority = PlannedExpensePriority.MEDIUM,
    val status: PlannedExpenseStatus = PlannedExpenseStatus.PLANNED,
    val targetDate: Long? = null,
    val targetOdometer: String = "",
    val notes: String = "",
    val shopUrl: String = "",

    val titleError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)

class EditPlannedExpenseViewModel(
    application: Application,
    private val carId: String,
    private val plannedId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val localRepo = PlannedExpenseRepository(database.plannedExpenseDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseRepo = SupabasePlannedExpenseRepository(supabaseAuth)

    private val _uiState = MutableStateFlow(EditPlannedExpenseUiState())
    val uiState: StateFlow<EditPlannedExpenseUiState> = _uiState.asStateFlow()

    init {
        loadPlannedExpense()
    }

    private fun loadPlannedExpense() {
        viewModelScope.launch {
            try {
                localRepo.getPlannedExpenseByIdFlow(plannedId).collect { plannedExpense ->
                    if (plannedExpense != null) {
                        _uiState.value = EditPlannedExpenseUiState(
                            plannedExpense = plannedExpense,
                            title = plannedExpense.title,
                            description = plannedExpense.description ?: "",
                            category = plannedExpense.category,
                            estimatedAmount = plannedExpense.estimatedAmount?.toString() ?: "",
                            actualAmount = plannedExpense.actualAmount?.toString() ?: "",
                            priority = plannedExpense.priority,
                            status = plannedExpense.status,
                            targetDate = plannedExpense.targetDate,
                            targetOdometer = plannedExpense.targetOdometer?.toString() ?: "",
                            notes = plannedExpense.notes ?: "",
                            shopUrl = plannedExpense.shopUrl ?: "",
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "План не найден"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("EditPlannedExpense", "Error loading", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ошибка загрузки: ${e.message}"
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(
            title = title,
            titleError = null
        )
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun updateEstimatedAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(estimatedAmount = filtered)
    }

    fun updateActualAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(actualAmount = filtered)
    }

    fun updatePriority(priority: PlannedExpensePriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun updateStatus(status: PlannedExpenseStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun updateTargetDate(date: Long?) {
        _uiState.value = _uiState.value.copy(targetDate = date)
    }

    fun updateTargetOdometer(odometer: String) {
        val filtered = odometer.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(targetOdometer = filtered)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun updateShopUrl(url: String) {
        _uiState.value = _uiState.value.copy(shopUrl = url)
    }

    fun savePlannedExpense() {
        val state = _uiState.value

        // Валидация
        if (state.title.isBlank()) {
            _uiState.value = state.copy(titleError = "Введите название")
            return
        }

        val plannedExpense = state.plannedExpense ?: return

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val updatedPlannedExpense = plannedExpense.copy(
                    title = state.title.trim(),
                    description = state.description.trim().takeIf { it.isNotBlank() },
                    category = state.category,
                    estimatedAmount = state.estimatedAmount.toDoubleOrNull(),
                    actualAmount = state.actualAmount.toDoubleOrNull(),
                    priority = state.priority,
                    status = state.status,
                    targetDate = state.targetDate,
                    targetOdometer = state.targetOdometer.toIntOrNull(),
                    notes = state.notes.trim().takeIf { it.isNotBlank() },
                    shopUrl = state.shopUrl.trim().takeIf { it.isNotBlank() },
                    completedDate = if (state.status == PlannedExpenseStatus.COMPLETED && plannedExpense.completedDate == null) {
                        System.currentTimeMillis()
                    } else {
                        plannedExpense.completedDate
                    },
                    isSynced = false // Сбрасываем флаг синхронизации
                )

                // 1. Сохраняем локально
                localRepo.updatePlannedExpense(updatedPlannedExpense)
                Log.d("EditPlannedExpense", "✅ Updated locally")

                // 2. Синхронизируем с Supabase
                val result = supabaseRepo.updatePlannedExpense(updatedPlannedExpense)
                result.fold(
                    onSuccess = {
                        Log.d("EditPlannedExpense", "✅ Synced to Supabase")
                        // Обновляем флаг синхронизации
                        localRepo.updateSyncStatus(updatedPlannedExpense.id, true)
                    },
                    onFailure = { error ->
                        Log.e("EditPlannedExpense", "❌ Supabase sync failed", error)
                        // Оставляем локально как несинхронизированный
                    }
                )

                _uiState.value = state.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                Log.e("EditPlannedExpense", "Error saving", e)
                _uiState.value = state.copy(
                    isSaving = false,
                    errorMessage = "Ошибка сохранения: ${e.message}"
                )
            }
        }
    }

    fun deletePlannedExpense() {
        val plannedExpense = _uiState.value.plannedExpense ?: return

        viewModelScope.launch {
            try {
                // 1. Удаляем локально
                localRepo.deletePlannedExpense(plannedExpense)
                Log.d("EditPlannedExpense", "✅ Deleted locally")

                // 2. Удаляем из Supabase
                val result = supabaseRepo.deletePlannedExpense(plannedExpense.id)
                result.fold(
                    onSuccess = {
                        Log.d("EditPlannedExpense", "✅ Deleted from Supabase")
                    },
                    onFailure = { error ->
                        Log.e("EditPlannedExpense", "❌ Supabase delete failed", error)
                        // Локально уже удалено, ничего не делаем
                    }
                )

                _uiState.value = _uiState.value.copy(isDeleted = true)
            } catch (e: Exception) {
                Log.e("EditPlannedExpense", "Error deleting", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка удаления: ${e.message}"
                )
            }
        }
    }
}

// ✅ ViewModelFactory
class EditPlannedExpenseViewModelFactory(
    private val application: Application,
    private val carId: String,
    private val plannedId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditPlannedExpenseViewModel::class.java)) {
            return EditPlannedExpenseViewModel(application, carId, plannedId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}