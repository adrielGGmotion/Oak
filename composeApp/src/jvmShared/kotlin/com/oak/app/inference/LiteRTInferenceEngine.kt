package com.oak.app.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

val MODEL_CATALOG = listOf(
    LocalModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        gpuMemoryMb = 676,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 50_000,
        isRecommended = true,
    ),
    LocalModel(
        id = "gemma-4-e4b-it",
        displayName = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        gpuMemoryMb = 710,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 75_000,
    ),
    LocalModel(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        sizeBytes = 614_236_160L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        gpuMemoryMb = 300,
        defaultContextTokens = 4_096,
        maxContextTokens = 8_192,
        kvPerTokenBytes = 35_000,
    ),
    LocalModel(
        id = "gemma3-1b-it",
        displayName = "Gemma 3 1B IT",
        fileName = "gemma3-1b-it-int4.litertlm",
        sizeBytes = 554_661_246L,
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        gpuMemoryMb = 350,
        defaultContextTokens = 2_048,
        maxContextTokens = 4_096,
        kvPerTokenBytes = 25_000,
    ),
    LocalModel(
        id = "gemma-3n-e2b-it",
        displayName = "Gemma 3n E2B IT",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        sizeBytes = 3_655_827_456L,
        downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        gpuMemoryMb = 500,
        defaultContextTokens = 4_096,
        maxContextTokens = 4_096,
        kvPerTokenBytes = 40_000,
    ),
    LocalModel(
        id = "gemma-3n-e4b-it",
        displayName = "Gemma 3n E4B IT",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        sizeBytes = 4_919_541_760L,
        downloadUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm",
        gpuMemoryMb = 600,
        defaultContextTokens = 4_096,
        maxContextTokens = 4_096,
        kvPerTokenBytes = 60_000,
    ),
    LocalModel(
        id = "qwen2.5-1.5b-instruct",
        displayName = "Qwen2.5 1.5B Instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_597_931_520L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        gpuMemoryMb = 350,
        defaultContextTokens = 4_096,
        maxContextTokens = 4_096,
        kvPerTokenBytes = 30_000,
    ),
    LocalModel(
        id = "deepseek-r1-distill-qwen-1.5b",
        displayName = "DeepSeek R1 Distill Qwen 1.5B",
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_833_451_520L,
        downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        gpuMemoryMb = 380,
        defaultContextTokens = 4_096,
        maxContextTokens = 4_096,
        kvPerTokenBytes = 30_000,
    ),
)

class LiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var idleReleaseJob: Job? = null

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    override var currentModelId: String? = null
        private set
    override var currentBackendPref: String = "auto"
        private set
    private var currentContextTokens: Int = 0

    // Conversation tracking — avoids closing + recreating on every turn.
    private var lastSystemPrompt: String? = null
    private var sentMessageCount: Int = 0

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<DownloadError?>(null)
    override val downloadError: StateFlow<DownloadError?> = _downloadError

    private val _activeBackend = MutableStateFlow<String?>(null)
    override val activeBackend: StateFlow<String?> = _activeBackend

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int, backendPreference: String) {
        withContext(Dispatchers.IO) {
            idleReleaseJob?.cancel()
            if (currentModelId == model.id && currentContextTokens == contextTokens && currentBackendPref == backendPreference && _engineState.value == EngineState.READY) return@withContext
            _engineState.value = EngineState.INITIALIZING
            try {
                val modelFile = File(model.filePath)
                if (!modelFile.exists() || modelFile.length() < 1_000_000) {
                    throw IllegalStateException("Model file missing or too small: ${model.filePath}")
                }

                val hadExistingEngine = engine != null
                release()
                _engineState.value = EngineState.INITIALIZING

                if (hadExistingEngine) {
                    System.gc()
                    delay(GPU_DRAIN_DELAY_MS.milliseconds)
                }

                val availMem = getAvailableMemoryBytes()
                if (availMem < MIN_MEMORY_HEADROOM_BYTES) {
                    throw InsufficientMemoryException()
                }

                fun initWithBackend(backend: Backend, maxTokens: Int?): Engine {
                    val config = EngineConfig(
                        modelPath = model.filePath,
                        backend = backend,
                        cacheDir = getModelCacheDirectory(),
                        maxNumTokens = maxTokens,
                    )
                    val e = Engine(config)
                    e.initialize()
                    return e
                }

                val requestedTokens = if (contextTokens > 0) contextTokens else null
                println("LiteRT: initializing model=${model.id} maxNumTokens=$requestedTokens backendPref=$backendPreference")

                val newEngine = when (backendPreference) {
                    "gpu" -> {
                        try {
                            initWithBackend(Backend.GPU(), requestedTokens).also { _activeBackend.value = "gpu" }
                        } catch (e: Exception) {
                            println("LiteRT: GPU init failed (${e.message}), cannot fall back because user chose GPU")
                            throw e
                        }
                    }
                    "cpu" -> {
                        initWithBackend(Backend.CPU(), requestedTokens).also { _activeBackend.value = "cpu" }
                    }
                    else -> {
                        try {
                            try {
                                initWithBackend(Backend.GPU(), requestedTokens).also { _activeBackend.value = "gpu" }
                            } catch (e: Exception) {
                                println("LiteRT: GPU init failed (${e.message}), falling back to CPU")
                                initWithBackend(Backend.CPU(), requestedTokens).also { _activeBackend.value = "cpu" }
                            }
                        } catch (e: Exception) {
                            println("LiteRT: init failed with maxNumTokens=$requestedTokens, falling back to default: ${e.message}")
                            if (requestedTokens != null) {
                                try {
                                    initWithBackend(Backend.GPU(), null).also { _activeBackend.value = "gpu" }
                                } catch (e2: Exception) {
                                    println("LiteRT: GPU init with default tokens failed too, falling back to CPU")
                                    initWithBackend(Backend.CPU(), null).also { _activeBackend.value = "cpu" }
                                }
                            } else {
                                throw e
                            }
                        }
                    }
                }

                engine = newEngine
                conversation = null
                currentModelId = model.id
                currentContextTokens = contextTokens
                currentBackendPref = backendPreference
                sentMessageCount = 0
                lastSystemPrompt = null
                _engineState.value = EngineState.READY
            } catch (e: Exception) {
                _activeBackend.value = null
                _engineState.value = EngineState.ERROR
                throw e
            }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            val convToClose = conversation
            val engineToClose = engine
            conversation = null
            engine = null
            currentModelId = null
            currentBackendPref = "auto"
            sentMessageCount = 0
            lastSystemPrompt = null
            _activeBackend.value = null
            _engineState.value = EngineState.UNINITIALIZED
            runCatching { convToClose?.close() }
            runCatching { engineToClose?.close() }
        }
    }

    override fun releaseInBackground() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch { release() }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
    ): String = withContext(Dispatchers.IO) {
        idleReleaseJob?.cancel()
        try {
            val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")

            val sanitizedSP = sanitizeForLiteRt(systemPrompt)

            val needsReset = conversation == null ||
                sanitizedSP != lastSystemPrompt ||
                sentMessageCount != messages.size

            if (needsReset) {
                buildConversation(currentEngine, messages, sanitizedSP)
            }

            val conv = conversation ?: throw IllegalStateException("Conversation not initialized")

            // Send any new user messages that arrived since the last call.
            // The Conversation already holds the prior exchanges internally.
            val newMessages = messages.drop(sentMessageCount)
            if (newMessages.isEmpty()) {
                // Should not happen, but guard against it.
                val fallback = messages.lastOrNull { it.role == "user" }?.content ?: ""
                val content = sanitizeForLiteRt(fallback) ?: ""
                return@withContext withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) { conv.sendMessage(content).toString() }
            }

            var lastResponse = ""
            for (msg in newMessages) {
                val content = sanitizeForLiteRt(msg.content) ?: ""
                if (msg.role == "user") {
                    lastResponse = withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                        conv.sendMessage(content).toString()
                    }
                }
                // "assistant" role messages are the model's own prior responses —
                // they're already in the Conversation's internal state from the
                // previous `sendMessage()` call, so we skip them here.
            }
            sentMessageCount = messages.size
            lastResponse
        } catch (e: TimeoutCancellationException) {
            throw InferenceTimeoutException()
        } finally {
            scheduleIdleRelease()
        }
    }

    private fun buildConversation(
        engine: Engine,
        messages: List<InferenceMessage>,
        systemPrompt: String?,
    ) {
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        val initialMessages = if (lastUserIndex > 0) {
            messages.subList(0, lastUserIndex).map { msg ->
                val sanitized = sanitizeForLiteRt(msg.content) ?: ""
                when (msg.role) {
                    "user" -> Message.user(sanitized)
                    else -> Message.model(sanitized)
                }
            }
        } else emptyList()

        val config = ConversationConfig(
            systemInstruction = systemPrompt?.let { Contents.of(it) },
            initialMessages = initialMessages,
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
            automaticToolCalling = false,
        )

        runCatching { conversation?.close() }
        conversation = engine.createConversation(config)
        sentMessageCount = if (lastUserIndex >= 0) lastUserIndex else messages.size
        lastSystemPrompt = systemPrompt
    }

    private fun sanitizeForLiteRt(s: String?): String? {
        if (s == null) return null
        if (s.none { it.isSurrogate() }) return s
        return s.filter { !it.isSurrogate() }
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS.milliseconds)
            release()
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000
        private const val INFERENCE_TIMEOUT_MS = 120_000L
        private const val MIN_MEMORY_HEADROOM_BYTES = 512L * 1024 * 1024
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024
        private const val GPU_DRAIN_DELAY_MS = 750L
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = File(getModelStorageDirectory())
        if (!modelsDir.exists()) return emptyList()
        return MODEL_CATALOG.mapNotNull { catalogModel ->
            val modelDir = File(modelsDir, catalogModel.id)
            val modelFile = File(modelDir, catalogModel.fileName)
            if (modelFile.exists()) {
                DownloadedModel(
                    id = catalogModel.id,
                    displayName = catalogModel.displayName,
                    filePath = modelFile.absolutePath,
                    sizeBytes = modelFile.length(),
                )
            } else {
                null
            }
        }
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
        cancelDownload()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null
            var tempFile: File? = null
            var notificationStarted = false

            try {
                val modelsDir = getModelStorageDirectory()
                val modelDir = File(modelsDir, model.id)
                modelDir.mkdirs()
                val targetFile = File(modelDir, model.fileName)
                tempFile = File(modelDir, "${model.fileName}.tmp")
                var lastNotifiedPercent = -1

                val freeSpace = getFreeSpaceBytes()
                if (freeSpace < model.sizeBytes + DOWNLOAD_SPACE_BUFFER_BYTES) {
                    _downloadError.value = DownloadError.NOT_ENOUGH_DISK_SPACE
                    return@launch
                }

                @Suppress("DEPRECATION")
                val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Download failed: HTTP $responseCode")
                }

                startDownloadNotificationService()
                notificationStarted = true

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
                val buffer = ByteArray(65536)
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val percent = (totalBytesRead * 100 / contentLength).toInt().coerceIn(1, 100)
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                _downloadProgress.value = percent / 100f
                                updateDownloadNotificationProgress(percent)
                            }
                        }
                    }
                }
                connection.disconnect()

                val downloadedSize = tempFile.length()
                if (downloadedSize < contentLength * 0.95) {
                    tempFile.delete()
                    throw IOException("Download incomplete: got $downloadedSize bytes, expected ~$contentLength")
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Throwable) {
                if (tempFile?.exists() == true) tempFile.delete()
                if (e is CancellationException) throw e
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
                if (notificationStarted) stopDownloadNotificationService()
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            idleReleaseJob?.cancelAndJoin()
            idleReleaseJob = null
            if (currentModelId == modelId) {
                release()
            }
            val modelDir = File(getModelStorageDirectory(), modelId)
            modelDir.deleteRecursively()
        }
    }
}
