package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.domain.gamification.AchievementChecker

class YearOwnerCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "year_owner_check"
    }

    override suspend fun doWork(): Result {
        try {
            val userId = SupabaseAuthRepository().getUserId() ?: return Result.success()
            val db = AppDatabase.getDatabase(applicationContext)
            AchievementChecker(
                achievementDao = db.achievementDao(),
                expenseDao = db.expenseDao(),
                carDao = db.carDao()
            ).checkYearOwner(userId)
        } catch (e: Exception) {
            android.util.Log.e("YearOwnerCheck", "Failed to check YEAR_OWNER achievement", e)
        }
        return Result.success()
    }
}
