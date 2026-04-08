package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.notifications.ActiveChatTracker
import com.aggin.carcost.data.notifications.NotificationHelper
import com.aggin.carcost.data.remote.repository.CarDto
import com.aggin.carcost.data.remote.repository.ExpenseDto
import com.aggin.carcost.data.remote.repository.ChatMessageDto
import com.aggin.carcost.data.remote.repository.MaintenanceReminderDto
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.abs

class RealtimeSyncManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val TAG = "RealtimeSync"
    private val json = Json { ignoreUnknownKeys = true }
    private val carRepo = SupabaseCarRepository(SupabaseAuthRepository())

    // Перехватывает SocketException/IOException, которые бросает Supabase Realtime при обрыве
    // TCP-соединения (сеть упала, телефон ушёл в сон). Без этого хэндлера exception
    // попадает в SupervisorJob без обработчика → FATAL EXCEPTION → краш.
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is java.net.SocketException,
            is java.io.IOException,
            is java.net.ConnectException -> {
                Log.w(TAG, "Network error in Realtime scope (caught, no crash): ${throwable.message}")
            }
            else -> {
                Log.e(TAG, "Unexpected error in Realtime scope: ${throwable.message}", throwable)
            }
        }
        // Сбрасываем канал — reconnectIfNeeded() при следующем форегрануде переподключится
        activeChannel = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private var activeChannel: RealtimeChannel? = null

    /**
     * Ensures a car with the given [carId] exists in local Room DB.
     * If not, fetches it from Supabase (via RLS — works for shared cars too) and inserts it.
     * This prevents FOREIGN KEY constraint failures when Realtime delivers a record
     * whose car hasn't been synced locally yet.
     */
    private suspend fun ensureCarExists(carId: String) {
        if (db.carDao().getCarById(carId) != null) return
        Log.d(TAG, "Car $carId not in local DB — fetching from Supabase")
        carRepo.fetchSharedCar(carId).onSuccess { car ->
            db.carDao().insertCar(car)
            Log.d(TAG, "✅ Car $carId fetched and cached locally")
        }.onFailure {
            Log.w(TAG, "Could not fetch car $carId: ${it.message}")
        }
    }

    /**
     * Observes the Supabase auth state. (Re)starts the WebSocket channel whenever
     * the user is authenticated and the channel is not already SUBSCRIBED.
     */
    fun start() {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val channelOk = activeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
                        if (!channelOk) {
                            Log.d(TAG, "Auth confirmed — (re)connecting Realtime (was: ${activeChannel?.status?.value})")
                            disconnectChannel()
                            safeConnect()
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

    /**
     * Call this when the app comes to foreground to ensure the Realtime channel
     * is active. Silently ignored if already SUBSCRIBED.
     */
    fun reconnectIfNeeded() {
        scope.launch {
            val session = supabase.auth.sessionStatus.value
            if (session !is SessionStatus.Authenticated) return@launch
            val channelOk = activeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
            if (!channelOk) {
                Log.d(TAG, "Foreground reconnect — channel was: ${activeChannel?.status?.value}")
                disconnectChannel()
                safeConnect()
            } else {
                Log.d(TAG, "Foreground check — channel already SUBSCRIBED")
            }
        }
    }

    /** Обёртка над connectChannel — ловит SocketException и другие сетевые ошибки,
     *  которые иначе крашат приложение через DefaultDispatcher-worker */
    private suspend fun safeConnect() {
        try {
            connectChannel()
        } catch (e: java.net.SocketException) {
            Log.w(TAG, "SocketException during Realtime connect — will retry on next auth event: ${e.message}")
            activeChannel = null
        } catch (e: Exception) {
            Log.w(TAG, "Realtime connect failed: ${e.message}")
            activeChannel = null
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
                    ensureCarExists(dto.carId)
                    db.expenseDao().insertExpense(dto.toExpense())
                    Log.d(TAG, "📥 Expense inserted: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = false)
                } catch (e: Exception) { Log.e(TAG, "Error handling expense insert", e) }
            }.catch { Log.w(TAG, "Expense insert flow error: ${it.message}") }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    ensureCarExists(dto.carId)
                    db.expenseDao().insertExpense(dto.toExpense())
                    Log.d(TAG, "✏️ Expense updated: ${dto.id}")
                    maybeNotifyExpense(dto, isUpdate = true)
                } catch (e: Exception) { Log.e(TAG, "Error handling expense update", e) }
            }.catch { Log.w(TAG, "Expense update flow error: ${it.message}") }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.oldRecord)
                    db.expenseDao().deleteExpenseById(dto.id)
                    Log.d(TAG, "🗑️ Expense deleted: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling expense delete", e) }
            }.catch { Log.w(TAG, "Expense delete flow error: ${it.message}") }.launchIn(scope)

            // ── Cars ──────────────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    db.carDao().insertCar(dto.toCar())
                    Log.d(TAG, "🚗 Car synced: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling car insert", e) }
            }.catch { Log.w(TAG, "Car insert flow error: ${it.message}") }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "cars"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(CarDto.serializer(), change.record)
                    db.carDao().insertCar(dto.toCar())
                    Log.d(TAG, "🚗 Car updated: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling car update", e) }
            }.catch { Log.w(TAG, "Car update flow error: ${it.message}") }.launchIn(scope)

            // ── Maintenance Reminders ─────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(MaintenanceReminderDto.serializer(), change.record)
                    ensureCarExists(dto.carId)
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder inserted: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = false)
                } catch (e: Exception) { Log.e(TAG, "Error handling reminder insert", e) }
            }.catch { Log.w(TAG, "Reminder insert flow error: ${it.message}") }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "maintenance_reminders"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(MaintenanceReminderDto.serializer(), change.record)
                    ensureCarExists(dto.carId)
                    db.maintenanceReminderDao().insertReminder(dto.toMaintenanceReminder())
                    Log.d(TAG, "🔧 Reminder updated: ${dto.id}")
                    maybeNotifyReminder(dto, isUpdate = true)
                } catch (e: Exception) { Log.e(TAG, "Error handling reminder update", e) }
            }.catch { Log.w(TAG, "Reminder update flow error: ${it.message}") }.launchIn(scope)

            // ── Chat Messages ─────────────────────────────────────────────────

            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.record)
                    ensureCarExists(dto.carId)
                    db.chatMessageDao().insert(dto.toChatMessage())
                    Log.d(TAG, "💬 Chat message received: ${dto.id}")
                    maybeNotifyChat(dto)
                } catch (e: Exception) { Log.e(TAG, "Error handling chat message", e) }
            }.catch { Log.w(TAG, "Chat insert flow error: ${it.message}") }.launchIn(scope)

            ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "chat_messages"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ChatMessageDto.serializer(), change.oldRecord)
                    db.chatMessageDao().deleteById(dto.id)
                    Log.d(TAG, "🗑️ Chat message deleted: ${dto.id}")
                } catch (e: Exception) { Log.e(TAG, "Error handling chat delete", e) }
            }.catch { Log.w(TAG, "Chat delete flow error: ${it.message}") }.launchIn(scope)

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
            isUpdate = isUpdate,
            carId = dto.carId
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
            isUpdate = isUpdate,
            carId = dto.carId
        )
        Log.d(TAG, "🔔 Sent reminder notification for ${dto.id}")
    }

    private suspend fun maybeNotifyChat(dto: ChatMessageDto) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        if (dto.userId == currentUserId) return  // own message — skip
        if (ActiveChatTracker.activeCarId == dto.carId) return  // user is in this chat — skip

        val car = db.carDao().getCarById(dto.carId) ?: return
        val carName = "${car.brand} ${car.model}"
        val sender = dto.userEmail.substringBefore("@")
        val notifId = CHAT_NOTIF_BASE + (abs(dto.id.hashCode()) % NOTIF_RANGE)

        NotificationHelper.sendChatNotification(
            context = context,
            notificationId = notifId,
            carName = carName,
            senderName = sender,
            message = dto.message,
            carId = dto.carId
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
