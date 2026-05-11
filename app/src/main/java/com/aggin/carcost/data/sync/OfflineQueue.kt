package com.aggin.carcost.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.PendingWrite

/**
 * Thin helper that enqueues a mutation for later sync when the device is offline,
 * or triggers the [OfflineWriteWorker] if already online.
 *
 * Usage:
 * ```kotlin
 * OfflineQueue.enqueue(context, "expenses", "INSERT", expense.toJsonString())
 * ```
 */
object OfflineQueue {

    suspend fun enqueue(
        context: Context,
        tableName: String,
        operation: String,    // "INSERT" | "UPDATE" | "DELETE"
        payload: String       // JSON string of the row
    ) {
        val db = AppDatabase.getDatabase(context)
        db.pendingWriteDao().insert(
            PendingWrite(
                tableName = tableName,
                operation = operation,
                payload = payload
            )
        )
        // If online, kick off the worker immediately so it flushes right away
        if (isOnline(context)) {
            OfflineWriteWorker.schedule(context)
        }
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
