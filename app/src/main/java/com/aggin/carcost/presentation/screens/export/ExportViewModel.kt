package com.aggin.carcost.presentation.screens.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.export.ExportService
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Состояние UI для экрана экспорта
data class ExportUiState(
    val isLoading: Boolean = true,
    val car: Car? = null,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccessMessage: String? = null
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

    fun exportToPdf() {
        export(ExportType.PDF)
    }

    fun exportToCsv() {
        export(ExportType.CSV)
    }

    private fun export(type: ExportType) {
        viewModelScope.launch {
            val car = _uiState.value.car ?: return@launch
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }

            try {
                // Получаем все данные для отчета
                val expenses = expenseDao.getExpensesByCar(car.id).first()
                // ИСПРАВЛЕНО: Используем новый метод getAllRemindersByCarId
                val reminders = reminderDao.getAllRemindersByCarId(car.id).first()

                val file = when (type) {
                    ExportType.PDF -> exportService.exportToPdf(car, expenses, reminders)
                    ExportType.CSV -> exportService.exportToCsv(car, expenses, reminders)
                }

                // Делимся файлом
                exportService.shareFile(file)

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "Файл успешно создан и готов к отправке!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Ошибка экспорта: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, exportSuccessMessage = null) }
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