package com.aggin.carcost.presentation.screens.carbot

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val isAiGenerated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class CarBotUiState(
    val messages: List<BotMessage> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String? = null,
    val isProcessing: Boolean = false,
    val inputText: String = "",
    // AI model state
    val isModelDownloaded: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val modelDownloadProgress: Int = 0,
    val isModelReady: Boolean = false,        // true after LlmInference is initialized
    val isModelInitializing: Boolean = false, // true while engine loads into memory
    val modelInitError: String? = null        // non-null when initialization failed
)

class CarBotViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val engine = CarBotEngine(db)
    private val contextBuilder = CarContextBuilder()
    private val modelManager = GemmaModelManager(application.filesDir)
    private var gemmaEngine: GemmaInferenceEngine? = null

    private val _messages = MutableStateFlow<List<BotMessage>>(emptyList())
    private val _selectedCarId = MutableStateFlow<String?>(null)
    private val _isProcessing = MutableStateFlow(false)
    private val _inputText = MutableStateFlow("")

    // All model-related flags in one flow so combine() reacts to every change
    private data class ModelState(
        val isDownloaded: Boolean,
        val isDownloading: Boolean,
        val progress: Int,
        val isReady: Boolean,
        val isInitializing: Boolean = false,
        val initError: String? = null
    )
    private val _modelState = MutableStateFlow(
        ModelState(
            isDownloaded = modelManager.isDownloaded,
            isDownloading = false,
            progress = 0,
            isReady = false
        )
    )

    // Group 1: messages + cars list
    private val _chatBase = combine(
        _messages,
        db.carDao().getAllActiveCars()
    ) { messages, cars -> Pair(messages, cars) }

    // Group 2: selection + processing + input
    private val _inputBase = combine(
        _selectedCarId,
        _isProcessing,
        _inputText
    ) { selectedCarId, isProcessing, inputText -> Triple(selectedCarId, isProcessing, inputText) }

    // Final combine of 3 flows — all model flags now reactive via _modelState
    val uiState: StateFlow<CarBotUiState> = combine(
        _chatBase,
        _inputBase,
        _modelState
    ) { (messages, cars), (selectedCarId, isProcessing, inputText), modelState ->
        val effectiveCarId = selectedCarId
            ?: if (cars.size == 1) cars.first().id else null
        CarBotUiState(
            messages = messages,
            cars = cars,
            selectedCarId = effectiveCarId,
            isProcessing = isProcessing,
            inputText = inputText,
            isModelDownloaded = modelState.isDownloaded,
            isDownloadingModel = modelState.isDownloading,
            modelDownloadProgress = modelState.progress,
            isModelReady = modelState.isReady,
            isModelInitializing = modelState.isInitializing,
            modelInitError = modelState.initError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CarBotUiState(
            isModelDownloaded = modelManager.isDownloaded
        )
    )

    init {
        addBotMessage("👋 Привет! Я **CarBot** — ваш автомобильный помощник. Задайте вопрос о расходах, ТО или состоянии автомобиля.\n\nНапишите **помощь**, чтобы узнать, что я умею.")

        // Proactive alerts (after short delay so car data loads)
        viewModelScope.launch {
            delay(800)
            val carId = uiState.value.selectedCarId
            try {
                val alerts = engine.checkProactiveAlerts(carId)
                if (alerts != null) addBotMessage(alerts)
            } catch (_: Exception) {}
        }

        // Auto-init Gemma if already downloaded
        if (modelManager.isDownloaded) {
            initGemmaIfReady(application)
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun selectCar(carId: String) {
        _selectedCarId.value = carId
        val car = uiState.value.cars.firstOrNull { it.id == carId }
        if (car != null) {
            addBotMessage("Переключился на **${car.brand} ${car.model}**.")
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

                // 0. Special commands
                if (text.trim().lowercase().let { it.contains("перезапустить ai") || it.contains("перезапустить ии") }) {
                    if (_modelState.value.isDownloaded && !_modelState.value.isReady) {
                        addBotMessage("🔄 Перезапускаю AI-движок...")
                        gemmaEngine?.close()
                        gemmaEngine = null
                        _modelState.update { it.copy(isReady = false, initError = null, isInitializing = false) }
                        initGemmaIfReady(getApplication())
                    } else if (_modelState.value.isReady) {
                        addBotMessage("✅ AI-движок уже работает и готов к ответам.")
                    } else {
                        addBotMessage("ℹ️ Для перезапуска сначала скачайте AI-модель (кнопка ✨ AI вверху).")
                    }
                    return@launch
                }

                // 1. Rules-based first (fast, offline)
                val rulesAnswer = withContext(Dispatchers.IO) {
                    engine.rulesBasedQuery(text, carId)
                }

                if (rulesAnswer != null) {
                    addBotMessage(rulesAnswer, isAiGenerated = false)
                    return@launch
                }

                // 2. AI fallback
                val gemma = gemmaEngine
                if (gemma == null || !gemma.isReady) {
                    val hint = when {
                        _modelState.value.initError != null ->
                            "⚠️ AI-движок не запустился.\n\nНапишите **перезапустить AI** чтобы попробовать снова."
                        _modelState.value.isInitializing ->
                            "⏳ AI-движок загружается в память, это может занять до 30 секунд. Пожалуйста, подождите."
                        _modelState.value.isDownloaded ->
                            "⏳ AI-движок ещё не запустился. Подождите несколько секунд и попробуйте снова."
                        else ->
                            "🤔 Не понял вопрос. Для умных ответов — скачайте AI-модель (кнопка ✨ AI вверху)."
                    }
                    addBotMessage(hint)
                    return@launch
                }

                val systemCtx = withContext(Dispatchers.IO) {
                    contextBuilder.buildPrompt(carId, db)
                }
                val aiAnswer = gemma.infer(systemCtx, text)
                addBotMessage(aiAnswer, isAiGenerated = true)

            } catch (e: Exception) {
                addBotMessage("Произошла ошибка: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun sendSuggestion(suggestion: String) {
        sendMessage(suggestion)
    }

    // ── AI Model management ───────────────────────────────────────────────────

    fun downloadModel(context: Context) {
        if (_modelState.value.isDownloading) return
        _modelState.update { it.copy(isDownloading = true, progress = 0) }

        viewModelScope.launch {
            modelManager.downloadModel(
                onProgress = { progress ->
                    _modelState.update { it.copy(progress = progress) }
                },
                onComplete = {
                    _modelState.update { it.copy(isDownloaded = true, isDownloading = false) }
                    addBotMessage("✅ AI-модель загружена! Инициализирую движок, подождите...")
                    initGemmaIfReady(context)
                },
                onError = { error ->
                    _modelState.update { it.copy(isDownloading = false) }
                    addBotMessage("❌ Ошибка загрузки модели: $error")
                }
            )
        }
    }

    fun deleteModel() {
        gemmaEngine?.close()
        gemmaEngine = null
        modelManager.deleteModel()
        _modelState.update { it.copy(isDownloaded = false, isReady = false, initError = null) }
        addBotMessage("AI-модель удалена. Для умных ответов загрузите её снова.")
    }

    fun initGemmaIfReady(context: Context) {
        if (!modelManager.isDownloaded) return
        if (_modelState.value.isReady || _modelState.value.isInitializing) return
        _modelState.update { it.copy(isInitializing = true, initError = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val eng = GemmaInferenceEngine(modelManager.modelFile.absolutePath)
                eng.initialize(context)   // blocks ~10–60 s for 1 GB model
                gemmaEngine = eng
                _modelState.update { it.copy(isReady = true, isInitializing = false) }
                withContext(Dispatchers.Main) {
                    addBotMessage("✨ AI-движок готов! Теперь я отвечаю на любые вопросы.")
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Неизвестная ошибка"
                _modelState.update { it.copy(isInitializing = false, initError = msg) }
                withContext(Dispatchers.Main) {
                    addBotMessage("⚠️ Не удалось запустить AI-движок: $msg")
                }
            }
        }
    }

    fun retryInitGemma(context: Context) {
        _modelState.update { it.copy(initError = null) }
        initGemmaIfReady(context)
    }

    private fun addBotMessage(text: String, isAiGenerated: Boolean = false) {
        _messages.update { it + BotMessage(text = text, isFromUser = false, isAiGenerated = isAiGenerated) }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaEngine?.close()
    }
}
