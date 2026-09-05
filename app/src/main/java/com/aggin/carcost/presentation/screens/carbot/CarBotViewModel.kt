package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.R
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.presentation.common.displayName
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
    val modelInitError: String? = null,       // non-null when initialization failed
    /**
     * Команда, разобранная из фразы, но ещё не выполненная.
     *
     * Запись создаётся только после подтверждения. Фраза может прийти голосом,
     * а распознавание ошибается: «две тысячи четыреста» легко становится другим
     * числом, и молча созданная запись потом ищется по всей истории.
     */
    val pendingExpense: CarBotCommand.Command.AddExpense? = null,
    /**
     * Что уместно спросить дальше.
     *
     * Раньше подсказки показывались только до первого сообщения и потом
     * исчезали навсегда — узнать, что ещё умеет бот, было неоткуда, кроме
     * команды «что умеешь», о которой тоже надо догадаться. Теперь после
     * каждого ответа предлагается продолжение по смыслу этого ответа.
     */
    val followUps: List<String> = emptyList()
)

class CarBotViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val engine = CarBotEngine(db, application)
    private val contextBuilder = CarContextBuilder(application)
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

    /** Команда, разобранная из фразы и ждущая подтверждения */
    private val _pendingExpense = MutableStateFlow<CarBotCommand.Command.AddExpense?>(null)

    /** Подсказки-продолжения после последнего ответа */
    private val _followUps = MutableStateFlow<List<String>>(emptyList())

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

    // Final combine — все флаги модели реактивны через _modelState
    val uiState: StateFlow<CarBotUiState> = combine(
        _chatBase,
        _inputBase,
        _modelState,
        _pendingExpense,
        _followUps
    ) { (messages, cars), (selectedCarId, isProcessing, inputText), modelState, pending, followUps ->
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
            modelInitError = modelState.initError,
            pendingExpense = pending,
            followUps = followUps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CarBotUiState(
            isModelDownloaded = modelManager.isDownloaded
        )
    )

    init {
        addBotMessage(application.getString(R.string.carbot_greeting))

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
        _followUps.value = emptyList()

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

                // 1. Команда? Тогда не отвечаем, а делаем — после подтверждения
                val command = CarBotCommand.parse(text)
                if (command != null) {
                    handleCommand(command)
                    return@launch
                }

                // 2. Rules-based first (fast, offline)
                val rulesAnswer = withContext(Dispatchers.IO) {
                    engine.rulesBasedQuery(text, carId)
                }

                if (rulesAnswer != null) {
                    addBotMessage(rulesAnswer, isAiGenerated = false)
                    _followUps.value = followUpsFor(engine.lastIntent)
                    return@launch
                }

                // 3. AI fallback
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
                            // Не зовём скачивать 1,2 ГБ на вопрос, ответ на
                            // который приложение и так знает из своей базы:
                            // сначала показываем, что бот умеет
                            "🤔 Не понял вопрос. Напишите **что умеешь** — там список " +
                                "того, о чём можно спросить и что можно поручить."
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

    // ── Команды ───────────────────────────────────────────────────────────────


    /** Экран, на который бот просит перейти. Читается экраном и сбрасывается */
    private val _navigateTo = MutableStateFlow<CarBotCommand.Command.Target?>(null)
    val navigateTo: StateFlow<CarBotCommand.Command.Target?> = _navigateTo

    private suspend fun handleCommand(command: CarBotCommand.Command) {
        when (command) {
            is CarBotCommand.Command.Open -> {
                _navigateTo.value = command.target
                addBotMessage("Открываю → " + targetName(command.target))
            }

            is CarBotCommand.Command.AddExpense -> {
                val carId = uiState.value.selectedCarId
                if (carId == null) {
                    addBotMessage("Записывать некуда: сначала выберите автомобиль.")
                    return
                }
                _pendingExpense.value = command
                addBotMessage(previewOf(command))
            }
        }
    }

    private fun targetName(target: CarBotCommand.Command.Target): String = when (target) {
        CarBotCommand.Command.Target.ANALYTICS -> "аналитику"
        CarBotCommand.Command.Target.EXPENSES -> "расходы"
        CarBotCommand.Command.Target.MAINTENANCE -> "ТО"
        CarBotCommand.Command.Target.DOCUMENTS -> "документы"
        CarBotCommand.Command.Target.NAVIGATOR -> "навигатор"
        CarBotCommand.Command.Target.BUDGET -> "бюджет"
        CarBotCommand.Command.Target.TIMELINE -> "таймлайн ТО"
    }

    fun consumeNavigation() { _navigateTo.value = null }

    /** Что именно будет записано — человек видит это до записи, а не после */
    private fun previewOf(c: CarBotCommand.Command.AddExpense): String {
        val app = getApplication<Application>()
        val parts = mutableListOf<String>()
        parts += c.category.displayName(app)
        parts += "${c.amount.toInt()} ₽"
        c.liters?.let { parts += "${it} л" }
        c.odometer?.let { parts += "${it} км" }
        return "Записать: **" + parts.joinToString(" · ") + "**?"
    }

    fun confirmPendingExpense() {
        val command = _pendingExpense.value ?: return
        val carId = uiState.value.selectedCarId ?: return
        _pendingExpense.value = null

        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val car = withContext(Dispatchers.IO) { db.carDao().getCarById(carId) }
                val expense = com.aggin.carcost.data.local.database.entities.Expense(
                    carId = carId,
                    category = command.category,
                    amount = command.amount,
                    date = System.currentTimeMillis(),
                    odometer = command.odometer ?: car?.currentOdometer ?: 0,
                    fuelLiters = command.liters,
                    title = null
                )
                withContext(Dispatchers.IO) {
                    db.expenseDao().insertExpense(expense)
                    // Пробег автомобиля — по наибольшему из записей, как и везде
                    com.aggin.carcost.data.local.repository.CarRepository(db.carDao())
                        .refreshOdometerFromExpenses(carId, db.expenseDao())
                    // Отправка на сервер: без неё запись осталась бы только здесь
                    runCatching {
                        com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository(
                            com.aggin.carcost.data.remote.repository.SupabaseAuthRepository()
                        ).insertExpense(expense)
                    }
                }
                addBotMessage("Готово. Запись добавлена и уехала на сервер.")
            } catch (e: Exception) {
                addBotMessage("Не удалось записать: ${e.message}")
            }
        }
    }

    fun cancelPendingExpense() {
        if (_pendingExpense.value == null) return
        _pendingExpense.value = null
        addBotMessage("Отменил, ничего не записал.")
    }

    /**
     * Что предложить спросить после ответа.
     *
     * Продолжения зависят от того, о чём был вопрос: после суммы уместно
     * сравнить с прошлым месяцем, после расхода топлива — посмотреть заправки.
     * Это единственное место, откуда человек узнаёт о возможностях бота по
     * ходу разговора, не читая список умений целиком.
     */
    private fun followUpsFor(intent: CarBotQuery.Intent?): List<String> {
        val app = getApplication<Application>()
        fun s(id: Int) = app.getString(id)
        return when (intent) {
            CarBotQuery.Intent.SPENDING -> listOf(
                s(R.string.carbot_follow_last_month),
                s(R.string.carbot_follow_peak_month),
                s(R.string.carbot_follow_total)
            )
            CarBotQuery.Intent.FUEL_CONSUMPTION -> listOf(
                s(R.string.carbot_follow_fuel_spend),
                s(R.string.carbot_follow_trips),
                s(R.string.carbot_follow_price_per_km)
            )
            CarBotQuery.Intent.MAINTENANCE -> listOf(
                s(R.string.carbot_follow_open_service),
                s(R.string.carbot_follow_service_spend),
                s(R.string.carbot_follow_insurance)
            )
            CarBotQuery.Intent.INSURANCE -> listOf(
                s(R.string.carbot_follow_open_docs),
                s(R.string.carbot_follow_maintenance)
            )
            CarBotQuery.Intent.BUDGET -> listOf(
                s(R.string.carbot_follow_this_month),
                s(R.string.carbot_follow_peak_month)
            )
            CarBotQuery.Intent.TRIPS -> listOf(
                s(R.string.carbot_follow_fuel),
                s(R.string.carbot_follow_price_per_km)
            )
            CarBotQuery.Intent.CAR_INFO, CarBotQuery.Intent.RECENT,
            CarBotQuery.Intent.PEAK_MONTH, CarBotQuery.Intent.TOTAL -> listOf(
                s(R.string.carbot_follow_this_month),
                s(R.string.carbot_follow_fuel)
            )
            else -> emptyList()
        }
    }

    /** Очищает переписку и память о предыдущем вопросе */
    fun clearConversation() {
        engine.forgetContext()
        _followUps.value = emptyList()
        _pendingExpense.value = null
        _messages.value = emptyList()
        addBotMessage(getApplication<Application>().getString(R.string.carbot_greeting))
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
