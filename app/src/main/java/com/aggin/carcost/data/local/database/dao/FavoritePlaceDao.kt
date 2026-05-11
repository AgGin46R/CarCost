package com.aggin.carcost.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aggin.carcost.data.local.database.entities.FavoritePlace
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePlaceDao {

    @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC")
    fun getAllFavoritePlaces(): Flow<List<FavoritePlace>>

    @Query("SELECT * FROM favorite_places WHERE id = :id")
    suspend fun getFavoritePlaceById(id: String): FavoritePlace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoritePlace(place: FavoritePlace)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteFavoritePlace(id: String)

    @Query("DELETE FROM favorite_places")
    suspend fun deleteAllFavoritePlaces()
}
