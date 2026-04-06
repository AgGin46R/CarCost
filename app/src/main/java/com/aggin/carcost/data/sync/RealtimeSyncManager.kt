package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.repository.CarDto
import com.aggin.carcost.data.remote.repository.ExpenseDto
import com.aggin.carcost.data.remote.repository.toExpense
import com.aggin.carcost.data.remote.repository.toCar
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RealtimeSyncManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val TAG = "RealtimeSync"

    // Track the current active channel so we can cleanly unsubscribe
    private var activeChannel: RealtimeChannel? = null
    private var channelJob: Job? = null

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
                    // LoadingFromStorage / NetworkError — wait for final state
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

            // --- Expenses ---
            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "expenses"
            }.onEach { change ->
                try {
                    val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), change.record)
                    db.expenseDao().insertExpense(dto.toExpense())
                    Log.d(TAG, "📥 Expense inserted: ${dto.id}")
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

            // --- Cars ---
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
}
