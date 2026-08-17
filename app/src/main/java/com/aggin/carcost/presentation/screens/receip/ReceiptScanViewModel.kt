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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

data class ReceiptScanUiState(
    val receiptData: ReceiptData? = null,
    val isScanning: Boolean = false,
    val error: String? = null
)

class ReceiptScanViewModel : ViewModel() {

    private companion object {
        /** Больше этого превью не нужно: оно показывается в рамке размером с экран */
        const val PREVIEW_MAX_PX = 1080
    }

    private val _uiState = MutableStateFlow(ReceiptScanUiState())
    val uiState: StateFlow<ReceiptScanUiState> = _uiState.asStateFlow()

    /**
     * Уменьшенное изображение для показа в рамке предпросмотра.
     *
     * Считаем реальный размер снимка без его загрузки (inJustDecodeBounds) и
     * декодируем сразу уменьшенным. Полное разрешение здесь не нужно никому:
     * картинка занимает часть экрана, а распознаванию ML Kit читает файл сам.
     */
    suspend fun loadPreview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sample = 1
            while (bounds.outWidth / sample > PREVIEW_MAX_PX ||
                   bounds.outHeight / sample > PREVIEW_MAX_PX) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            android.util.Log.w("ReceiptScan", "Не удалось построить превью", e)
            null
        }
    }

    /**
     * Распознаватель создаётся один раз на экран, а не на каждое сканирование.
     *
     * Раньше `ReceiptScannerService(context)` создавался при каждом вызове, а
     * внутри него — клиент ML Kit, который держит нативные ресурсы и свой поток
     * и обязан закрываться. Не закрывался никогда: десяток отсканированных чеков
     * подряд оставлял десяток живых распознавателей, память не возвращалась до
     * перезапуска приложения.
     *
     * Контекст берём у приложения: держать в поле ViewModel ссылку на Activity
     * значит удерживать её вместе со всем деревом экрана.
     */
    private var scannerInstance: ReceiptScannerService? = null

    private fun scanner(context: Context): ReceiptScannerService =
        scannerInstance ?: ReceiptScannerService(context.applicationContext)
            .also { scannerInstance = it }

    override fun onCleared() {
        super.onCleared()
        scannerInstance?.close()
        scannerInstance = null
    }

    fun scanReceipt(uri: Uri, context: Context) {
        com.aggin.carcost.data.analytics.Analytics.receiptScanned()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)

            try {
                val data = scanner(context).scanReceipt(uri)

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
                val data = scanner(context).scanReceipt(bitmap)

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