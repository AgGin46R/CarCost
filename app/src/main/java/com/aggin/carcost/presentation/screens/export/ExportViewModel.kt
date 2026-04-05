package com.aggin.carcost.presentation.screens.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.export.ExportService
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
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
    val filterEndDate: Long? = null
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

    fun setDateFilter(startDate: Long?, endDate: Long?) {
        _uiState.update { it.copy(filterStartDate = startDate, filterEndDate = endDate) }
    }

    fun exportToPdf() {
        export(ExportType.PDF)
    }

    fun exportToCsv() {
        export(ExportType.CSV)
    }

    fun exportBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }
            try {
                val allCars = carDao.getAllCars().first()
                val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

                val csv = buildString {
                    appendLine("# CarCost Backup — ${dateFmt.format(Date())}")
                    appendLine("# Cars: ${allCars.size}")
                    appendLine()

                    for (car in allCars) {
                        appendLine("## CAR: ${car.brand} ${car.model} ${car.year} | ${car.licensePlate} | ${car.currentOdometer} km")
                        val expenses = expenseDao.getExpensesByCar(car.id).first()
                        appendLine("# EXPENSES (${expenses.size})")
                        appendLine("date,category,amount,currency,odometer,title,description,location,workshopName,fuelLiters,serviceType")
                        expenses.sortedByDescending { it.date }.forEach { e ->
                            appendLine(
                                "${dateFmt.format(Date(e.date))}," +
                                "${e.category}," +
                                "${e.amount}," +
                                "${e.currency}," +
                                "${e.odometer}," +
                                "\"${e.title?.replace("\"", "'") ?: ""}\"," +
                                "\"${e.description?.replace("\"", "'") ?: ""}\"," +
                                "\"${e.location?.replace("\"", "'") ?: ""}\"," +
                                "\"${e.workshopName?.replace("\"", "'") ?: ""}\"," +
                                "${e.fuelLiters ?: ""}," +
                                "${e.serviceType ?: ""}"
                            )
                        }
                        appendLine()
                    }
                }

                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val file = File(getApplication<Application>().cacheDir, "carcost_backup_$dateStr.csv")
                file.writeText(csv)

                exportService.shareFile(file)
                _uiState.update {
                    it.copy(isExporting = false, exportSuccessMessage = "Резервная копия создана (${allCars.size} авто)!")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "Ошибка резервной копии: ${e.message}") }
            }
        }
    }

    private fun export(type: ExportType) {
        viewModelScope.launch {
            val car = _uiState.value.car ?: return@launch
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }

            try {
                val allExpenses = expenseDao.getExpensesByCar(car.id).first()

                // Фильтрация по периоду
                val state = _uiState.value
                val expenses = allExpenses.filter { expense ->
                    val afterStart = state.filterStartDate?.let { expense.date >= it } ?: true
                    val beforeEnd = state.filterEndDate?.let { expense.date <= it } ?: true
                    afterStart && beforeEnd
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
                    it.copy(isExporting = false, exportSuccessMessage = "Файл создан$periodNote!")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, errorMessage = "Ошибка экспорта: ${e.message}")
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