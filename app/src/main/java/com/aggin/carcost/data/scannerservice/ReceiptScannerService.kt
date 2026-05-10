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
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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
    suspend fun scanReceipt(imageUri: Uri): ReceiptData = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, imageUri)

            val task = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    if (continuation.isActive) {
                        continuation.resume(parseReceiptText(fullText, imageUri.toString()))
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(
                            ReceiptData(
                                text = "Ошибка распознавания: ${e.message}",
                                photoUri = imageUri.toString()
                            )
                        )
                    }
                }

            continuation.invokeOnCancellation { task.addOnCanceledListener { } }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(ReceiptData(text = "Ошибка: ${e.message}"))
            }
        }
    }

    /**
     * Сканировать чек из Bitmap
     */
    suspend fun scanReceipt(bitmap: Bitmap): ReceiptData = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            val task = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    if (continuation.isActive) {
                        continuation.resume(parseReceiptText(fullText, null))
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(ReceiptData(text = "Ошибка распознавания: ${e.message}"))
                    }
                }

            continuation.invokeOnCancellation { task.addOnCanceledListener { } }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(ReceiptData(text = "Ошибка: ${e.message}"))
            }
        }
    }

    // ---------------------------------------------------------------------------
    //  Central parse entry point
    // ---------------------------------------------------------------------------

    private fun parseReceiptText(text: String, photoUri: String?): ReceiptData {
        return ReceiptData(
            amount = extractAmount(text),
            date = extractDate(text),
            text = text,
            photoUri = photoUri,
            fuelLiters = extractFuelLiters(text),
            odometer = extractOdometer(text),
            stationName = extractStationName(text),
            fuelType = extractFuelType(text)
        )
    }

    // ---------------------------------------------------------------------------
    //  Amount extraction
    //  Strategy: prefer explicitly labelled totals; fall back to largest value.
    // ---------------------------------------------------------------------------

    private fun extractAmount(text: String): Double? {
        val lines = text.lines()

        // 1. Find a line that contains a known total keyword, then extract number from it
        val totalKeywords = Regex(
            """^.*(ИТОГО|ИТОГ|TOTAL|СУММА|К\s*ОПЛАТЕ|К\s*ВЫДАЧЕ|ОПЛАЧЕНО|ЧЕКОМ|НАЛИЧНЫМИ|КАРТОЙ|БЕЗНАЛ).*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        for (match in totalKeywords.findAll(text)) {
            val lineText = match.value
            // Extract the last number on that line
            val num = extractLastNumber(lineText)
            if (num != null && num > 0.5) return num
        }

        // 2. Look for number followed by currency sign on any line
        val currencyPattern = Regex("""(\d[\d\s]*[.,]\d{2})\s*(?:руб\.?|₽|RUB)""", RegexOption.IGNORE_CASE)
        val currencyMatches = currencyPattern.findAll(text).mapNotNull { m ->
            m.groupValues[1].replace(Regex("""\s"""), "").replace(',', '.').toDoubleOrNull()
        }.filter { it > 0.5 }.toList()
        if (currencyMatches.isNotEmpty()) return currencyMatches.max()

        // 3. Collect all decimal numbers and return the maximum (safest fallback)
        val allNumbers = Regex("""(\d[\d\s]*[.,]\d{2})""").findAll(text).mapNotNull { m ->
            m.groupValues[1].replace(Regex("""\s"""), "").replace(',', '.').toDoubleOrNull()
        }.filter { it > 0.5 }.toList()
        return allNumbers.maxOrNull()
    }

    /** Extract the last number (possibly with spaces as thousands-sep) from a string. */
    private fun extractLastNumber(line: String): Double? {
        val pattern = Regex("""(\d[\d\s]*[.,]\d{1,2}|\d{3,})""")
        val matches = pattern.findAll(line).toList()
        if (matches.isEmpty()) return null
        val last = matches.last().value.replace(Regex("""\s"""), "").replace(',', '.')
        return last.toDoubleOrNull()
    }

    // ---------------------------------------------------------------------------
    //  Date extraction
    // ---------------------------------------------------------------------------

    private fun extractDate(text: String): Long? {
        data class DateFormat(val sdf: SimpleDateFormat, val regex: Regex)

        val formats = listOf(
            // Prefer full 4-digit year formats first
            DateFormat(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()), Regex("""(\d{2}\.\d{2}\.\d{4})""")),
            DateFormat(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()), Regex("""(\d{2}/\d{2}/\d{4})""")),
            DateFormat(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()), Regex("""(\d{2}-\d{2}-\d{4})""")),
            DateFormat(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()), Regex("""(\d{4}-\d{2}-\d{2})""")),
            DateFormat(SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()), Regex("""(\d{4}\.\d{2}\.\d{2})""")),
            // 2-digit year as last resort
            DateFormat(SimpleDateFormat("dd.MM.yy", Locale.getDefault()), Regex("""(\d{2}\.\d{2}\.\d{2})(?!\d)"""))
        )

        for (df in formats) {
            df.regex.find(text)?.let { match ->
                try {
                    val parsed = df.sdf.parse(match.groupValues[1])
                    if (parsed != null) return parsed.time
                } catch (_: Exception) { /* try next */ }
            }
        }

        return null
    }

    // ---------------------------------------------------------------------------
    //  Fuel liters extraction
    // ---------------------------------------------------------------------------

    private fun extractFuelLiters(text: String): Double? {
        val patterns = listOf(
            // "Отпущено: 42.350 л" / "Кол-во: 33,55 л"
            Regex("""(?:отпущено|объём|объем|кол-во|количество|литры?|litres?|volume)\s*:?\s*(\d+[.,]\d{1,3})\s*(?:л|l|ltr)?""", RegexOption.IGNORE_CASE),
            // "42.350 л" / "33,55 л" — number directly before unit
            Regex("""(\d+[.,]\d{1,3})\s*(?:л|л\.|ltr|litr)(?:\b|$)""", RegexOption.IGNORE_CASE),
            // "Топливо ХХ.ХХХ" line pattern without explicit unit
            Regex("""(?:топливо|бензин|дизель|заправлено)\s+(\d+[.,]\d{2,3})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (value in 0.5..500.0) return value  // sanity range: 0.5 … 500 litres
        }
        return null
    }

    // ---------------------------------------------------------------------------
    //  Odometer extraction
    // ---------------------------------------------------------------------------

    private fun extractOdometer(text: String): Int? {
        val patterns = listOf(
            // Explicit label
            Regex("""(?:пробег|одометр|км пути|odometer)\s*:?\s*(\d{4,6})""", RegexOption.IGNORE_CASE),
            // Number followed by unit: "125840 км"
            Regex("""(\d{5,6})\s*(?:км|km)\b""", RegexOption.IGNORE_CASE),
            // 6-digit standalone number that looks like an odometer
            Regex("""(?<!\d)(\d{6})(?!\d)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].toIntOrNull() ?: continue
            if (value in 100..999_999) return value
        }
        return null
    }

    // ---------------------------------------------------------------------------
    //  Station name extraction
    //  Strategy: check known brand list first; then look for "АЗС" context; then
    //  fallback to the first non-trivial line of the receipt (station name is
    //  usually printed at the top).
    // ---------------------------------------------------------------------------

    private val knownStations = listOf(
        "Лукойл", "LUKOIL",
        "Газпромнефть", "Газпром",
        "Роснефть", "Rosneft",
        "BP",
        "Shell",
        "Татнефть",
        "Башнефть",
        "Сургутнефтегаз",
        "ЕКА", "EKA",
        "Neste",
        "Трасса",
        "Магна",
        "RusНефть", "Русснефть",
        "Опти",
        "Лавр",
        "G-Drive",
        "ПТК",
        "Eni",
        "Total",
        "Sunoco",
        "Сибнефть",
        "Альфа",
        "Феникс",
        "Кедр"
    )

    private fun extractStationName(text: String): String? {
        val upperText = text.uppercase()

        // 1. Known brand list (case-insensitive substring)
        knownStations.firstOrNull { upperText.contains(it.uppercase()) }?.let { return it }

        // 2. Look for pattern "АЗС №<N>" or "АЗС <Name>"
        val azsPattern = Regex("""АЗС\s+(?:№\s*\d+|[«"'"]?([А-ЯЁA-Z][А-ЯЁа-яёA-Za-z\s]{2,20})[»"'"]?)""")
        azsPattern.find(text)?.let { match ->
            val captured = match.groupValues[1].trim()
            if (captured.isNotBlank()) return "АЗС $captured"
            return match.value.trim().take(30)
        }

        // 3. First non-trivial line (likely the header / company name)
        val trivialLine = Regex("""^\s*(?:\d+|кассовый чек|фискальный|чек|receipt|кассир|итого|сумма)""", RegexOption.IGNORE_CASE)
        val firstMeaningfulLine = text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 3..40 &&
                !trivialLine.containsMatchIn(line) &&
                line.any { it.isLetter() }
            }
        return firstMeaningfulLine
    }

    // ---------------------------------------------------------------------------
    //  Fuel type extraction
    //  Use more specific patterns to avoid false positives from years / prices.
    // ---------------------------------------------------------------------------

    private fun extractFuelType(text: String): String? {
        // Ordered from most-specific to least-specific
        val patterns = listOf(
            Regex("""АИ[-–\s]?100""", RegexOption.IGNORE_CASE) to "АИ-100",
            Regex("""АИ[-–\s]?98|AI[-–\s]?98|Бензин[-–\s]98""", RegexOption.IGNORE_CASE) to "АИ-98",
            Regex("""АИ[-–\s]?95|AI[-–\s]?95|Бензин[-–\s]95|Премиум[-–\s]95""", RegexOption.IGNORE_CASE) to "АИ-95",
            Regex("""АИ[-–\s]?92|AI[-–\s]?92|Бензин[-–\s]92|Регуляр[-–\s]92""", RegexOption.IGNORE_CASE) to "АИ-92",
            Regex("""АИ[-–\s]?80|AI[-–\s]?80""", RegexOption.IGNORE_CASE) to "АИ-80",
            Regex("""дизел(?:ь|ьное)|ДТ\b|diesel|дт\b""", RegexOption.IGNORE_CASE) to "Дизель",
            Regex("""газ\b|LPG\b|CNG\b|метан|пропан""", RegexOption.IGNORE_CASE) to "Газ",
            // Generic "Бензин" fallback if no grade found
            Regex("""бензин""", RegexOption.IGNORE_CASE) to "Бензин"
        )

        for ((pattern, label) in patterns) {
            if (pattern.containsMatchIn(text)) return label
        }
        return null
    }

    // ---------------------------------------------------------------------------
    //  Photo helpers
    // ---------------------------------------------------------------------------

    /**
     * Сохранить фото чека из Bitmap
     */
    fun saveReceiptPhoto(bitmap: Bitmap, expenseId: Long): String {
        val receiptsDir = File(context.filesDir, "receipts")
        if (!receiptsDir.exists()) receiptsDir.mkdirs()

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
    @Suppress("DEPRECATION")
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
            if (file.exists()) BitmapFactory.decodeFile(photoPath) else null
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
