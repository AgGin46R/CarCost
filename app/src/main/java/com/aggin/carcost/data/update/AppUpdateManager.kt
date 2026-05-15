package com.aggin.carcost.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.aggin.carcost.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/** Информация об обновлении, полученная из Supabase-таблицы app_config */
@Serializable
data class VersionInfo(
    @SerialName("version_code")  val versionCode: Int,
    @SerialName("version_name")  val versionName: String,
    @SerialName("download_url")  val apkUrl: String,
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("force_update")  val forceUpdate: Boolean = false
)

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
    }

    /**
     * Проверяет наличие обновления через таблицу app_config в Supabase.
     * Возвращает [VersionInfo] если доступна новая версия, иначе null.
     */
    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val info = supabase
                .from("app_config")
                .select { filter { eq("id", "android") } }
                .decodeSingle<VersionInfo>()

            val currentCode = context.packageManager
                .getPackageInfo(context.packageName, 0).let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        it.longVersionCode.toInt()
                    else
                        @Suppress("DEPRECATION") it.versionCode
                }

            Log.d(TAG, "Текущая: $currentCode, Удалённая: ${info.versionCode}")
            if (info.versionCode > currentCode) info else null
        } catch (e: Exception) {
            Log.w(TAG, "Проверка обновлений не удалась: ${e.message}")
            null
        }
    }

    /**
     * Скачивает APK через системный DownloadManager и запускает установку по завершении.
     * Для скачивания нужно разрешение REQUEST_INSTALL_PACKAGES (уже есть в манифесте).
     */
    fun downloadAndInstall(apkUrl: String) {
        if (apkUrl.isBlank()) {
            Log.w(TAG, "APK URL пустой — скачивание пропущено")
            return
        }

        val fileName = "carcost_update.apk"
        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("CarCost — загрузка обновления")
            .setDescription("Подождите, идёт загрузка...")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, fileName
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(dest)
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
