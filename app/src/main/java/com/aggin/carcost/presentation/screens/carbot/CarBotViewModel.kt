package com.aggin.carcost.presentation.screens.carbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class BotMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class CarBotUiState(
    val messages: List<BotMessage> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String? = null,
    val isProcessing: Boolean = false,
    val inputText: String = ""
)

class CarBotViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val engine = CarBotEngine(db)

    private val _messages = MutableStateFlow<List<BotMessage>>(emptyList())
    private val _selectedCarId = MutableStateFlow<String?>(null)
    private val _isProcessing = MutableStateFlow(false)
    private val _inputText = MutableStateFlow("")

    val uiState: StateFlow<CarBotUiState> = combine(
        _messages,
        db.carDao().getAllActiveCars(),
        _selectedCarId,
        _isProcessing,
        _inputText
    ) { messages, cars, selectedCarId, isProcessing, inputText ->
        // Auto-select the only car if there's just one
        val effectiveCarId = selectedCarId
            ?: if (cars.size == 1) cars.first().id else null
        CarBotUiState(
            messages = messages,
            cars = cars,
            selectedCarId = effectiveCarId,
            isProcessing = isProcessing,
            inputText = inputText
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CarBotUiState()
    )

    init {
        // Greeting message
        addBotMessage("👋 Привет! Я **CarBot** — ваш автомобильный помощник. Задайте вопрос о расходах, ТО или состоянии автомобиля.\n\nНапишите **помощь**, чтобы узнать, что я умею.")
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun selectCar(carId: String) {
        _selectedCarId.value = carId
        val state = uiState.value
        val car = state.cars.firstOrNull { it.id == carId }
        if (car != null) {
            addBotMessage("✅ Переключился на **${car.brand} ${car.model}**.")
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isProcessing.value) return
        _inputText.value = ""

        val userMsg = BotMessage(text = text, isFromUser = true)
        _messages.update { it + userMsg }
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val carId = uiState.value.selectedCarId
                val response = withContext(Dispatchers.IO) {
                    engine.processQuery(text, carId)
                }
                addBotMessage(response)
            } catch (e: Exception) {
                addBotMessage("❌ Произошла ошибка: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun sendSuggestion(suggestion: String) {
        sendMessage(suggestion)
    }

    private fun addBotMessage(text: String) {
        _messages.update { it + BotMessage(text = text, isFromUser = false) }
    }
}
