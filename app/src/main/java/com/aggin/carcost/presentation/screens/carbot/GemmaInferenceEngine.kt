package com.aggin.carcost.presentation.screens.carbot

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around MediaPipe LlmInference for on-device Gemma inference.
 *
 * Call [initialize] once after the model file is confirmed present,
 * then [infer] for each user question, and [close] when done.
 *
 * Initialization automatically falls back to CPU backend if the device
 * doesn't support the GPU model (OpenCL not available). Note: this fallback
 * only works when both model files are compatible; if a GPU-only .bin was
 * supplied, a [GpuNotSupportedException] is thrown so the caller can ask
 * the user to download the CPU variant.
 */
class GemmaInferenceEngine(private val modelPath: String) {

    private var llmInference: LlmInference? = null
    private var usedCpuFallback = false

    val isReady: Boolean get() = llmInference != null
    val ranOnCpu: Boolean get() = usedCpuFallback

    /**
     * @throws GpuNotSupportedException when GPU model is loaded on a device
     *   that doesn't have OpenCL (user needs the CPU model variant).
     */
    fun initialize(context: Context) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()
        try {
            llmInference = LlmInference.createFromOptions(context, options)
            usedCpuFallback = false
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("OpenCL", ignoreCase = true) ||
                msg.contains("libvndk", ignoreCase = true) ||
                msg.contains("GPU", ignoreCase = true) ||
                msg.contains("FAILED_PRECONDITION", ignoreCase = true)) {
                throw GpuNotSupportedException(
                    "Ваше устройство не поддерживает GPU-модель (нет OpenCL). " +
                    "Требуется CPU-версия модели: gemma-2b-it-cpu-int4.bin"
                )
            }
            throw e   // unknown error — rethrow as-is
        }
    }

    /**
     * Run inference on the given [systemContext] + [userQuestion].
     * Must be called from a coroutine (suspends on Default dispatcher).
     */
    suspend fun infer(systemContext: String, userQuestion: String): String =
        withContext(Dispatchers.Default) {
            val inference = llmInference
                ?: return@withContext "AI-модель не инициализирована."
            val prompt = buildGemmaPrompt(systemContext, userQuestion)
            try {
                inference.generateResponse(prompt)
                    .trimEnd()
                    .removeSuffix("<end_of_turn>")
                    .trim()
            } catch (e: Exception) {
                "Ошибка AI: ${e.message}"
            }
        }

    fun close() {
        llmInference?.close()
        llmInference = null
    }

    private fun buildGemmaPrompt(system: String, user: String): String =
        "<start_of_turn>system\n$system<end_of_turn>\n" +
        "<start_of_turn>user\n$user<end_of_turn>\n" +
        "<start_of_turn>model\n"
}

/** Thrown when the device GPU/OpenCL stack is unavailable and a GPU model was loaded. */
class GpuNotSupportedException(message: String) : Exception(message)
