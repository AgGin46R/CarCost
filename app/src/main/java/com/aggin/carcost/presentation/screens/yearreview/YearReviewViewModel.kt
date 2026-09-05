package com.aggin.carcost.presentation.screens.yearreview

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.domain.year.YearSummaryCalculator
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class YearReviewUiState(
    val isLoading: Boolean = true,
    val car: Car? = null,
    val summary: YearSummaryCalculator.YearSummary? = null,
    /** Годы, за которые вообще есть записи — по убыванию */
    val availableYears: List<Int> = emptyList(),
    val error: String? = null
)

class YearReviewViewModel(
    application: Application,
    private val carId: String,
    initialYear: Int?
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(YearReviewUiState())
    val uiState: StateFlow<YearReviewUiState> = _uiState.asStateFlow()

    init {
        load(initialYear ?: defaultYear())
    }

    /**
     * Год по умолчанию.
     *
     * В январе человеку интересен прошлый год — он только что закончился, и
     * итогов текущего ещё не существует. С февраля показывать прошлый год
     * странно, но текущий пуст ровно один месяц в году, и промахнуться в
     * январе неприятнее.
     */
    private fun defaultYear(): Int {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        return if (cal.get(Calendar.MONTH) == Calendar.JANUARY) year - 1 else year
    }

    fun load(year: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val car = db.carDao().getCarById(carId)
                if (car == null) {
                    _uiState.value = YearReviewUiState(isLoading = false)
                    return@launch
                }
                val expenses = db.expenseDao().getExpensesByCarIdSync(carId)
                val trips = db.gpsTripDao().getTripsByCarIdSync(carId)

                val years = expenses.map {
                    Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.YEAR)
                }.distinct().sortedDescending()

                _uiState.value = YearReviewUiState(
                    isLoading = false,
                    car = car,
                    summary = YearSummaryCalculator.calculate(expenses, trips, year),
                    availableYears = years
                )
            } catch (e: Exception) {
                Log.e(TAG, "Итоги года не собрались", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    /**
     * Отправляет страницу картинкой.
     *
     * Картинкой, а не текстом: итоги — это страница, её отправляют в чат и
     * выкладывают, а текстовая простыня цифр никому не интересна. Снимок
     * делает экран через GraphicsLayer, сюда приходит уже готовое изображение.
     */
    fun share(image: ImageBitmap, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { writeImage(image) }
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(
                    intent,
                    getApplication<Application>().getString(R.string.yearreview_share)
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                getApplication<Application>().startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Картинка не отправилась", e)
                onError(e.message ?: "")
            }
        }
    }

    private fun writeImage(image: ImageBitmap): File {
        val dir = File(getApplication<Application>().cacheDir, "year_review").apply { mkdirs() }
        // Имя постоянное: файл нужен ровно на время показа системного диалога,
        // и складывать в кеш по копии на каждое нажатие незачем
        val file = File(dir, "year_review.png")
        FileOutputStream(file).use { out ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    companion object {
        private const val TAG = "YearReviewVM"
    }
}
