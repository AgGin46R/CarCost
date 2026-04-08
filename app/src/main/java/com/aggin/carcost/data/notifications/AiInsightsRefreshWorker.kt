package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.domain.ai.BreakdownPredictionEngine
import com.aggin.carcost.domain.ai.ExpenseAnalysisEngine
import com.aggin.carcost.domain.ai.SmartTipsEngine
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.first

class AiInsightsRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ai_insights_refresh"
    }

    override suspend fun doWork(): Result {
        // Wait for auth to settle — skip if not authenticated
        val sessionStatus = supabase.auth.sessionStatus.first {
            it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated
        }
        if (sessionStatus !is SessionStatus.Authenticated) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val insightDao = db.aiInsightDao()

        return try {
            val cars = carDao.getAllActiveCars().first()
            for (car in cars) {
                val expenses = expenseDao.getExpensesByCar(car.id).first()

                val existing = insightDao.getInsightsByCarId(car.id).first()
                val readTitles = existing.filter { it.isRead }.map { it.title }.toSet()

                val newInsights = mutableListOf<com.aggin.carcost.data.local.database.entities.AiInsight>()
                newInsights += ExpenseAnalysisEngine.analyze(car.id, expenses)
                newInsights += SmartTipsEngine.generateTips(car.id, expenses)
                newInsights += BreakdownPredictionEngine.predict(car.id, car, expenses)

                insightDao.deleteByCarId(car.id)
                if (newInsights.isNotEmpty()) {
                    val toInsert = newInsights.map { insight ->
                        if (insight.title in readTitles) insight.copy(isRead = true) else insight
                    }
                    insightDao.insertInsights(toInsert)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
