package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Offline write queue — stores mutations that failed to sync with Supabase.
 * The sync worker reads these and retries them in order of [createdAt].
 */
@Entity(tableName = "pending_writes")
data class PendingWrite(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /** Table name in Supabase, e.g. "expenses", "maintenance_reminders" */
    val tableName: String,

    /** "INSERT" | "UPDATE" | "DELETE" */
    val operation: String,

    /** JSON payload (full row for INSERT/UPDATE, just {id} for DELETE) */
    val payload: String,

    /** How many times we've tried and failed */
    val retryCount: Int = 0,

    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null
)
