package com.aggin.carcost.data.local.database.entities

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class FavoritePlaceType { HOME, WORK, OTHER }

@Serializable
@Entity(tableName = "favorite_places")
data class FavoritePlace(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val type: FavoritePlaceType = FavoritePlaceType.OTHER,
    val createdAt: Long = System.currentTimeMillis()
)
