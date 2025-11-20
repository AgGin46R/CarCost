package com.aggin.carcost.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val uid: String,  // Firebase UID

    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),

    val isPremium: Boolean = false,
    val premiumUntil: Long? = null
) : Parcelable