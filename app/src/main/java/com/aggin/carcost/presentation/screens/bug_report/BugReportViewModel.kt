package com.aggin.carcost.presentation.screens.bug_report

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class BugReportUiState(
    val userDescription: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@Serializable
data class BugReport(
    val userId: String,
    val userEmail: String,
    val userName: String,
    val timestamp: String,
    val description: String,
    val deviceInfo: DeviceInfo,
    val appLogs: List<String>
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val appVersion: String
)

class BugReportViewModel(application: Application) : AndroidViewModel(application) {

    private val supabaseAuth = SupabaseAuthRepository()
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(BugReportUiState())
    val uiState: StateFlow<BugReportUiState> = _uiState.asStateFlow()

    fun updateDescription(text: String) {
        _uiState.value = _uiState.value.copy(userDescription = text, errorMessage = null)
    }

    fun submitBugReport() {
        val description = _uiState.value.userDescription.trim()

        if (description.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Пожалуйста, опишите проблему")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Собираем информацию о пользователе
                val userId = supabaseAuth.getUserId() ?: "anonymous"
                val userEmail = supabaseAuth.getCurrentUserEmail() ?: "not_available"
                val userName = supabaseAuth.getCurrentUserDisplayName() ?: "Unknown User"

                // Собираем логи приложения
                val logs = collectAppLogs()

                // Создаем отчет
                val bugReport = BugReport(
                    userId = userId,
                    userEmail = userEmail,
                    userName = userName,
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    description = description,
                    deviceInfo = DeviceInfo(
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        sdkVersion = Build.VERSION.SDK_INT,
                        appVersion = getAppVersion()
                    ),
                    appLogs = logs
                )

                // Конвертируем в JSON
                val jsonReport = Json.encodeToString(bugReport)

                // Загружаем в Supabase Storage
                uploadToSupabase(userId, jsonReport)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                }

            } catch (e: Exception) {
                Log.e("BugReportViewModel", "Error submitting bug report", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Ошибка отправки отчета: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun uploadToSupabase(userId: String, jsonReport: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "bug_reports/$userId/report_$timestamp.json"

            val bucket = supabase.storage.from("bug-reports")

            // Загружаем JSON как байты
            bucket.upload(
                path = fileName,
                data = jsonReport.toByteArray(Charsets.UTF_8),
                upsert = false
            )

            Log.d("BugReportViewModel", "Bug report uploaded successfully: $fileName")

        } catch (e: Exception) {
            Log.e("BugReportViewModel", "Error uploading to Supabase", e)
            throw e
        }
    }

    private fun collectAppLogs(): List<String> {
        val logs = mutableListOf<String>()
        try {
            val twoHoursAgoMs = System.currentTimeMillis() - 2 * 60 * 60 * 1000L

            // Фильтруем по тегам наших классов — работает даже после краша (PID мог смениться).
            // AndroidRuntime:E — захватывает stack trace при падении приложения.
            // -T ms — логи за последние 2 часа.
            val tags = arrayOf(
                "CarCostApp:V",
                "RealtimeSync:V",
                "BackgroundSyncWorker:V",
                "GpsTripService:V",
                "ProfileViewModel:V",
                "ChatViewModel:V",
                "SupabaseCarMembers:V",
                "BugReportViewModel:V",
                "FcmTokenManager:V",
                "CarCostFCM:V",
                "AndroidRuntime:E",   // крэш stack trace
                "*:S"                 // всё остальное — молчать
            )

            val cmd = arrayOf("logcat", "-d", "-T", twoHoursAgoMs.toString(), "-v", "time", "-s") + tags
            val process = Runtime.getRuntime().exec(cmd)

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { logs.add(it) }
            }
            reader.close()
            process.waitFor()

            // Fallback: если тегов нет — собираем по нашему package name из общего лога
            if (logs.size <= 2) {
                logs.clear()
                val fallbackProcess = Runtime.getRuntime().exec(arrayOf(
                    "logcat", "-d", "-T", twoHoursAgoMs.toString(), "-v", "time"
                ))
                val fbReader = BufferedReader(InputStreamReader(fallbackProcess.inputStream))
                while (fbReader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.contains("carcost", ignoreCase = true) ||
                        l.contains("aggin", ignoreCase = true)) {
                        logs.add(l)
                    }
                }
                fbReader.close()
                fallbackProcess.waitFor()
            }

        } catch (e: Exception) {
            Log.e("BugReportViewModel", "Error collecting logs", e)
            logs.add("[Ошибка сбора логов: ${e.message}]")
        }
        return logs.takeLast(3000)
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun resetState() {
        _uiState.value = BugReportUiState()
    }
}