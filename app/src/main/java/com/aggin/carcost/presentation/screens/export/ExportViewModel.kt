package com.aggin.carcost.presentation.screens.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.export.ExportService
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Состояние UI для экрана экспорта
data class ExportUiState(
    val isLoading: Boolean = true,
    val car: Car? = null,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccessMessage: String? = null,
    val filterStartDate: Long? = null,
    val filterEndDate: Long? = null,
    val selectedCategories: Set<ExpenseCategory> = ExpenseCategory.entries.toSet(),
    /** Разобранный, но ещё НЕ применённый бэкап — показываем сводку до восстановления */
    val pendingBackup: com.aggin.carcost.data.backup.CarCostBackup? = null,
    val isRestoring: Boolean = false
)

class ExportViewModel(
    application: Application,
    private val carId: String // ID автомобиля для экспорта
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val carDao = database.carDao()
    private val expenseDao = database.expenseDao()
    private val reminderDao = database.maintenanceReminderDao()
    private val exportService = ExportService(application)
    private val backupService = com.aggin.carcost.data.backup.BackupService(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadCarDetails()
    }

    private fun loadCarDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // ИСПРАВЛЕНО: Убираем .first(). getCarById - это suspend-функция
            val car = carDao.getCarById(carId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    car = car
                )
            }
        }
    }

    fun setDateFilter(startDate: Long?, endDate: Long?) {
        _uiState.update { it.copy(filterStartDate = startDate, filterEndDate = endDate) }
    }

    fun toggleCategory(category: ExpenseCategory) {
        val current = _uiState.value.selectedCategories.toMutableSet()
        if (category in current) current.remove(category) else current.add(category)
        _uiState.update { it.copy(selectedCategories = current) }
    }

    fun selectAllCategories() {
        val current = _uiState.value.selectedCategories
        val allSelected = current.size == ExpenseCategory.entries.size
        _uiState.update {
            it.copy(selectedCategories = if (allSelected) emptySet() else ExpenseCategory.entries.toSet())
        }
    }

    fun exportToPdf() {
        com.aggin.carcost.data.analytics.Analytics.reportExported()
        export(ExportType.PDF)
    }

    fun exportToCsv() {
        export(ExportType.CSV)
    }

    /**
     * Резервная копия в JSON.
     *
     * Раньше здесь собирался человекочитаемый CSV, который приложение не умело
     * прочитать обратно: без идентификаторов, без напоминаний, документов и
     * бюджетов, с датами в локальном формате. Кнопка обещала страховку, которой
     * не было. CSV и PDF остались отдельно — как отчёты.
     */
    fun exportBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }
            try {
                val file = backupService.createBackupFile()
                exportService.shareFile(file)
                _uiState.update {
                    it.copy(isExporting = false, exportSuccessMessage = "Резервная копия создана")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "Ошибка резервной копии: ${e.message}") }
            }
        }
    }

    /** Читает выбранный файл и показывает, что в нём, НЕ трогая базу */
    fun peekBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupService.peek(uri)
                .onSuccess { backup -> _uiState.update { it.copy(pendingBackup = backup) } }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingBackup = null) }
    }

    fun confirmRestore() {
        val backup = _uiState.value.pendingBackup ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }
            backupService.restore(backup)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            pendingBackup = null,
                            exportSuccessMessage = "Восстановлено: ${backup.summary}"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRestoring = false, pendingBackup = null, errorMessage = e.message) }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, exportSuccessMessage = null) }
    }

    private fun export(type: ExportType) {
        viewModelScope.launch {
            val car = _uiState.value.car ?: return@launch
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }

            try {
                val allExpenses = expenseDao.getExpensesByCar(car.id).first()

                // Фильтрация по периоду и категориям
                val state = _uiState.value
                val expenses = allExpenses.filter { expense ->
                    val afterStart = state.filterStartDate?.let { expense.date >= it } ?: true
                    val beforeEnd = state.filterEndDate?.let { expense.date <= it } ?: true
                    val inCategory = expense.category in state.selectedCategories
                    afterStart && beforeEnd && inCategory
                }

                val reminders = reminderDao.getAllRemindersByCarId(car.id).first()

                val file = when (type) {
                    ExportType.PDF -> exportService.exportToPdf(car, expenses, reminders)
                    ExportType.CSV -> exportService.exportToCsv(car, expenses, reminders)
                }

                exportService.shareFile(file)

                val periodNote = if (state.filterStartDate != null || state.filterEndDate != null)
                    " (${expenses.size} записей за период)"
                else ""

                _uiState.update {
                    it.copy(isExporting = false, exportSuccessMessage = "Файл создан$periodNote")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, errorMessage = "Ошибка экспорта: ${e.message}")
                }
            }
        }
    }
}

private enum class ExportType {
    PDF, CSV
}

// Фабрика для ViewModel, чтобы передать carId
class ExportViewModelFactory(
    private val application: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExportViewModel(application, carId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}