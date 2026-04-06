package com.aggin.carcost.data.sync

import android.content.Context
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.repository.CarDto
import com.aggin.carcost.data.remote.repository.ExpenseDto
import com.aggin.carcost.data.remote.repository.toExpense
import com.aggin.carcost.data.remote.repository.toCar
import com.aggin.carcost.supabase
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RealtimeSyncManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = supabase.channel("carcost-sync")
    private val json = Json { ignoreUnknownKeys = true }

    private val TAG = "RealtimeSync"

    fun start() {
        scope.launch {
            try {
                setupExpensesSync()
                setupCarsSync()
                channel.subscribe()
                Log.d(TAG, "✅ Realtime sync started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start realtime sync", e)
            }
        }
    }

    fun stop() {
        scope.launch {
            try {
                channel.unsubscribe()
                Log.d(TAG, "Realtime sync stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping realtime sync", e)
            }
        }
    }

    private fun setupExpensesSync() {
        // New expense added
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
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

        // Expense updated
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
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

        // Expense deleted
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "expenses"
        }.onEach { change ->
            try {
                val oldRecord = change.oldRecord
                val dto = json.decodeFromJsonElement(ExpenseDto.serializer(), oldRecord)
                db.expenseDao().deleteExpenseById(dto.id)
                Log.d(TAG, "🗑️ Expense deleted: ${dto.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling expense delete", e)
            }
        }.launchIn(scope)
    }

    private fun setupCarsSync() {
        // Car inserted (shared car accepted)
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
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

        // Car updated (odometer, name, etc.)
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
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
    }
}
