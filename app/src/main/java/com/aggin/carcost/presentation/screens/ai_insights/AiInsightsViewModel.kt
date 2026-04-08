package com.aggin.carcost.presentation.screens.ai_insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.domain.ai.BreakdownPredictionEngine
import com.aggin.carcost.domain.ai.ExpenseAnalysisEngine
import com.aggin.carcost.domain.ai.SmartTipsEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AiInsightsUiState(
    val insights: List<AiInsight> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val unreadCount: Int = 0
)

class AiInsightsViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val insightDao = db.aiInsightDao()

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<AiInsightsUiState> = combine(
        insightDao.getInsightsByCarId(carId),
        insightDao.getUnreadCount(carId),
        _isRefreshing
    ) { insights, unread, refreshing ->
        AiInsightsUiState(
            insights = insights,
            isLoading = false,
            isRefreshing = refreshing,
            unreadCount = unread
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiInsightsUiState()
    )

    init {
        // Always generate fresh insights when screen opens
        viewModelScope.launch {
            generateInsights()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            generateInsights()
        }
    }

    fun markAsRead(insightId: String) {
        viewModelScope.launch {
            insightDao.markAsRead(insightId)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            uiState.value.insights.forEach { insightDao.markAsRead(it.id) }
        }
    }

    private suspend fun generateInsights() {
        _isRefreshing.value = true
        try {
            val car = db.carDao().getCarById(carId) ?: return
            val expenses = db.expenseDao().getExpensesByCar(carId).first()

            val newInsights = mutableListOf<AiInsight>()
            newInsights += ExpenseAnalysisEngine.analyze(carId, expenses)
            newInsights += SmartTipsEngine.generateTips(carId, expenses)
            newInsights += BreakdownPredictionEngine.predict(carId, car, expenses)

            // Replace old insights, preserving isRead state for unchanged ones
            val existing = insightDao.getInsightsByCarId(carId).first()
            val readTitles = existing.filter { it.isRead }.map { it.title }.toSet()

            insightDao.deleteByCarId(carId)
            if (newInsights.isNotEmpty()) {
                // Restore isRead for insights with same title (re-generated same tip)
                val toInsert = newInsights.map { insight ->
                    if (insight.title in readTitles) insight.copy(isRead = true) else insight
                }
                insightDao.insertInsights(toInsert)
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}

class AiInsightsViewModelFactory(
    private val application: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiInsightsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiInsightsViewModel(application, carId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
