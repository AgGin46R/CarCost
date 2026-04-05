package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vin_cache")
data class VinCache(
    @PrimaryKey
    val vin: String,
    val make: String? = null,
    val model: String? = null,
    val year: String? = null,
    val engine: String? = null,
    val country: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
