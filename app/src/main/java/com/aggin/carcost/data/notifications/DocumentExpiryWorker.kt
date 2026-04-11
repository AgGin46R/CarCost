package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.data.local.database.AppDatabase
import java.util.concurrent.TimeUnit

class DocumentExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "document_expiry_check"
        private val CHECK_THRESHOLDS_DAYS = listOf(30, 7, 1)
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val documentDao = db.carDocumentDao()
        val carDao = db.carDao()

        val now = System.currentTimeMillis()

        for (days in CHECK_THRESHOLDS_DAYS) {
            val from = now + TimeUnit.DAYS.toMillis(days.toLong()) - TimeUnit.HOURS.toMillis(12)
            val to = now + TimeUnit.DAYS.toMillis(days.toLong()) + TimeUnit.HOURS.toMillis(12)
            val expiring = documentDao.getDocumentsExpiringBetween(from, to)

            expiring.forEachIndexed { i, document ->
                val car = carDao.getCarById(document.carId) ?: return@forEachIndexed
                val carName = "${car.brand} ${car.model}"
                val typeLabel = document.type.displayName
                val docTitle = if (document.title.isNotBlank()) document.title else typeLabel
                val body = "$docTitle истекает через $days ${dayWord(days)}"
                NotificationHelper.sendGenericNotification(
                    context = applicationContext,
                    notificationId = 6000 + i + days * 10,
                    title = "Документ: $carName",
                    body = body,
                    carId = document.carId,
                    navType = NotificationHelper.NAV_TYPE_CAR
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
