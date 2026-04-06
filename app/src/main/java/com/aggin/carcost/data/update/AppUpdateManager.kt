package com.aggin.carcost.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

@Serializable
data class VersionInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("release_notes") val releaseNotes: String = ""
)

class AppUpdateManager(private val context: Context) {

    companion object {
        // Public URL to version.json in Supabase Storage bucket "releases"
        private const val VERSION_JSON_URL =
            "https://mkwwidzaovxosnhsjomy.supabase.co/storage/v1/object/public/releases/version.json"
        private const val TAG = "AppUpdateManager"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns [VersionInfo] if a newer version is available, null otherwise. */
    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val raw = URL(VERSION_JSON_URL).readText()
            val info = json.decodeFromString<VersionInfo>(raw)
            val currentCode = context.packageManager
                .getPackageInfo(context.packageName, 0).let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        it.longVersionCode.toInt()
                    else
                        @Suppress("DEPRECATION") it.versionCode
                }
            Log.d(TAG, "Current: $currentCode, Remote: ${info.versionCode}")
            if (info.versionCode > currentCode) info else null
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    /** Downloads APK via system DownloadManager and installs when done. */
    fun downloadAndInstall(apkUrl: String) {
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
