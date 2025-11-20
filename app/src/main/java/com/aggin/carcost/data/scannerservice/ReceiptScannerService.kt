package com.aggin.carcost.data.scannerservice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class ReceiptData(
    val amount: Double? = null,
    val date: Long? = null,
    val text: String = "",
    val photoUri: String? = null
)

class ReceiptScannerService(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Сканировать чек из Uri изображения
     */
    suspend fun scanReceipt(imageUri: Uri): ReceiptData = suspendCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, imageUri)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val amount = extractAmount(fullText)
                    val date = extractDate(fullText)

                    continuation.resume(
                        ReceiptData(
                            amount = amount,
                            date = date,
                            text = fullText,
                            photoUri = imageUri.toString()
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resume(
                        ReceiptData(
                            text = "Ошибка распознавания: ${e.message}",
                            photoUri = imageUri.toString()
                        )
                    )
                }
        } catch (e: Exception) {
            continuation.resume(
                ReceiptData(
                    text = "Ошибка: ${e.message}"
                )
            )
        }
    }

    /**
     * Сканировать чек из Bitmap
     */
    suspend fun scanReceipt(bitmap: Bitmap): ReceiptData = suspendCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val amount = extractAmount(fullText)
                    val date = extractDate(fullText)

                    continuation.resume(
                        ReceiptData(
                            amount = amount,
                            date = date,
                            text = fullText
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resume(
                        ReceiptData(text = "Ошибка распознавания: ${e.message}")
                    )
                }
        } catch (e: Exception) {
            continuation.resume(
                ReceiptData(text = "Ошибка: ${e.message}")
            )
        }
    }

    /**
     * Извлечение суммы из текста чека
     * Ищет паттерны: "ИТОГО", "TOTAL", "СУММА", за которыми следует число
     */
    private fun extractAmount(text: String): Double? {
        // Паттерны для поиска суммы
        val patterns = listOf(
            // ИТОГО: 1234.56 или ИТОГО 1234.56
            Regex("""(?:ИТОГО|ИТОГ|TOTAL|СУММА|СУМ|К ОПЛАТЕ)\s*:?\s*(\d+[.,]\d+)""", RegexOption.IGNORE_CASE),
            // Просто числа с руб или ₽
            Regex("""(\d+[.,]\d{2})\s*(?:руб|₽|RUB)""", RegexOption.IGNORE_CASE),
            // Любое число с двумя десятичными знаками в конце строки
            Regex("""(\d+[.,]\d{2})$""", RegexOption.MULTILINE)
        )

        // Ищем все суммы
        val amounts = mutableListOf<Double>()

        patterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val amountStr = match.groupValues[1].replace(',', '.')
                amountStr.toDoubleOrNull()?.let { amounts.add(it) }
            }
        }

        // Возвращаем максимальную сумму (обычно это итоговая сумма)
        return amounts.maxOrNull()
    }

    /**
     * Извлечение даты из текста чека
     */
    private fun extractDate(text: String): Long? {
        // Паттерны дат: DD.MM.YYYY, DD/MM/YYYY, DD-MM-YYYY
        val datePatterns = listOf(
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) to Regex("""(\d{2}\.\d{2}\.\d{4})"""),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) to Regex("""(\d{2}/\d{2}/\d{4})"""),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) to Regex("""(\d{2}-\d{2}-\d{4})"""),
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) to Regex("""(\d{4}\.\d{2}\.\d{2})"""),
            SimpleDateFormat("dd.MM.yy", Locale.getDefault()) to Regex("""(\d{2}\.\d{2}\.\d{2})""")
        )

        datePatterns.forEach { (format, pattern) ->
            pattern.find(text)?.let { match ->
                try {
                    return format.parse(match.value)?.time
                } catch (e: Exception) {
                    // Продолжаем поиск
                }
            }
        }

        return null
    }

    /**
     * Сохранить фото чека
     */
    fun saveReceiptPhoto(bitmap: Bitmap, expenseId: Long): String {
        val receiptsDir = File(context.filesDir, "receipts")
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs()
        }

        val fileName = "receipt_${expenseId}_${System.currentTimeMillis()}.jpg"
        val file = File(receiptsDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return file.absolutePath
    }

    /**
     * Сохранить фото чека из Uri
     */
    fun saveReceiptPhoto(uri: Uri, expenseId: Long): String {
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        return saveReceiptPhoto(bitmap, expenseId)
    }

    /**
     * Получить фото чека
     */
    fun getReceiptPhoto(photoPath: String): Bitmap? {
        return try {
            val file = File(photoPath)
            if (file.exists()) {
                BitmapFactory.decodeFile(photoPath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Удалить фото чека
     */
    fun deleteReceiptPhoto(photoPath: String): Boolean {
        return try {
            File(photoPath).delete()
        } catch (e: Exception) {
            false
        }
    }
}