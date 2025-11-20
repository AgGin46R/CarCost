package com.aggin.carcost.presentation.screens.receipt_scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.scannerservice.ReceiptData
import com.aggin.carcost.data.scannerservice.ReceiptScannerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReceiptScanUiState(
    val receiptData: ReceiptData? = null,
    val isScanning: Boolean = false,
    val error: String? = null
)

class ReceiptScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptScanUiState())
    val uiState: StateFlow<ReceiptScanUiState> = _uiState.asStateFlow()

    fun scanReceipt(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)

            try {
                val scanner = ReceiptScannerService(context)
                val data = scanner.scanReceipt(uri)

                _uiState.value = _uiState.value.copy(
                    receiptData = data,
                    isScanning = false,
                    error = if (data.amount == null && data.date == null) {
                        "Не удалось распознать сумму и дату"
                    } else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Ошибка сканирования: ${e.message}"
                )
            }
        }
    }

    fun scanReceiptFromBitmap(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)

            try {
                val scanner = ReceiptScannerService(context)
                val data = scanner.scanReceipt(bitmap)

                _uiState.value = _uiState.value.copy(
                    receiptData = data,
                    isScanning = false,
                    error = if (data.amount == null && data.date == null) {
                        "Не удалось распознать сумму и дату"
                    } else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Ошибка сканирования: ${e.message}"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = ReceiptScanUiState()
    }
}