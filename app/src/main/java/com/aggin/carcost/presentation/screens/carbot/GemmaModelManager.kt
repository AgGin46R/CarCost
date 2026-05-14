package com.aggin.carcost.presentation.screens.carbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages downloading and deleting the on-device Gemma CPU model file.
 * The model is downloaded automatically from [MODEL_URL] (GitHub Releases).
 */
class GemmaModelManager(private val filesDir: File) {

    val modelFile: File
        get() = File(filesDir, "models/gemma_model.bin")

    val isDownloaded: Boolean
        get() = modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES

    fun deleteModel() {
        modelFile.delete()
    }

    suspend fun downloadModel(
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (MODEL_URL.isBlank()) {
            withContext(Dispatchers.Main) { onError("URL модели не задан") }
            return@withContext
        }
        try {
            modelFile.parentFile?.mkdirs()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(MODEL_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onError("HTTP ${response.code}: ${response.message}")
                    }
                    return@withContext
                }

                val body = response.body
                    ?: run { withContext(Dispatchers.Main) { onError("Пустой ответ сервера") }; return@withContext }

                val total = body.contentLength()
                var downloaded = 0L

                FileOutputStream(modelFile).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(65_536)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            out.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                withContext(Dispatchers.Main) { onProgress(pct) }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) { onComplete() }
            }
        } catch (e: Exception) {
            if (modelFile.exists()) modelFile.delete()
            withContext(Dispatchers.Main) { onError(e.message ?: "Ошибка загрузки") }
        }
    }

    companion object {
        /**
         * GitHub Releases URL for the CPU model (gemma-2b-it-cpu-int4).
         * Works on ALL Android devices, no GPU/OpenCL required.
         * Upload gemma-2b-it-cpu-int4.bin to GitHub Releases and paste the link here.
         */
        const val MODEL_URL = "https://github.com/AgGin46R/CarCost/releases/download/ai_model_v1/gemma-2b-it-cpu-int4.bin"   // ← вставь ссылку из GitHub Releases

        val hasDirectUrl get() = MODEL_URL.isNotBlank()

        /** 800 MB — sanity check */
        private const val MIN_MODEL_SIZE_BYTES = 800_000_000L
    }
}
