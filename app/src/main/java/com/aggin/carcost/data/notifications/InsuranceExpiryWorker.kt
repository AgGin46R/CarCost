package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import java.util.concurrent.TimeUnit

class InsuranceExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "insurance_expiry_check"
        private val CHECK_THRESHOLDS_DAYS = listOf(30, 14, 7, 1)
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val policyDao = db.insurancePolicyDao()
        val carDao = db.carDao()

        val now = System.currentTimeMillis()

        for (days in CHECK_THRESHOLDS_DAYS) {
            val from = now + TimeUnit.DAYS.toMillis(days.toLong()) - TimeUnit.HOURS.toMillis(12)
            val to = now + TimeUnit.DAYS.toMillis(days.toLong()) + TimeUnit.HOURS.toMillis(12)
            val expiring = policyDao.getPoliciesExpiringBetween(from, to)

            expiring.forEachIndexed { i, policy ->
                val car = carDao.getCarById(policy.carId) ?: return@forEachIndexed
                val carName = "${car.brand} ${car.model}"
                val typeLabel = when (policy.type) {
                    "OSAGO" -> "ОСАГО"
                    "KASKO" -> "КАСКО"
                    else -> "Страховка"
                }
                val body = "$typeLabel истекает через $days ${dayWord(days)}"
                NotificationHelper.sendGenericNotification(
                    context = applicationContext,
                    notificationId = 5000 + i + days * 10,
                    title = "Страховка: $carName",
                    body = body,
                    carId = policy.carId,
                    navType = null
                )
            }
        }

        return Result.success()
    }

    private fun dayWord(days: Int): String = when {
        days == 1 -> "день"
        days in 2..4 -> "дня"
        else -> "дней"
    }
}
