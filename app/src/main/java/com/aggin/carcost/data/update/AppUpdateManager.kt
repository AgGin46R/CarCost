package com.aggin.carcost.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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

        // URL приходит из строки app_config в Supabase. По http его можно
        // подменить на пути к устройству, поэтому принимаем только https.
        val parsedUrl = Uri.parse(apkUrl)
        if (!parsedUrl.scheme.equals("https", ignoreCase = true)) {
            Log.e(TAG, "Отклонено: download_url должен быть https, получено '${parsedUrl.scheme}'")
            toast("Обновление отклонено: небезопасная ссылка")
            return
        }

        val fileName = "carcost_update.apk"
        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(parsedUrl)
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
                if (id != downloadId) return
                try { context.unregisterReceiver(this) } catch (_: Exception) {}

                // ACTION_DOWNLOAD_COMPLETE приходит и при неудачной загрузке —
                // без этой проверки установка запускалась на битом файле
                val status = dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                    if (c != null && c.moveToFirst()) {
                        val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val st = if (statusIdx >= 0) c.getInt(statusIdx) else -1
                        if (st != DownloadManager.STATUS_SUCCESSFUL && reasonIdx >= 0) {
                            Log.e(TAG, "Загрузка не удалась, reason=${c.getInt(reasonIdx)}")
                        }
                        st
                    } else -1
                }

                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    toast("Не удалось скачать обновление")
                    dest.delete()
                    return
                }

                // Путь берём у DownloadManager: при коллизии имён он сохранит
                // файл как carcost_update-1.apk, и dest указывал бы не туда
                val downloadedFile = dm.getUriForDownloadedFile(downloadId)
                    ?.let { uri -> uri.path?.let(::File)?.takeIf { it.exists() } }
                    ?: dest

                if (!verifyApk(downloadedFile)) {
                    downloadedFile.delete()
                    return
                }

                installApk(downloadedFile)
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

    /**
     * Проверяет скачанный APK перед установкой.
     *
     * Ссылка на файл приходит из таблицы app_config: тот, кто может её изменить,
     * без этих проверок ставил бы пользователям произвольный пакет. Проверяем,
     * что это наше приложение, что версия действительно новее и что оно подписано
     * тем же ключом.
     */
    private fun verifyApk(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Проверка APK: файл отсутствует или пуст")
            toast("Файл обновления повреждён")
            return false
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

        val archiveInfo = try {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Проверка APK: не удалось прочитать пакет", e)
            null
        }

        if (archiveInfo == null) {
            toast("Файл обновления повреждён")
            return false
        }

        // Для чтения подписи из архива этим полям нужен путь к самому файлу
        archiveInfo.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

        if (archiveInfo.packageName != context.packageName) {
            Log.e(TAG, "Проверка APK: чужой пакет ${archiveInfo.packageName}")
            toast("Обновление отклонено: посторонний пакет")
            return false
        }

        val installed = pm.getPackageInfo(context.packageName, 0)
        val newCode = archiveInfo.longVersionCodeCompat()
        val currentCode = installed.longVersionCodeCompat()
        if (newCode <= currentCode) {
            Log.e(TAG, "Проверка APK: версия не новее ($newCode <= $currentCode)")
            toast("Обновление отклонено: версия не новее установленной")
            return false
        }

        if (!signaturesMatch(archiveInfo)) {
            Log.e(TAG, "Проверка APK: подпись не совпадает с установленным приложением")
            toast("Обновление отклонено: неверная подпись")
            return false
        }

        Log.d(TAG, "Проверка APK пройдена: версия $newCode")
        return true
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else @Suppress("DEPRECATION") versionCode.toLong()

    private fun signaturesMatch(archiveInfo: PackageInfo): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val installed = pm.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                val current = installed.signingInfo?.apkContentsSigners
                    ?.map { it.toCharsString() }?.toSet().orEmpty()
                val incoming = archiveInfo.signingInfo?.apkContentsSigners
                    ?.map { it.toCharsString() }?.toSet().orEmpty()
                current.isNotEmpty() && incoming.isNotEmpty() && current == incoming
            } else {
                @Suppress("DEPRECATION")
                val installed = pm.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                val current = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
                @Suppress("DEPRECATION")
                val incoming = archiveInfo.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
                current.isNotEmpty() && incoming.isNotEmpty() && current == incoming
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сравнить подписи", e)
            false
        }
    }

    private fun toast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
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
