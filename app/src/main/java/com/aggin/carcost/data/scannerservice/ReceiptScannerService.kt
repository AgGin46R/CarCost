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
    val photoUri: String? = null,
    // Fuel-specific fields
    val fuelLiters: Double? = null,
    val odometer: Int? = null,
    val stationName: String? = null,
    val fuelType: String? = null
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
                    continuation.resume(
                        ReceiptData(
                            amount = extractAmount(fullText),
                            date = extractDate(fullText),
                            text = fullText,
                            photoUri = imageUri.toString(),
                            fuelLiters = extractFuelLiters(fullText),
                            odometer = extractOdometer(fullText),
                            stationName = extractStationName(fullText),
                            fuelType = extractFuelType(fullText)
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
                    continuation.resume(
                        ReceiptData(
                            amount = extractAmount(fullText),
                            date = extractDate(fullText),
                            text = fullText,
                            fuelLiters = extractFuelLiters(fullText),
                            odometer = extractOdometer(fullText),
                            stationName = extractStationName(fullText),
                            fuelType = extractFuelType(fullText)
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
     * Извлечение количества литров топлива из текста чека АЗС
     */
    private fun extractFuelLiters(text: String): Double? {
        val patterns = listOf(
            Regex("""(\d+[.,]\d{1,3})\s*(?:л|L|лит|ltr|litr)""", RegexOption.IGNORE_CASE),
            Regex("""(?:объём|объем|кол-во|количество|литры?|Litres?)\s*:?\s*(\d+[.,]\d{1,3})""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]\d{3})\s*(?:л|L)""")  // e.g. 42,350 л
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (value in 1.0..200.0) return value  // sanity check
        }
        return null
    }

    /**
     * Извлечение показаний одометра
     */
    private fun extractOdometer(text: String): Int? {
        val patterns = listOf(
            Regex("""(?:пробег|одометр|км пути|odometer)\s*:?\s*(\d{4,6})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{5,6})\s*(?:км|km)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].toIntOrNull() ?: continue
            if (value in 100..999999) return value
        }
        return null
    }

    /**
     * Извлечение названия АЗС
     */
    private fun extractStationName(text: String): String? {
        val knownStations = listOf("Лукойл", "Газпром", "Роснефть", "BP", "Shell",
            "Татнефть", "Башнефть", "Сургутнефтегаз", "ЕКА", "Neste",
            "Трасса", "Магна", "Rusнефть", "Опти", "Лавр", "G-Drive")
        val upperText = text.uppercase()
        return knownStations.firstOrNull { upperText.contains(it.uppercase()) }
    }

    /**
     * Определение типа топлива из текста чека
     */
    private fun extractFuelType(text: String): String? {
        val upperText = text.uppercase()
        return when {
            upperText.contains("АИ-98") || upperText.contains("AI-98") || upperText.contains("98") -> "АИ-98"
            upperText.contains("АИ-95") || upperText.contains("AI-95") || upperText.contains("95") -> "АИ-95"
            upperText.contains("АИ-92") || upperText.contains("AI-92") || upperText.contains("92") -> "АИ-92"
            upperText.contains("ДИЗЕЛ") || upperText.contains("ДТ") || upperText.contains("DIESEL") -> "Дизель"
            upperText.contains("ГАЗ") || upperText.contains("LPG") || upperText.contains("CNG") -> "Газ"
            else -> null
        }
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