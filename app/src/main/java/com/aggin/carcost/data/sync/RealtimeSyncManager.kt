package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.remote.repository.CarDto
import com.aggin.carcost.data.remote.repository.ExpenseDto
import com.aggin.carcost.data.remote.repository.ChatMessageDto
import com.aggin.carcost.data.remote.repository.MaintenanceReminderDto
import com.aggin.carcost.data.remote.repository.toChatMessage
import com.aggin.carcost.data.remote.repository.toExpense
import com.aggin.carcost.data.remote.repository.toCar
import com.aggin.carcost.data.remote.repository.toMaintenanceReminder
import com.aggin.carcost.supabase
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.abs

class RealtimeSyncManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val TAG = "RealtimeSync"

    private var activeChannel: RealtimeChannel? = null

    /**
     * Observes the Supabase auth state. Starts the WebSocket channel only when
     * the user is authenticated — this prevents the "connection refused / 401"
     * errors that appear when the channel is subscribed with the anon key before
     * a valid JWT is available.
     */
    fun start() {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        if (activeChannel == null) {
                            Log.d(TAG, "Auth confirmed — starting Realtime channel")
                            connectChannel()
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        Log.d(TAG, "User signed out — stopping Realtime channel")
                        disconnectChannel()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        scope.launch { disconnectChannel() }
    }

    // -------------------------------------------------------------------------

    private suspend fun connectChannel() {
        try {
            val ch = supabase.channel("carcost-sync")
            activeChannel = ch

            // ── Expenses ─────────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    db.expenseDao().insertExpense(dto.toExpense())
                    Log.d(TAG, "📥 Expense inserted: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling expense insert", e)
                }
            }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    db.expenseDao().insertExpense(dto.toExpense())
                    Log.d(TAG, "✏️ Expense updated: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling expense update", e)
                }
            }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.oldRecord)
                    db.expenseDao().deleteExpenseById(dto.id)
                    Log.d(TAG, "🗑️ Expense deleted: ${dto.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling expense delete", e)
                }
            }.launchIn(scope)

            // ── Cars ──────────────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    db.carDao().insertCar(dto.toCar())
                    Log.d(TAG, "🚗 Car synced: ${dto.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling car insert", e)
                }
            }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    db.carDao().insertCar(dto.toCar())
                    Log.d(TAG, "🚗 Car updated: ${dto.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling car update", e)
                }
            }.launchIn(scope)

            // ── Maintenance Reminders ─────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(
                        MaintenanceReminderDto.serializer(), change.record
                    )
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder inserted: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling reminder insert", e)
                }
            }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(
                        MaintenanceReminderDto.serializer(), change.record
                    )
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder updated: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling reminder update", e)
                }
            }.launchIn(scope)

            // ── Chat Messages ─────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.record)
                    db.chatMessageDao().insert(dto.toChatMessage())
                    Log.d(TAG, "💬 Chat message received: ${dto.id}")
                    maybeNotifyChat(dto)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling chat message", e)
                }
            }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.oldRecord)
                    db.chatMessageDao().deleteById(dto.id)
                    Log.d(TAG, "🗑️ Chat message deleted: ${dto.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling chat delete", e)
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "✅ Realtime channel subscribed")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect Realtime channel", e)
            activeChannel = null
        }
    }

    private suspend fun disconnectChannel() {
        try {
            activeChannel?.unsubscribe()
            activeChannel?.let { supabase.realtime.removeChannel(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting Realtime channel: ${e.message}")
        } finally {
            activeChannel = null
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    /**
     * Shows a push notification for an expense change if it was made
     * by a DIFFERENT user (i.e. a member of a shared car).
     */
    private suspend fun maybeNotifyExpense(dto: ExpenseDto, isUpdate: Boolean) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return   // my own action — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val actorEmail = db.carMemberDao().getEmailByUserId(dto.userId)
        val categoryName = NotificationHelper.categoryDisplayName(dto.category)
        val notifId = EXPENSE_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendSharedExpenseNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            categoryName = categoryName,
            amount = dto.amount,
            actorEmail = actorEmail,
            isUpdate = isUpdate
        )
        Log.d(TAG, "🔔 Sent expense notification for ${dto.id}")
    }

    /**
     * Shows a push notification for a maintenance reminder change if it was made
     * by a DIFFERENT user.
     */
    private suspend fun maybeNotifyReminder(dto: MaintenanceReminderDto, isUpdate: Boolean) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return   // my own action — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val actorEmail = db.carMemberDao().getEmailByUserId(dto.userId)
        val typeName = NotificationHelper.reminderTypeDisplayName(dto.type)
        val notifId = REMINDER_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendSharedReminderNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            reminderTypeName = typeName,
            actorEmail = actorEmail,
            isUpdate = isUpdate
        )
        Log.d(TAG, "🔔 Sent reminder notification for ${dto.id}")
    }

    private suspend fun maybeNotifyChat(dto: ChatMessageDto) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return  // own message — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val sender = dto.userEmail.substringBefore("@")
        val notifId = CHAT_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendChatNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            senderName = sender,
            message = dto.message
        )
        Log.d(TAG, "🔔 Sent chat notification from $sender")
    }

    companion object {
        private const val EXPENSE_NOTIF_BASE  = 20_000
        private const val REMINDER_NOTIF_BASE = 30_000
        private const val CHAT_NOTIF_BASE     = 40_000
        private const val NOTIF_RANGE         = 9_000
    }
}
