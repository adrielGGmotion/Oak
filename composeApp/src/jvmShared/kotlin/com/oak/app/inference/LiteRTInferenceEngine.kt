package com.oak.app.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private var lastToolCount: Int = 0
    private var lastSamplerParams: SamplerParams? = null
    private var sentUserMessageCount: Int = 0

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

    private val _streamingContent = MutableStateFlow<String?>(null)
    override val streamingContent: StateFlow<String?> = _streamingContent

    private val _streamingReasoning = MutableStateFlow<String?>(null)
    override val streamingReasoning: StateFlow<String?> = _streamingReasoning

    private val _lastGenerationMetrics = MutableStateFlow<GenerationMetrics?>(null)
    override val lastGenerationMetrics: StateFlow<GenerationMetrics?> = _lastGenerationMetrics

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

                release()
                _engineState.value = EngineState.INITIALIZING

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
                sentUserMessageCount = 0
                lastSystemPrompt = null
                lastToolCount = 0
                lastSamplerParams = null
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
            sentUserMessageCount = 0
            lastSystemPrompt = null
            lastToolCount = 0
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

    @OptIn(ExperimentalApi::class)
    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
        samplerParams: SamplerParams,
    ): String = withContext(Dispatchers.IO) {
        try {
            val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")

            val sanitizedSP = sanitizeForLiteRt(systemPrompt)

            // Convert Oak tools to litertlm ToolProviders for native tool calling
            val toolProviders = if (tools.isNotEmpty()) {
                tools.map { localTool -> tool(LocalToolAdapter(localTool)) }
            } else {
                emptyList()
            }

            // Only rebuild conversation when truly necessary:
            // - No conversation exists
            // - System prompt changed
            // - Tool set changed
            // - Sampler params changed (rebuild with new SamplerConfig)
            // - User messages decreased (regeneration, deletion, or compaction)
            // Don't rebuild when messages grow — litertlm handles multi-turn internally
            val needsReset = conversation == null ||
                sanitizedSP != lastSystemPrompt ||
                toolProviders.size != lastToolCount ||
                samplerParams != lastSamplerParams ||
                messages.count { it.role == "user" } <= sentUserMessageCount

            if (needsReset) {
                buildConversation(currentEngine, messages, sanitizedSP, toolProviders, samplerParams)
            }

            val conv = conversation ?: throw IllegalStateException("Conversation not initialized")

            // Find new user messages since last call.
            // Only send user messages — litertlm manages assistant responses internally.
            val newUserMessages = messages.drop(sentUserMessageCount).filter { it.role == "user" }

            if (newUserMessages.isEmpty()) {
                return@withContext ""
            }

            // Send only the last new user message (the current turn)
            val lastUserMsg = newUserMessages.last()
            val content = sanitizeForLiteRt(lastUserMsg.content) ?: ""

            val startTime = System.currentTimeMillis()
            val response = sendMessageWithStreaming(conv, content)
            val durationMs = System.currentTimeMillis() - startTime

            sentUserMessageCount = messages.count { it.role == "user" }

            // Track generation metrics for performance visibility
            val charCount = response.length
            val tokensPerSec = if (durationMs > 0) {
                (charCount.toFloat() / durationMs * 1000f / 4f).coerceAtLeast(0f)
            } else 0f
            _lastGenerationMetrics.value = GenerationMetrics(
                outputCharCount = charCount,
                tokensPerSecond = tokensPerSec,
                durationMs = durationMs,
            )

            response
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun buildConversation(
        engine: Engine,
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        toolProviders: List<com.google.ai.edge.litertlm.ToolProvider>,
        samplerParams: SamplerParams,
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
        } else {
            emptyList()
        }

        // Enable constrained decoding for reliable tool calling.
        // Must be reset in finally to avoid leaking the flag on exception.
        ExperimentalFlags.enableConversationConstrainedDecoding = true
        try {
            val config = ConversationConfig(
                systemInstruction = systemPrompt?.let { Contents.of(it) },
                initialMessages = initialMessages,
                tools = toolProviders,
                samplerConfig = SamplerConfig(
                    topK = samplerParams.topK,
                    topP = samplerParams.topP.toDouble(),
                    temperature = samplerParams.temperature.toDouble(),
                ),
                automaticToolCalling = true,
            )

            runCatching { conversation?.close() }
            conversation = null
            conversation = engine.createConversation(config)
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
        // Only count user messages that were included as initialMessages (before the
        // last user message) — the last user message still needs to be sent via
        // sendMessageAsync in chat().
        sentUserMessageCount = if (lastUserIndex > 0) {
            messages.subList(0, lastUserIndex).count { it.role == "user" }
        } else {
            0
        }
        lastSystemPrompt = systemPrompt
        lastToolCount = toolProviders.size
        lastSamplerParams = samplerParams
    }

    /**
     * Sends a message using sendMessageAsync with MessageCallback for streaming.
     * Batches StateFlow emissions to reduce per-token string-copy overhead
     * and GC pressure from the full response being copied on every token.
     */
    private suspend fun sendMessageWithStreaming(
        conv: com.google.ai.edge.litertlm.Conversation,
        content: String,
    ): String = suspendCancellableCoroutine { continuation ->
        // Reset any stale streaming state from a previous run
        _streamingContent.value = null
        val responseBuilder = StringBuilder()
        val latch = CountDownLatch(1)
        var tokenCount = 0
        var lastFlushTime = 0L
        val flushIntervalMs = 50L
        val flushEveryN = 4

        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                responseBuilder.append(message.toString())
                tokenCount++
                val now = System.currentTimeMillis()
                // Only flush to StateFlow periodically to avoid O(n²) string copies
                if (tokenCount % flushEveryN == 0 || now - lastFlushTime >= flushIntervalMs) {
                    _streamingContent.value = responseBuilder.toString()
                    lastFlushTime = now
                }
            }

            override fun onDone() {
                // Always flush the complete response before finishing.
                // Don't set _streamingContent to null here — the collector in the
                // repository's finally block handles clearing the streaming state,
                // and setting null here would cause the UI to miss the final token.
                _streamingContent.value = responseBuilder.toString()
                latch.countDown()
                if (continuation.isActive) {
                    continuation.resume(responseBuilder.toString())
                }
            }

            override fun onError(throwable: Throwable) {
                latch.countDown()
                _streamingContent.value = null
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }

        // Handle cancellation — cancel the conversation process
        continuation.invokeOnCancellation {
            runCatching { conv.cancelProcess() }
        }

        try {
            conv.sendMessageAsync(content, callback)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun sanitizeForLiteRt(s: String?): String? {
        if (s == null) return null
        // Single-pass: scan for first lone surrogate, build filtered result only if needed.
        // Preserve valid surrogate pairs (e.g. emoji) — only strip lone surrogates.
        val len = s.length
        var i = 0
        while (i < len) {
            val c = s[i]
            if (c.isSurrogate()) {
                if (c.isHighSurrogate() && i + 1 < len && s[i + 1].isLowSurrogate()) {
                    // Valid surrogate pair — skip both
                    i += 2
                    continue
                }
                // Lone surrogate — start building filtered result
                val sb = StringBuilder(len - 1)
                if (i > 0) sb.append(s, 0, i)
                i++
                while (i < len) {
                    val c2 = s[i]
                    if (c2.isHighSurrogate() && i + 1 < len && s[i + 1].isLowSurrogate()) {
                        // Valid surrogate pair — keep both
                        sb.append(c2)
                        sb.append(s[i + 1])
                        i += 2
                    } else if (!c2.isSurrogate()) {
                        sb.append(c2)
                        i++
                    } else {
                        // Lone surrogate — skip
                        i++
                    }
                }
                return sb.toString()
            }
            i++
        }
        return s
    }

    companion object {
        private const val INFERENCE_TIMEOUT_MS = 120_000L
        private const val MIN_MEMORY_HEADROOM_BYTES = 512L * 1024 * 1024
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024
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

                startDownloadNotificationService(model.displayName)
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
                                updateDownloadNotificationProgress(percent, model.displayName)
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
                postDownloadCompleteNotification(
                    title = "Download complete",
                    text = "${model.displayName} downloaded",
                )
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
