package com.aggin.carcost.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.aggin.carcost.data.local.database.entities.TyreSet
import kotlinx.coroutines.flow.Flow

@Dao
interface TyreSetDao {

    /**
     * Запись без удаления строки.
     *
     * Upsert, а не INSERT OR REPLACE: REPLACE удаляет строку и вставляет
     * заново, а вместе с удалением срабатывают внешние ключи. На этой таблице
     * дочерних записей пока нет, но тот же приём уже стоил приложению
     * пропавших тегов и реакций — повторять не будем.
     */
    @Upsert
    suspend fun upsert(tyreSet: TyreSet)

    @Delete
    suspend fun delete(tyreSet: TyreSet)

    @Query("SELECT * FROM tyre_sets WHERE carId = :carId ORDER BY isInstalled DESC, season, name")
    fun getByCarId(carId: String): Flow<List<TyreSet>>

    @Query("SELECT * FROM tyre_sets WHERE carId = :carId ORDER BY isInstalled DESC, season, name")
    suspend fun getByCarIdSync(carId: String): List<TyreSet>

    @Query("SELECT * FROM tyre_sets WHERE id = :id")
    suspend fun getById(id: String): TyreSet?

    /** Комплект, стоящий на машине сейчас. null, когда ни один не отмечен */
    @Query("SELECT * FROM tyre_sets WHERE carId = :carId AND isInstalled = 1 LIMIT 1")
    suspend fun getInstalled(carId: String): TyreSet?

    @Query("UPDATE tyre_sets SET syncedAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: String, timestamp: Long = System.currentTimeMillis())
}
