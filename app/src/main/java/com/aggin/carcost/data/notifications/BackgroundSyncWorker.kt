package com.aggin.carcost.data.notifications

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private val Context.syncDataStore by preferencesDataStore("background_sync_prefs")
private val KEY_LAST_SYNC = longPreferencesKey("last_background_sync_ms")

/**
 * WorkManager worker that runs periodically even when the app is killed.
 *
 * Strategy:
 * 1. Read `last_sync_timestamp` from DataStore
 * 2. Query Supabase for new chat_messages / expenses / maintenance_reminders
 *    added by OTHER users since that timestamp (only for cars I'm a member of)
 * 3. Show a local notification for each type of change
 * 4. Update `last_sync_timestamp`
 *
 * This gives near-realtime notifications (≤15 min) without FCM or Firebase.
 * When the app is open, Supabase Realtime handles instant updates.
 */
class BackgroundSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "background_sync"
        private const val TAG = "BackgroundSyncWorker"

        // ISO 8601 formatter for Supabase timestamptz queries
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private const val CHAT_BASE     = 50_000
        private const val EXPENSE_BASE  = 55_000
        private const val REMINDER_BASE = 60_000

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    // ── Supabase DTOs ─────────────────────────────────────────────────────────
    // created_at is a Supabase timestamptz → comes as ISO 8601 string

    @Serializable
    private data class ChatRow(
        val id: String,
        @SerialName("car_id") val carId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("user_email") val userEmail: String,
        val message: String,
        @SerialName("created_at") val createdAt: String  // "2025-04-06T12:34:56.123456+00:00"
    )

    @Serializable
    private data class ExpenseRow(
        val id: String,
        @SerialName("car_id") val carId: String,
        @SerialName("user_id") val userId: String,
        val category: String,
        val amount: Double,
        @SerialName("created_at") val createdAt: String
    )

    @Serializable
    private data class ReminderRow(
        val id: String,
        @SerialName("car_id") val carId: String,
        @SerialName("user_id") val userId: String,
        val type: String,
        @SerialName("created_at") val createdAt: String
    )

    // ── Worker entry point ────────────────────────────────────────────────────

    override suspend fun doWork(): Result {
        return try {
            // Ждём пока auth загрузится из storage (может быть асинхронным при старте)
            val sessionStatus = supabase.auth.sessionStatus.first {
                it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated
            }
            if (sessionStatus !is SessionStatus.Authenticated) {
                Log.d(TAG, "Not authenticated — skipping background sync")
                return Result.success()
            }
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return Result.success()

            val db = AppDatabase.getDatabase(context)
            val prefs = context.syncDataStore.data.first()
            val lastSyncMs = prefs[KEY_LAST_SYNC] ?: (System.currentTimeMillis() - 60_000L)
            val nowMs = System.currentTimeMillis()

            // Convert epoch ms → ISO 8601 UTC string for Supabase filter
            val lastSyncIso = isoFormat.format(Date(lastSyncMs))
            Log.d(TAG, "Background sync: checking since $lastSyncIso")

            // Get IDs of all cars this user is a member of
            val myCars = db.carDao().getAllActiveCars().first().map { it.id }
            if (myCars.isEmpty()) {
                updateLastSync(nowMs)
                return Result.success()
            }

            var notifCount = 0

            // ── Chat messages ─────────────────────────────────────────────────
            try {
                val newMessages = supabase.from("chat_messages")
                    .select {
                        filter {
                            isIn("car_id", myCars)
                            gt("created_at", lastSyncIso)
                            neq("user_id", currentUserId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(20)
                    }
                    .decodeList<ChatRow>()

                newMessages.groupBy { it.carId }.forEach { (carId, msgs) ->
                    val car = db.carDao().getCarById(carId) ?: return@forEach
                    val carName = "${car.brand} ${car.model}"
                    val sender = msgs.first().userEmail.substringBefore("@")
                    val body = if (msgs.size == 1) msgs.first().message
                               else "${msgs.size} новых сообщений"

                    NotificationHelper.sendChatNotification(
                        context = context,
                        notificationId = CHAT_BASE + abs(carId.hashCode() % 8_000),
                        carName = carName,
                        senderName = sender,
                        message = body,
                        carId = carId
                    )
                    notifCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chat sync failed: ${e.message}")
            }

            // ── New expenses ──────────────────────────────────────────────────
            try {
                val newExpenses = supabase.from("expenses")
                    .select {
                        filter {
                            isIn("car_id", myCars)
                            gt("created_at", lastSyncIso)
                            neq("user_id", currentUserId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(20)
                    }
                    .decodeList<ExpenseRow>()

                newExpenses.groupBy { it.carId }.forEach { (carId, expenses) ->
                    val car = db.carDao().getCarById(carId) ?: return@forEach
                    val carName = "${car.brand} ${car.model}"
                    val actorEmail = db.carMemberDao().getEmailByUserId(expenses.first().userId)
                    val exp = expenses.first()
                    val categoryName = NotificationHelper.categoryDisplayName(exp.category)

                    if (expenses.size == 1) {
                        NotificationHelper.sendSharedExpenseNotification(
                            context = context,
                            notificationId = EXPENSE_BASE + abs(carId.hashCode() % 8_000),
                            carName = carName,
                            categoryName = categoryName,
                            amount = exp.amount,
                            actorEmail = actorEmail,
                            isUpdate = false,
                            carId = carId
                        )
                    } else {
                        NotificationHelper.sendSharedExpenseNotification(
                            context = context,
                            notificationId = EXPENSE_BASE + abs(carId.hashCode() % 8_000),
                            carName = carName,
                            categoryName = "${expenses.size} новых расходов",
                            amount = expenses.sumOf { it.amount },
                            actorEmail = actorEmail,
                            isUpdate = false,
                            carId = carId
                        )
                    }
                    notifCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Expenses sync failed: ${e.message}")
            }

            // ── New maintenance reminders ─────────────────────────────────────
            try {
                val newReminders = supabase.from("maintenance_reminders")
                    .select {
                        filter {
                            isIn("car_id", myCars)
                            gt("created_at", lastSyncIso)
                            neq("user_id", currentUserId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<ReminderRow>()

                newReminders.groupBy { it.carId }.forEach { (carId, reminders) ->
                    val car = db.carDao().getCarById(carId) ?: return@forEach
                    val carName = "${car.brand} ${car.model}"
                    val actorEmail = db.carMemberDao().getEmailByUserId(reminders.first().userId)
                    val typeName = NotificationHelper.reminderTypeDisplayName(reminders.first().type)

                    NotificationHelper.sendSharedReminderNotification(
                        context = context,
                        notificationId = REMINDER_BASE + abs(carId.hashCode() % 8_000),
                        carName = carName,
                        reminderTypeName = typeName,
                        actorEmail = actorEmail,
                        isUpdate = false,
                        carId = carId
                    )
                    notifCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reminders sync failed: ${e.message}")
            }

            updateLastSync(nowMs)
            Log.d(TAG, "Background sync done. Showed $notifCount notifications.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun updateLastSync(timestamp: Long) {
        context.syncDataStore.edit { prefs -> prefs[KEY_LAST_SYNC] = timestamp }
    }
}
