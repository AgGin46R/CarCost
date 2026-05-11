package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.PendingWrite
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.TimeUnit

/**
 * Processes the offline write queue ([PendingWrite] table).
 *
 * Strategy:
 * - Runs whenever network becomes available (CONNECTED constraint).
 * - Reads all pending writes ordered by [createdAt].
 * - For each write, attempts the corresponding Supabase operation.
 * - On success → deletes from queue.
 * - On failure → increments [retryCount]; records with retryCount >= 5 are
 *   purged by [PendingWriteDao.deleteExhausted].
 */
class OfflineWriteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "offline_write_flush"
        private const val TAG = "OfflineWriteWorker"
        private const val MAX_RETRIES = 5

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineWriteWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    private val db = AppDatabase.getDatabase(applicationContext)
    private val pendingDao = db.pendingWriteDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pending = try {
            pendingDao.getAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read pending writes", e)
            return@withContext Result.retry()
        }

        if (pending.isEmpty()) return@withContext Result.success()

        // Purge exhausted entries first
        pendingDao.deleteExhausted(MAX_RETRIES)

        var anyFailure = false
        for (write in pending) {
            val success = processWrite(write)
            if (success) {
                pendingDao.deleteById(write.id)
            } else {
                anyFailure = true
                pendingDao.update(
                    write.copy(
                        retryCount = write.retryCount + 1,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
            }
        }

        if (anyFailure) Result.retry() else Result.success()
    }

    private suspend fun processWrite(write: PendingWrite): Boolean {
        return try {
            val payloadObj = Json.parseToJsonElement(write.payload).jsonObject
            when (write.operation) {
                "INSERT", "UPDATE" -> {
                    supabase.from(write.tableName).upsert(payloadObj)
                    true
                }
                "DELETE" -> {
                    val id = payloadObj["id"]?.toString()?.trim('"')
                    if (id.isNullOrBlank()) {
                        Log.w(TAG, "DELETE write missing id field: ${write.id}")
                        true // drop malformed
                    } else {
                        supabase.from(write.tableName).delete {
                            filter { eq("id", id) }
                        }
                        true
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown operation '${write.operation}' — dropping write ${write.id}")
                    true // drop unknown ops
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process write ${write.id} (${write.operation} ${write.tableName})", e)
            false
        }
    }
}
