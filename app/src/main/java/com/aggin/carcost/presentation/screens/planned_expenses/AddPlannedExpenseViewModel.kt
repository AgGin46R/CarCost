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

data class AddPlannedExpenseUiState(
    val title: String = "",
    val description: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val estimatedAmount: String = "",
    val priority: PlannedExpensePriority = PlannedExpensePriority.MEDIUM,
    val targetDate: Long? = null,
    val targetOdometer: String = "",
    val notes: String = "",
    val shopUrl: String = "",

    val titleError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class AddPlannedExpenseViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val localRepo = PlannedExpenseRepository(database.plannedExpenseDao())

    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseRepo = SupabasePlannedExpenseRepository(supabaseAuth)

    private val _uiState = MutableStateFlow(AddPlannedExpenseUiState())
    val uiState: StateFlow<AddPlannedExpenseUiState> = _uiState.asStateFlow()

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

    fun updatePriority(priority: PlannedExpensePriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
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

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val userId = supabaseAuth.getUserId()
                    ?: throw Exception("Пользователь не аутентифицирован")

                val plannedExpense = PlannedExpense(
                    carId = carId,
                    userId = userId,
                    title = state.title.trim(),
                    description = state.description.trim().takeIf { it.isNotBlank() },
                    category = state.category,
                    estimatedAmount = state.estimatedAmount.toDoubleOrNull(),
                    priority = state.priority,
                    targetDate = state.targetDate,
                    targetOdometer = state.targetOdometer.toIntOrNull(),
                    notes = state.notes.trim().takeIf { it.isNotBlank() },
                    shopUrl = state.shopUrl.trim().takeIf { it.isNotBlank() },
                    status = PlannedExpenseStatus.PLANNED
                )

                // 1. Сохраняем локально
                localRepo.insertPlannedExpense(plannedExpense)
                Log.d("AddPlannedExpense", "✅ Saved locally: ${plannedExpense.id}")

                // 2. Синхронизируем с Supabase
                val result = supabaseRepo.insertPlannedExpense(plannedExpense)
                result.fold(
                    onSuccess = {
                        Log.d("AddPlannedExpense", "✅ Synced to Supabase")
                        // Обновляем флаг синхронизации
                        localRepo.updateSyncStatus(plannedExpense.id, true)
                    },
                    onFailure = { error ->
                        Log.e("AddPlannedExpense", "❌ Supabase sync failed", error)
                        // Оставляем локально как несинхронизированный
                    }
                )

                _uiState.value = state.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                Log.e("AddPlannedExpense", "Error saving", e)
                _uiState.value = state.copy(
                    isSaving = false,
                    errorMessage = "Ошибка сохранения: ${e.message}"
                )
            }
        }
    }
}

// ✅ ViewModelFactory
class AddPlannedExpenseViewModelFactory(
    private val application: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddPlannedExpenseViewModel::class.java)) {
            return AddPlannedExpenseViewModel(application, carId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}