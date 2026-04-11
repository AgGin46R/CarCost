package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.CarDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: CarDocument)

    @Query("SELECT * FROM car_documents WHERE carId = :carId ORDER BY createdAt DESC")
    fun getDocumentsByCarId(carId: String): Flow<List<CarDocument>>

    @Query("SELECT * FROM car_documents WHERE id = :id")
    suspend fun getDocumentById(id: String): CarDocument?

    @Update
    suspend fun updateDocument(document: CarDocument)

    @Query("DELETE FROM car_documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT * FROM car_documents WHERE carId = :carId AND expiryDate IS NOT NULL AND expiryDate < :timestamp")
    suspend fun getExpiredDocuments(carId: String, timestamp: Long = System.currentTimeMillis()): List<CarDocument>

    @Query("SELECT * FROM car_documents WHERE expiryDate IS NOT NULL AND expiryDate BETWEEN :from AND :to")
    suspend fun getDocumentsExpiringBetween(from: Long, to: Long): List<CarDocument>
}
