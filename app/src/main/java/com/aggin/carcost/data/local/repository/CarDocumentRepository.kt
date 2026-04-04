package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.CarDocumentDao
import com.aggin.carcost.data.local.database.entities.CarDocument
import kotlinx.coroutines.flow.Flow

class CarDocumentRepository(private val dao: CarDocumentDao) {

    fun getDocuments(carId: String): Flow<List<CarDocument>> = dao.getDocumentsByCarId(carId)

    suspend fun addDocument(document: CarDocument) = dao.insertDocument(document)

    suspend fun updateDocument(document: CarDocument) =
        dao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteDocument(id: String) = dao.deleteDocument(id)

    suspend fun getExpiredDocuments(carId: String): List<CarDocument> =
        dao.getExpiredDocuments(carId)
}
