package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.domain.ai.BreakdownPredictionEngine
import com.aggin.carcost.domain.ai.ExpenseAnalysisEngine
import com.aggin.carcost.domain.ai.SmartTipsEngine
import kotlinx.coroutines.flow.first

class AiInsightsRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ai_insights_refresh"
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val insightDao = db.aiInsightDao()

        return try {
            val cars = carDao.getAllCars().first()
            for (car in cars) {
                val expenses = expenseDao.getExpensesByCar(car.id).first()

                // Clear old insights before regenerating
                insightDao.deleteByCarId(car.id)

                val newInsights = mutableListOf<com.aggin.carcost.data.local.database.entities.AiInsight>()
                newInsights += ExpenseAnalysisEngine.analyze(car.id, expenses)
                newInsights += SmartTipsEngine.generateTips(car.id, expenses)
                newInsights += BreakdownPredictionEngine.predict(car.id, car, expenses)

                if (newInsights.isNotEmpty()) {
                    insightDao.insertInsights(newInsights)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
