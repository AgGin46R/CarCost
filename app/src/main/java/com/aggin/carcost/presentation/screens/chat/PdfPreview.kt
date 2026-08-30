package com.aggin.carcost.presentation.screens.chat

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Просмотр PDF внутри приложения.
 *
 * Почему только PDF: его умеет открывать сама система — PdfRenderer входит в
 * Android с восьмой версии и рисует страницы в картинки без единой сторонней
 * библиотеки. Для doc, docx и xlsx такого нет: пришлось бы либо тащить в APK
 * тяжёлую библиотеку, либо гонять документы через чужой веб-сервис — а
 * отправлять файлы пользователей наружу в приложении, которое ради хранения
 * данных в России переезжало на свой сервер, нельзя.
 *
 * Всё, что не PDF, по-прежнему открывается системой, тем приложением, которое
 * у человека установлено.
 */
private const val TAG = "PdfPreview"

/** Скачивает файл в кэш и отдаёт его. Повторно не качает. */
suspend fun downloadToCache(context: Context, url: String, fileName: String): File? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "downloads").also { it.mkdirs() }
            val file = File(dir, fileName)
            if (!file.exists() || file.length() == 0L) {
                file.writeBytes(URL(url).readBytes())
            }
            file
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Не удалось скачать $fileName: ${e.message}", e)
            null
        }
    }

/**
 * Отрисовывает страницы PDF в картинки.
 *
 * Ширина берётся с запасом под экран телефона: рисовать в исходном разрешении
 * документа расточительно по памяти, а мельче — текст становится нечитаемым.
 */
private suspend fun renderPdf(file: File, targetWidth: Int): List<Bitmap> =
    withContext(Dispatchers.IO) {
        val pages = mutableListOf<Bitmap>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = targetWidth.toFloat() / page.width
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                            // Белая подложка: PDF рисует только содержимое, и без
                            // заливки страница получится прозрачной, а на тёмной
                            // теме — чёрный текст на чёрном фоне
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pages.add(bmp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Не удалось разобрать PDF: ${e.message}", e)
        }
        pages
    }

/**
 * Окно просмотра PDF.
 *
 * [onOpenExternally] — запасной выход: если документ не разобрался или человеку
 * нужны возможности полноценной читалки, он открывает файл системой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    url: String,
    fileName: String,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        val file = downloadToCache(context, url, fileName)
        if (file == null) {
            failed = true
            return@LaunchedEffect
        }
        val rendered = renderPdf(file, targetWidth = 1080)
        if (rendered.isEmpty()) failed = true else pages = rendered
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
                    },
                    actions = {
                        IconButton(
                            onClick = { downloadAttachment(context, url, fileName, "file") }
                        ) {
                            Icon(Icons.Default.Download, stringResource(R.string.chat_sohranit_dokument))
                        }
                        IconButton(onClick = onOpenExternally) {
                            Icon(Icons.Default.OpenInNew, stringResource(R.string.chat_otkryt_v_drugom_prilozhenii))
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.chat_ne_udalos_pokazat_dokument))
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onOpenExternally) {
                            Text(stringResource(R.string.chat_otkryt_drugim_prilozheniem))
                        }
                    }

                    pages == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.chat_zagruzhaem_dokument), style = MaterialTheme.typography.bodySmall)
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pages!!) { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }
}
