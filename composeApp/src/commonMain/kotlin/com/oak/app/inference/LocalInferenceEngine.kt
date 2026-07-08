package com.oak.app.inference

import kotlinx.coroutines.flow.StateFlow

data class LocalModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val gpuMemoryMb: Int,
    val defaultContextTokens: Int,
    val maxContextTokens: Int,
    val kvPerTokenBytes: Int,
    val isRecommended: Boolean = false,
)

enum class DevicePerformance {
    GOOD,
    OK,
    POOR,
}

fun estimateGpuMemoryMb(model: LocalModel, contextTokens: Int): Int {
    val modelFileMb = (model.sizeBytes / (1024 * 1024)).toInt()
    val extraTokens = contextTokens - model.defaultContextTokens
    val extraMemoryMb = (extraTokens.toLong() * model.kvPerTokenBytes) / (1024 * 1024)
    return modelFileMb + model.gpuMemoryMb + extraMemoryMb.toInt()
}

fun calculateDevicePerformance(totalMemoryBytes: Long, estimatedGpuMemoryMb: Int): DevicePerformance {
    val gpuMemoryBytes = estimatedGpuMemoryMb.toLong() * 1024 * 1024
    val ratio = totalMemoryBytes.toDouble() / gpuMemoryBytes
    return when {
        ratio >= 2.5 -> DevicePerformance.GOOD
        ratio >= 1.85 -> DevicePerformance.OK
        else -> DevicePerformance.POOR
    }
}

data class DownloadedModel(
    val id: String,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long,
)

enum class EngineState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    ERROR,
}

data class InferenceMessage(
    val role: String,
    val content: String,
)

/**
 * A tool definition handed to the on-device inference engine.
 *
 * @param name the tool's identifier as the model will see it
 * @param descriptionJsonString a complete OpenAPI/OpenAI-style JSON object describing the
 *        tool, e.g. `{"name":"get_time","description":"...","parameters":{"type":"object",...}}`
 * @param execute receives the JSON arguments object as a string and returns the
 *        JSON-encoded result string
 */
data class LocalTool(
    val name: String,
    val descriptionJsonString: String,
    val execute: suspend (jsonArgs: String) -> String,
)

class InsufficientMemoryException : Exception()
class InferenceTimeoutException : Exception()
class NoModelDownloadedException : Exception()

enum class DownloadError {
    NOT_ENOUGH_DISK_SPACE,
    NETWORK_ERROR,
    DOWNLOAD_INCOMPLETE,
}

/** User-configurable sampling parameters for local inference. */
data class SamplerParams(
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.8f,
)

/** Metrics for the most recent generation, surfaced in the UI. */
data class GenerationMetrics(
    val outputCharCount: Int,
    val tokensPerSecond: Float,
    val durationMs: Long,
) {
    /** Rough token count estimate (chars / 4). */
    val estimatedTokenCount: Int get() = outputCharCount / 4
}

interface LocalInferenceEngine {
    val engineState: StateFlow<EngineState>
    val downloadingModelId: StateFlow<String?>
    val downloadProgress: StateFlow<Float?>
    val downloadError: StateFlow<DownloadError?>

    val currentModelId: String?

    /** "gpu" or "cpu" — which backend is running inference, or null before init. */
    val activeBackend: StateFlow<String?>

    /** "auto", "gpu", or "cpu" — the backend preference used at last init. */
    val currentBackendPref: String

    /** Live streaming content tokens during an in-progress chat() call. */
    val streamingContent: StateFlow<String?>

    /** Live streaming reasoning tokens during an in-progress chat() call. */
    val streamingReasoning: StateFlow<String?>

    /** Metrics for the most recently completed generation, or null if none. */
    val lastGenerationMetrics: StateFlow<GenerationMetrics?>

    suspend fun initialize(model: DownloadedModel, contextTokens: Int = 0, backendPreference: String = "auto")
    suspend fun release()

    /**
     * Fire-and-forget release, run on the engine's own coroutine scope. Called from
     * non-suspend contexts (e.g. Settings UI when the user picks a different model) so
     * the GPU driver has time to reclaim memory before the next inference.
     */
    fun releaseInBackground()

    suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool> = emptyList(),
        samplerParams: SamplerParams = SamplerParams(),
    ): String

    fun getDownloadedModels(): List<DownloadedModel>
    fun getAvailableModels(): List<LocalModel>
    fun getFreeSpaceBytes(): Long
    fun startDownload(model: LocalModel)
    fun cancelDownload()
    suspend fun deleteModel(modelId: String)
}
