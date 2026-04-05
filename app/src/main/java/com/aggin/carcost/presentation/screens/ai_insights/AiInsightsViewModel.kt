package com.aggin.carcost.presentation.screens.ai_insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.notifications.AiInsightsRefreshWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AiInsightsUiState(
    val insights: List<AiInsight> = emptyList(),
    val isLoading: Boolean = true,
    val unreadCount: Int = 0
)

class AiInsightsViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val insightDao = db.aiInsightDao()

    val uiState: StateFlow<AiInsightsUiState> = combine(
        insightDao.getInsightsByCarId(carId),
        insightDao.getUnreadCount(carId)
    ) { insights, unread ->
        AiInsightsUiState(
            insights = insights,
            isLoading = false,
            unreadCount = unread
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiInsightsUiState()
    )

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
