package com.aggin.carcost.presentation.screens.chat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast

private const val TAG = "MediaDownload"

/**
 * Сохранение вложения на устройство.
 *
 * Сделано через системный загрузчик, а не своей загрузкой в поток. Причины
 * практические: он показывает уведомление с ходом загрузки, продолжает работу,
 * когда приложение свернули, сам докачивает при обрыве связи и кладёт файл
 * туда, где его увидят галерея и проводник. Своими руками это всё пришлось бы
 * написать заново и хуже.
 *
 * Разрешения не нужны: файл пишется в общую папку через системную службу.
 * WRITE_EXTERNAL_STORAGE в манифесте объявлен только для Android 9 и старше и
 * к этому пути отношения не имеет.
 */
fun downloadAttachment(
    context: Context,
    url: String,
    fileName: String?,
    mediaType: String?
) {
    if (url.isBlank()) {
        Toast.makeText(context, "Файл недоступен", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val name = safeFileName(url, fileName, mediaType)

        // Раскладываем по назначению: снимки и ролики должны попадать в галерею,
        // документы — в загрузки. В одну кучу складывать неудобно.
        val directory = when (mediaType) {
            "video" -> Environment.DIRECTORY_MOVIES
            "file" -> Environment.DIRECTORY_DOWNLOADS
            else -> Environment.DIRECTORY_PICTURES
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("CarCost")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(directory, name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)

        Toast.makeText(context, "Загружаю «$name»…", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Поставлено в очередь: $name → $directory")
    } catch (e: Exception) {
        // Молчаливый отказ здесь недопустим: человек нажал «Скачать» и вправе
        // знать, что ничего не вышло
        Log.e(TAG, "Не удалось поставить загрузку: ${e.message}", e)
        Toast.makeText(context, "Не удалось скачать: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Имя, под которым файл ляжет на устройство.
 *
 * Берём имя из сообщения, если оно есть. Для снимков и роликов его нет — там
 * в адресе лежит случайный идентификатор, и «a3f9c1e2-…jpg» в галерее выглядит
 * мусором. Поэтому им даётся понятное имя с порядковым номером по времени.
 *
 * Символы, недопустимые в именах файлов, заменяются: иначе системная служба
 * молча откажется от загрузки.
 */
private fun safeFileName(url: String, fileName: String?, mediaType: String?): String {
    val fromMessage = fileName?.takeIf { it.isNotBlank() }
    if (fromMessage != null) return sanitize(fromMessage)

    val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        .takeIf { !it.isNullOrBlank() }
        ?: when (mediaType) {
            "video" -> "mp4"
            else -> "jpg"
        }

    val stamp = System.currentTimeMillis()
    val prefix = if (mediaType == "video") "CarCost_video" else "CarCost_photo"
    return "${prefix}_$stamp.$extension"
}

private fun sanitize(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
