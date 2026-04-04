package com.aggin.carcost.presentation.screens.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.CarDocument
import com.aggin.carcost.data.local.database.entities.DocumentType
import com.aggin.carcost.data.local.repository.CarDocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DocumentsUiState(
    val documents: List<CarDocument> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CarDocumentRepository(
        AppDatabase.getDatabase(application).carDocumentDao()
    )

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    fun loadDocuments(carId: String) {
        viewModelScope.launch {
            repository.getDocuments(carId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { docs ->
                    _uiState.update { it.copy(documents = docs, isLoading = false) }
                }
        }
    }

    fun addDocument(
        carId: String,
        type: DocumentType,
        title: String,
        fileUri: String?,
        expiryDate: Long?,
        notes: String?
    ) {
        viewModelScope.launch {
            val doc = CarDocument(
                carId = carId,
                type = type,
                title = title,
                fileUri = fileUri,
                expiryDate = expiryDate,
                notes = notes
            )
            repository.addDocument(doc)
        }
    }

    fun updateDocument(
        document: CarDocument,
        type: DocumentType,
        title: String,
        fileUri: String?,
        expiryDate: Long?,
        notes: String?
    ) {
        viewModelScope.launch {
            repository.updateDocument(
                document.copy(
                    type = type,
                    title = title,
                    fileUri = fileUri,
                    expiryDate = expiryDate,
                    notes = notes
                )
            )
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
        }
    }
}
