package com.aggin.carcost.presentation.screens.incidents

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.CarIncident
import com.aggin.carcost.data.local.database.entities.IncidentType
import com.aggin.carcost.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class IncidentHistoryUiState(
    val incidents: List<CarIncident> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingIncident: CarIncident? = null,
    val showDeleteDialog: CarIncident? = null,

    // Add/Edit form fields
    val formDate: Long = System.currentTimeMillis(),
    val formType: IncidentType = IncidentType.ACCIDENT,
    val formDescription: String = "",
    val formDamageAmount: String = "",
    val formRepairCost: String = "",
    val formRepairDate: Long? = null,
    val formLocation: String = "",
    val formInsuranceClaim: String = "",
    val formPhotoUri: String? = null,
    val formNotes: String = "",

    val isUploadingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

class IncidentHistoryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val carId: String = savedStateHandle.get<String>("carId") ?: ""
    private val database = AppDatabase.getDatabase(application)
    private val incidentDao = database.carIncidentDao()
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(IncidentHistoryUiState())
    val uiState: StateFlow<IncidentHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            incidentDao.getIncidentsByCarId(carId).collect { list ->
                _uiState.value = _uiState.value.copy(incidents = list, isLoading = false)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingIncident = null,
            formDate = System.currentTimeMillis(),
            formType = IncidentType.ACCIDENT,
            formDescription = "",
            formDamageAmount = "",
            formRepairCost = "",
            formRepairDate = null,
            formLocation = "",
            formInsuranceClaim = "",
            formPhotoUri = null,
            formNotes = "",
            showError = false
        )
    }

    fun showEditDialog(incident: CarIncident) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingIncident = incident,
            formDate = incident.date,
            formType = incident.type,
            formDescription = incident.description,
            formDamageAmount = incident.damageAmount?.toString() ?: "",
            formRepairCost = incident.repairCost?.toString() ?: "",
            formRepairDate = incident.repairDate,
            formLocation = incident.location ?: "",
            formInsuranceClaim = incident.insuranceClaimNumber ?: "",
            formPhotoUri = incident.photoUri,
            formNotes = incident.notes ?: "",
            showError = false
        )
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingIncident = null)
    }

    fun showDeleteConfirm(incident: CarIncident) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = incident)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = null)
    }

    fun updateFormDate(value: Long) { _uiState.value = _uiState.value.copy(formDate = value) }
    fun updateFormType(value: IncidentType) { _uiState.value = _uiState.value.copy(formType = value) }
    fun updateFormDescription(value: String) { _uiState.value = _uiState.value.copy(formDescription = value, showError = false) }
    fun updateFormDamageAmount(value: String) { if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) _uiState.value = _uiState.value.copy(formDamageAmount = value) }
    fun updateFormRepairCost(value: String) { if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) _uiState.value = _uiState.value.copy(formRepairCost = value) }
    fun updateFormRepairDate(value: Long?) { _uiState.value = _uiState.value.copy(formRepairDate = value) }
    fun updateFormLocation(value: String) { _uiState.value = _uiState.value.copy(formLocation = value) }
    fun updateFormInsuranceClaim(value: String) { _uiState.value = _uiState.value.copy(formInsuranceClaim = value) }
    fun updateFormNotes(value: String) { _uiState.value = _uiState.value.copy(formNotes = value) }

    fun uploadPhoto(uri: Uri) {
        val incidentId = _uiState.value.editingIncident?.id ?: java.util.UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = true, showError = false)
            }
            try {
                val bytes = compressImage(uri)
                val fileName = "incidents/$incidentId.jpg"
                val bucket = supabase.storage.from("car-photos")
                bucket.upload(path = fileName, data = bytes, upsert = true)
                val photoUrl = bucket.publicUrl(fileName)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(formPhotoUri = photoUrl, isUploadingPhoto = false)
                }
            } catch (e: Exception) {
                Log.e("IncidentHistory", "Photo upload failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        showError = true,
                        errorMessage = "Ошибка загрузки фото: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть изображение")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) throw IllegalStateException("Не удалось декодировать изображение")
        val maxSize = 1024
        val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height, 1f)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        bitmap.recycle(); scaledBitmap.recycle()
        outputStream.toByteArray()
    }

    fun saveIncident() {
        val state = _uiState.value
        if (state.formDescription.isBlank()) {
            _uiState.value = state.copy(showError = true, errorMessage = "Введите описание инцидента")
            return
        }

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val incident = (state.editingIncident ?: CarIncident(carId = carId, date = state.formDate, type = state.formType, description = "")).copy(
                    carId = carId,
                    date = state.formDate,
                    type = state.formType,
                    description = state.formDescription.trim(),
                    damageAmount = state.formDamageAmount.toDoubleOrNull(),
                    repairCost = state.formRepairCost.toDoubleOrNull(),
                    repairDate = state.formRepairDate,
                    location = state.formLocation.ifBlank { null },
                    insuranceClaimNumber = state.formInsuranceClaim.ifBlank { null },
                    photoUri = state.formPhotoUri,
                    notes = state.formNotes.ifBlank { null }
                )
                if (state.editingIncident != null) {
                    incidentDao.updateIncident(incident)
                } else {
                    incidentDao.insertIncident(incident)
                }
                _uiState.value = state.copy(isSaving = false, showAddDialog = false, editingIncident = null)
            } catch (e: Exception) {
                Log.e("IncidentHistory", "Save failed", e)
                _uiState.value = state.copy(isSaving = false, showError = true, errorMessage = "Ошибка сохранения: ${e.message}")
            }
        }
    }

    fun deleteIncident(incident: CarIncident) {
        viewModelScope.launch {
            try {
                incidentDao.deleteIncident(incident)
            } catch (e: Exception) {
                Log.e("IncidentHistory", "Delete failed", e)
            }
            _uiState.value = _uiState.value.copy(showDeleteDialog = null)
        }
    }
}
