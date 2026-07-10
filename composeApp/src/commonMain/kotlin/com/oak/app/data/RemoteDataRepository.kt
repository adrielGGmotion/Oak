@file:OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.oak.app.data

import com.oak.app.SandboxController
import com.oak.app.compressImageBytes
import com.oak.app.currentPlatform
import com.oak.app.email.EmailPoller
import com.oak.app.formatFileSize
import com.oak.app.getAvailableTools
import com.oak.app.getPlatformToolDefinitions
import com.oak.app.inference.DownloadError
import com.oak.app.inference.DownloadedModel
import com.oak.app.inference.EngineState
import com.oak.app.inference.InferenceMessage
import com.oak.app.inference.LocalInferenceEngine
import com.oak.app.inference.LocalModel
import com.oak.app.inference.LocalTool
import com.oak.app.inference.NoModelDownloadedException
import com.oak.app.inference.getTotalMemoryBytes
import com.oak.app.mcp.McpServerConfig
import com.oak.app.mcp.McpServerManager
import com.oak.app.network.AnthropicInsufficientCreditsException
import com.oak.app.network.ContextWindowExceededException
import com.oak.app.network.FileTooLargeException
import com.oak.app.network.OpenAICompatibleEmptyResponseException
import com.oak.app.network.OpenAICompatibleInvalidApiKeyException
import com.oak.app.network.OpenAICompatibleQuotaExhaustedException
import com.oak.app.network.Requests
import com.oak.app.network.ServiceCredentials
import com.oak.app.network.UnsupportedFileTypeException
import com.oak.app.network.dtos.anthropic.AnthropicChatRequestDto
import com.oak.app.network.dtos.anthropic.extractText
import com.oak.app.network.dtos.gemini.extractText
import com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import com.oak.app.network.dtos.openaicompatible.toolCallMarkerRegex
import com.oak.app.network.toUiError
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.smartTruncate
import com.oak.app.sms.SmsPoller
import com.oak.app.sms.SmsReader
import com.oak.app.sms.SmsSendResult
import com.oak.app.sms.SmsSender
import com.oak.app.ssh.SshAuthType
import com.oak.app.ssh.SshServerConfig
import com.oak.app.ssh.SshServerManager
import com.oak.app.tools.CommonTools
import com.oak.app.tools.NotificationListenerController
import com.oak.app.tools.SmsPermissionController
import com.oak.app.tools.SmsSendPermissionController
import com.oak.app.ui.chat.History
import com.oak.app.ui.chat.ToolCallInfo
import com.oak.app.ui.chat.toAnthropicContentBlocks
import com.oak.app.ui.chat.toGeminiMessageDto
import com.oak.app.ui.chat.toOpenAICompatibleMessageDto
import com.oak.app.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.default_soul
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_TOOL_ITERATIONS = 15
private const val MAX_UNLIMITED_TOOL_ITERATIONS = 500

private const val MIN_TOOL_DISPLAY_MS = 2000L

private const val MAX_REPEATED_TOOL_CALLS = 3
private const val MAX_UNLIMITED_REPEATED_TOOL_CALLS = 100

private const val ASK_QUESTIONS_TOOL_NAME = "ask_questions"
private const val MAX_API_RETRIES = 2
private const val MAX_HEARTBEAT_MESSAGES = 50
private const val ESTIMATED_CHARS_PER_TOKEN = 4
private const val COMPACTION_THRESHOLD = 0.7 // Compact when history exceeds 70% of context window
private const val COMPACTION_KEEP_RECENT = 4 // Number of recent user exchanges to keep verbatim

// Explicit allowlist of tools exposed to the on-device (LiteRT) model. We use a
// hardcoded name list rather than a structural filter because small on-device models
// struggle with complex schemas via prompt injection. Excluded by design:
// memory_learn (4 params + enum), schedule_task / list_tasks / cancel_task
// (datetime + cron), the entire email family, the heartbeat config tools, and MCP tools.
internal val LOCAL_TOOL_ALLOWLIST = setOf(
    "get_local_time",
    "get_location_from_ip",
    "web_search",
    "open_url",
    "memory_store",
    "memory_forget",
    "memory_reinforce",
    "execute_shell_command",
)

class RemoteDataRepository(
    private val requests: Requests,
    private val appSettings: AppSettings,
    private val conversationStorage: ConversationStorage,
    private val toolExecutor: ToolExecutor,
    private val memoryStore: MemoryStore,
    private val taskStore: TaskStore,
    private val heartbeatManager: HeartbeatManager,
    private val emailStore: EmailStore,
    private val emailPoller: EmailPoller,
    private val smsStore: SmsStore,
    private val smsPoller: SmsPoller,
    private val smsReader: SmsReader,
    private val smsPermissionController: SmsPermissionController,
    private val smsSendPermissionController: SmsSendPermissionController,
    private val smsSender: SmsSender,
    private val smsDraftStore: SmsDraftStore,
    private val notificationStore: NotificationStore,
    private val notificationListenerController: NotificationListenerController,
    private val mcpServerManager: McpServerManager,
    private val sshServerManager: SshServerManager,
    private val sandboxController: SandboxController,
    private val localInferenceEngine: LocalInferenceEngine? = null,
) : DataRepository {

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Returns the tools exposed to the on-device (LiteRT) model. Filtered by name against
     * [LOCAL_TOOL_ALLOWLIST]. Tools the user has disabled in settings (e.g. shell command,
     * which is gated behind `isToolEnabled("execute_shell_command")`) won't appear in
     * `getAvailableTools()` in the first place, so they're naturally excluded.
     */
    private fun getLocalSafeTools(): List<Tool> = getAvailableTools()
        .filter { it.schema.name in LOCAL_TOOL_ALLOWLIST }

    // Per-instance model storage: instanceId -> models flow
    private val modelsByInstance: MutableMap<String, MutableStateFlow<List<SettingsModel>>> = mutableMapOf()

    /** Build credentials from per-instance settings */
    private fun instanceCredentials(instanceId: String, service: Service): ServiceCredentials = ServiceCredentials(
        apiKey = appSettings.getInstanceApiKey(instanceId),
        modelId = appSettings.getInstanceModelId(instanceId).ifEmpty { appSettings.getSelectedModelId(service) },
        baseUrl = appSettings.getInstanceBaseUrl(instanceId).ifEmpty { appSettings.getBaseUrl(service) },
    )

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    override val currentConversationId: StateFlow<String?> = _currentConversationId

    private var askForConversationId: String? = null

    /** Per-conversation excluded skill IDs — synced with [Conversation.excludedSkillIds]. */
    private val _currentExcludedSkillIds = MutableStateFlow<Set<String>>(emptySet())

    private val _fallbackStatus = MutableStateFlow<FallbackStatus?>(null)
    override val fallbackStatus: StateFlow<FallbackStatus?> = _fallbackStatus

    private val _streamingReasoning = MutableStateFlow<String?>(null)
    override val streamingReasoning: StateFlow<String?> = _streamingReasoning

    private val _streamingContent = MutableStateFlow<String?>(null)
    override val streamingContent: StateFlow<String?> = _streamingContent

    override val savedConversations: StateFlow<List<Conversation>> = conversationStorage.conversations

    override fun getConfiguredServiceInstances(): List<ServiceInstance> = appSettings.getConfiguredServiceInstances()

    override fun addConfiguredService(serviceId: String): ServiceInstance {
        val instanceId = appSettings.generateInstanceId(serviceId)
        val instance = ServiceInstance(instanceId = instanceId, serviceId = serviceId)
        val current = appSettings.getConfiguredServiceInstances().toMutableList()
        current.add(instance)
        appSettings.setConfiguredServiceInstances(current)
        return instance
    }

    override fun removeConfiguredService(instanceId: String) {
        val current = appSettings.getConfiguredServiceInstances().toMutableList()
        current.removeAll { it.instanceId == instanceId }
        appSettings.setConfiguredServiceInstances(current)
        appSettings.removeInstanceSettings(instanceId)
        modelsByInstance.remove(instanceId)
    }

    override fun reorderConfiguredServices(orderedInstanceIds: List<String>) {
        val current = appSettings.getConfiguredServiceInstances()
        val byId = current.associateBy { it.instanceId }
        val reordered = orderedInstanceIds.mapNotNull { byId[it] }
        appSettings.setConfiguredServiceInstances(reordered)
    }

    override fun getServiceEntries(): List<ServiceEntry> = getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val modelId = appSettings.getInstanceModelId(instance.instanceId).ifEmpty {
            appSettings.getSelectedModelId(service)
        }
        ServiceEntry(
            instanceId = instance.instanceId,
            serviceId = service.id,
            serviceName = service.displayName,
            modelId = modelId,
            icon = service.icon,
        )
    }

    override fun isUnlimitedToolCallsEnabled(): Boolean = appSettings.isUnlimitedToolCallsEnabled()

    override fun setUnlimitedToolCallsEnabled(enabled: Boolean) {
        appSettings.setUnlimitedToolCallsEnabled(enabled)
    }

    /** Max iterations before forcing a text-only response. 500 = effectively unlimited. */
    private fun maxToolIterations(): Int = if (isUnlimitedToolCallsEnabled()) MAX_UNLIMITED_TOOL_ITERATIONS else MAX_TOOL_ITERATIONS

    /** Max repeated tool-call sequences before bailing out. 100 = effectively unlimited. */
    private fun maxRepeatedToolCalls(): Int = if (isUnlimitedToolCallsEnabled()) MAX_UNLIMITED_REPEATED_TOOL_CALLS else MAX_REPEATED_TOOL_CALLS

    override fun isStreamingEnabled(): Boolean = appSettings.isStreamingEnabled()

    override fun setStreamingEnabled(enabled: Boolean) {
        appSettings.setStreamingEnabled(enabled)
    }

    // Per-instance settings
    override fun getInstanceApiKey(instanceId: String): String = appSettings.getInstanceApiKey(instanceId)

    override fun updateInstanceApiKey(instanceId: String, apiKey: String) {
        appSettings.setInstanceApiKey(instanceId, apiKey)
    }

    override fun getInstanceBaseUrl(instanceId: String, service: Service): String {
        val url = appSettings.getInstanceBaseUrl(instanceId)
        return url.ifBlank { if (service is Service.OpenAICompatible) Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL else "" }
    }

    override fun updateInstanceBaseUrl(instanceId: String, baseUrl: String) {
        appSettings.setInstanceBaseUrl(instanceId, baseUrl)
    }

    override fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>> = modelsByInstance.getOrPut(instanceId) {
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val defaultSettingsModels = service.defaultModels.map {
            SettingsModel(
                id = it.id,
                subtitle = it.subtitle,
                descriptionRes = it.descriptionRes,
                isSelected = it.id == selectedModelId,
            )
        }
        val models = if (selectedModelId.isNotEmpty() && defaultSettingsModels.none { it.id == selectedModelId }) {
            listOf(SettingsModel(id = selectedModelId, subtitle = "", isSelected = true)) + defaultSettingsModels
        } else {
            defaultSettingsModels
        }
        MutableStateFlow(models)
    }

    override fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String) {
        appSettings.setInstanceModelId(instanceId, modelId)
        modelsByInstance[instanceId]?.update { models ->
            models.map { it.copy(isSelected = it.id == modelId) }
        }
        // Free the previously-loaded on-device model as soon as the user picks a new one.
        // Deferring until the next chat would briefly hold both models' GPU buffers resident
        // and the driver's lazy reclaim can push us past LMK thresholds on mid-range devices.
        if (service.isOnDevice && localInferenceEngine?.currentModelId?.let { it != modelId } == true) {
            localInferenceEngine.releaseInBackground()
        }
    }

    override fun clearInstanceModels(instanceId: String, service: Service) {
        modelsByInstance[instanceId]?.update { emptyList() }
    }

    override suspend fun validateConnection(service: Service, instanceId: String) {
        if (service.isOnDevice) {
            fetchInstanceModels(service, instanceId)
            return
        }
        val creds = instanceCredentials(instanceId, service)
        when (service) {
            Service.OpenRouter -> {
                requests.validateOpenRouterApiKey(creds).getOrThrow()
                fetchInstanceModels(service, instanceId)
            }

            else -> fetchInstanceModels(service, instanceId)
        }
    }

    private suspend fun fetchInstanceModels(service: Service, instanceId: String) {
        when (service) {
            Service.Gemini -> fetchGeminiModelsForInstance(instanceId)

            Service.Anthropic -> fetchAnthropicModelsForInstance(instanceId)

            Service.LiteRT -> {
                val engine = localInferenceEngine ?: return
                val selectedModelId = appSettings.getInstanceModelId(instanceId)
                val downloaded = engine.getDownloadedModels()
                val models = downloaded.map {
                    SettingsModel(
                        id = it.id,
                        subtitle = "${it.displayName} (${formatFileSize(it.sizeBytes)})",
                        isSelected = it.id == selectedModelId,
                    )
                }
                updateModelsForInstance(instanceId, models, service)
            }

            else -> {
                if (service.modelsUrl != null) {
                    fetchOpenAICompatibleModelsForInstance(service, instanceId)
                } else if (service.defaultModels.isNotEmpty()) {
                    val selectedModelId = appSettings.getInstanceModelId(instanceId)
                    val models = service.defaultModels.map {
                        SettingsModel(
                            id = it.id,
                            subtitle = it.subtitle,
                            descriptionRes = it.descriptionRes,
                            isSelected = it.id == selectedModelId,
                        )
                    }
                    updateModelsForInstance(instanceId, models, service)
                }
            }
        }
    }

    private suspend fun fetchAnthropicModelsForInstance(instanceId: String) {
        val creds = instanceCredentials(instanceId, Service.Anthropic)
        val response = requests.getAnthropicModels(creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapAnthropicModels(response.data, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private suspend fun fetchGeminiModelsForInstance(instanceId: String) {
        val creds = instanceCredentials(instanceId, Service.Gemini)
        val response = requests.getGeminiModels(creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapGeminiModels(response.models, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private suspend fun fetchOpenAICompatibleModelsForInstance(service: Service, instanceId: String) {
        val creds = instanceCredentials(instanceId, service)
        val response = requests.getOpenAICompatibleModels(service, creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapOpenAICompatibleModels(response.data, service, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private fun updateModelsForInstance(instanceId: String, models: List<SettingsModel>, service: Service? = null) {
        val flow = modelsByInstance.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        flow.update { models }
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            val default = pickDefaultModel(models, service)
            if (default != null) {
                appSettings.setInstanceModelId(instanceId, default.id)
                flow.update { m -> m.map { it.copy(isSelected = it.id == default.id) } }
            }
        }
    }

    private fun pickDefaultModel(models: List<SettingsModel>, service: Service? = null): SettingsModel? {
        val defaultModel = service?.defaultModel
        if (defaultModel != null) {
            models.firstOrNull { it.id == defaultModel }?.let { return it }
        }
        return models.firstOrNull { it.id.contains("kimi-k2.5", ignoreCase = true) }
            ?: models.firstOrNull()
    }

    private suspend fun askWithLocalEngine(
        messages: List<History>,
        systemPrompt: String?,
        instanceId: String,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val engine = localInferenceEngine
            ?: throw IllegalStateException("On-device inference not available on this platform")

        val modelId = appSettings.getInstanceModelId(instanceId)
        val downloadedModels = engine.getDownloadedModels()
        val model = downloadedModels.find { it.id == modelId }
            ?: downloadedModels.firstOrNull()
            ?: throw NoModelDownloadedException()

        val catalogModel = engine.getAvailableModels().find { it.id == model.id }
        val storedContext = appSettings.getModelContextTokens(model.id)
        val contextTokens = if (storedContext > 0) storedContext else catalogModel?.defaultContextTokens ?: 0

        val backendPref = appSettings.getBackendPreference()
        val needsInit = engine.engineState.value != EngineState.READY || engine.currentModelId != model.id || engine.currentBackendPref != backendPref
        if (needsInit) {
            val statusEntry = History(
                role = History.Role.TOOL_EXECUTING,
                content = "",
                toolName = "Initializing ${model.displayName}",
                isStatusMessage = true,
            )
            history.update { it + statusEntry }
            try {
                engine.initialize(model, contextTokens, backendPref)
            } finally {
                history.update { h -> h.filter { it.id != statusEntry.id } }
            }
        }

        // Convert Oak tools to LocalTool format for native litertlm tool calling.
        // litertlm handles tool call parsing, execution, and result injection internally
        // via constrained decoding — no manual XML/JSON parsing needed.
        val localTools = getLocalSafeTools().map { tool ->
            val schema = tool.schema
            val descriptionJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("name", schema.name)
                    put("description", schema.description)
                    if (schema.parameters.isNotEmpty()) {
                        put(
                            "parameters",
                            kotlinx.serialization.json.buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    kotlinx.serialization.json.buildJsonObject {
                                        for ((name, param) in schema.parameters) {
                                            put(
                                                name,
                                                kotlinx.serialization.json.buildJsonObject {
                                                    put("type", param.type)
                                                    put("description", param.description)
                                                },
                                            )
                                        }
                                    },
                                )
                                put(
                                    "required",
                                    kotlinx.serialization.json.buildJsonArray {
                                        for ((name, param) in schema.parameters) {
                                            if (param.required) add(kotlinx.serialization.json.JsonPrimitive(name))
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
            LocalTool(
                name = schema.name,
                descriptionJsonString = descriptionJson,
                execute = { jsonArgs ->
                    val argsMap = try {
                        val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonArgs)
                        if (element is kotlinx.serialization.json.JsonObject) {
                            element.mapValues { (_, v) ->
                                when (v) {
                                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                                    else -> v.toString()
                                }
                            }
                        } else {
                            emptyMap<String, Any>()
                        }
                    } catch (e: Exception) {
                        emptyMap<String, Any>()
                    }
                    val result = tool.execute(argsMap)
                    result.toString()
                },
            )
        }

        val inferenceMessages = messages.mapNotNull { msg ->
            when (msg.role) {
                History.Role.USER -> InferenceMessage(role = "user", content = msg.content)
                History.Role.ASSISTANT -> InferenceMessage(role = "assistant", content = msg.content)
                else -> null
            }
        }

        // Pass tools to engine.chat() — litertlm handles tool calling natively
        // Collect streaming tokens from the engine and propagate to UI StateFlows
        return coroutineScope {
            val streamingJob = launch {
                engine.streamingContent.collect { token ->
                    _streamingContent.value = token
                }
            }
            try {
                stripThinkBlocks(
                    engine.chat(
                        messages = inferenceMessages,
                        systemPrompt = systemPrompt,
                        tools = localTools,
                    ),
                )
            } finally {
                streamingJob.cancel()
            }
        }
    }

    /**
     * Builds a text description of available tools for injection into the local model's
     * system prompt. Returns null when there are no tools to expose.
     */
    private fun buildLocalToolPrompt(tools: List<Tool>): String? {
        if (tools.isEmpty()) return null
        return buildString {
            append("## Available Tools\n\n")
            append("When you need to use a tool, output an XML block:\n")
            append("""<invoke name="tool_name"><parameter name="param_name">value</parameter></invoke>""")
            append("\n\nOr alternatively, a JSON block on its own line:\n")
            append("""{"function": "name", "arguments": {"param": "value"}}""")
            append("\n\n")
            for (tool in tools) {
                append("- **").append(tool.schema.name).append("**")
                if (tool.schema.description.isNotEmpty()) {
                    append(": ").append(tool.schema.description)
                }
                append('\n')
                val params = tool.schema.parameters
                if (params.isNotEmpty()) {
                    append("  Parameters:\n")
                    for ((name, param) in params) {
                        val paramDesc = if (param.description.isNotEmpty()) " — ${param.description}" else ""
                        val raw = param.rawSchema
                        if (raw != null) {
                            append("    - `$name` (${param.type}$paramDesc): ${raw}\n")
                        } else {
                            val required = if (param.required) " (required)" else ""
                            append("    - `$name`: ${param.type}$required$paramDesc\n")
                        }
                    }
                }
            }
        }
    }

    private val thinkBlockRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

    private fun stripThinkBlocks(text: String): String = thinkBlockRegex.replace(text, "").trim()

    /**
     * Extracts a balanced JSON object string starting at [startIdx] in [s].
     * Counts brace depth to correctly handle nested objects.
     */
    private fun extractBalancedBraceBlock(s: String, startIdx: Int): String? {
        if (startIdx >= s.length || s[startIdx] != '{') return null
        var depth = 0
        var idx = startIdx
        var inString = false
        while (idx < s.length) {
            val c = s[idx]
            if (c == '"' && (idx == 0 || s[idx - 1] != '\\')) inString = !inString
            if (!inString) {
                when (c) {
                    '{' -> depth++

                    '}' -> {
                        depth--
                        if (depth == 0) return s.substring(startIdx, idx + 1)
                    }
                }
            }
            idx++
        }
        return null
    }

    private data class ParsedInvokeCall(val name: String, val arguments: String)

    private val invokeBlockRegex = Regex(
        """<invoke\s+(?:[^>]*\s)?name\s*=\s*["']([^"']+)["'](?:[^>]*)>([\s\S]*?)</invoke>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val parameterRegex = Regex(
        """<parameter\s+(?:[^>]*\s)?name\s*=\s*["']([^"']+)["'](?:[^>]*)>([\s\S]*?)</parameter>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val invokeStripRegex = Regex(
        """<invoke\b[^>]*>[\s\S]*?</invoke>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private fun String.stripToolMarkup(): String = this
        .replace(toolCallMarkerRegex, "")
        .replace(invokeStripRegex, "")
        .trim()

    private fun coerceValue(raw: String): JsonElement {
        val trimmed = raw.trim()
        if (trimmed.equals("true", ignoreCase = true)) return JsonPrimitive(true)
        if (trimmed.equals("false", ignoreCase = true)) return JsonPrimitive(false)
        val asInt = trimmed.toIntOrNull()
        if (asInt != null) return JsonPrimitive(asInt)
        val asDouble = trimmed.toDoubleOrNull()
        if (asDouble != null) return JsonPrimitive(asDouble)
        return JsonPrimitive(trimmed)
    }

    private fun parseInvokeBlocks(text: String): List<ParsedInvokeCall> = invokeBlockRegex.findAll(text).map { match ->
        val name = match.groupValues[1]
        val body = match.groupValues[2]
        val params = buildJsonObject {
            parameterRegex.findAll(body).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = paramMatch.groupValues[2].trim()
                put(paramName, coerceValue(paramValue))
            }
        }
        ParsedInvokeCall(name = name, arguments = params.toString())
    }.toList()

    private data class InlineToolCallResult(
        val toolCalls: List<ToolCallInfo>,
        val cleanContent: String,
    )

    private fun parseInlineToolCalls(text: String): InlineToolCallResult? {
        val cleaned = text.replace(toolCallMarkerRegex, "").trim()
        val calls = parseInvokeBlocks(cleaned)
        if (calls.isEmpty()) return null
        return InlineToolCallResult(
            toolCalls = calls.map { ToolCallInfo(id = "invoke-${Uuid.random()}", name = it.name, arguments = it.arguments) },
            cleanContent = cleaned.replace(invokeBlockRegex, "").trim(),
        )
    }

    private val jsonToolCallRegex = Regex(
        """\{"function"\s*:\s*"([^"]+)"\s*,\s*"arguments"\s*:\s*(\{.*?\})\s*\}""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun parseJsonToolCalls(text: String): List<ParsedInvokeCall> {
        return jsonToolCallRegex.findAll(text).mapNotNull { match ->
            val name = match.groupValues[1]
            val rawArgs = match.groupValues[2]
            val balanced = extractBalancedBraceBlock(rawArgs, 0) ?: return@mapNotNull null
            ParsedInvokeCall(name = name, arguments = balanced)
        }.toList()
    }

    private suspend fun askWithService(
        service: Service,
        messages: List<History>,
        systemPrompt: String?,
        instanceId: String,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        if (service.isOnDevice) {
            // Re-fetch the system prompt with the CHAT_LOCAL variant — the caller
            // (`ask()`/`askWithTools()`) pre-fetched a CHAT_REMOTE prompt, but on-device
            // needs the trimmed variant.
            val localPrompt = getActiveSystemPrompt(SystemPromptVariant.CHAT_LOCAL)
            return askWithLocalEngine(messages, localPrompt, instanceId, history)
        }

        val creds = instanceCredentials(instanceId, service)
        val tools = if (supportsTools(creds.modelId)) getAvailableTools() else emptyList()

        return when (service) {
            Service.Gemini -> {
                if (tools.isNotEmpty()) {
                    handleGeminiChatWithTools(creds, messages, tools, systemPrompt, history)
                } else {
                    val geminiMessages = messages.map { it.toGeminiMessageDto() }
                    val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.extractText()
                }
            }

            Service.Anthropic -> {
                if (tools.isNotEmpty()) {
                    handleAnthropicChatWithTools(creds, messages, tools, systemPrompt, history)
                } else {
                    val anthropicMessages = buildAnthropicMessages(messages)
                    val response = requests.anthropicChat(creds, anthropicMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.extractText()
                }
            }

            else -> {
                handleOpenAICompatibleChatWithTools(service, creds, messages, tools, systemPrompt, history)
            }
        }
    }

    private fun hasValidInstanceApiKey(instanceId: String, service: Service): Boolean {
        if (service.isOnDevice) return true
        if (!service.requiresApiKey && !service.supportsOptionalApiKey) return true
        if (service.requiresApiKey) return appSettings.getInstanceApiKey(instanceId).isNotBlank()
        return true // Optional API key services are always valid
    }

    private data class FallbackEntry(val instanceId: String, val service: Service)

    private fun getOrderedFallbackEntries(): List<FallbackEntry> {
        val instances = getConfiguredServiceInstances()
        return instances.map { FallbackEntry(instanceId = it.instanceId, service = Service.fromId(it.serviceId)) }
            .filter { !it.service.isOnDevice || localInferenceEngine != null }
    }

    override suspend fun ask(question: String?, files: List<PlatformFile>, uiSubmission: UiSubmission?) {
        // Allocate a conversation id immediately for fresh chats. Without this,
        // the very first tool call lands here with _currentConversationId.value
        // still null, so per-conversation routing (e.g. the sandbox shell)
        // falls through to a shared default — which both makes the new chat
        // invisible in the Terminal session picker and lets unrelated callers
        // collide on the same shell mutex. Persistence is deferred to the
        // existing saveCurrentConversation() flow that runs after the response.
        if (_currentConversationId.value == null) {
            setCurrentConversationId(Uuid.random().toString())
        }
        // Process every attached file: classify, compress/encode, and build an Attachment.
        // readBytes() is suspend, so this happens before the StateFlow.update block.
        val attachments = files.map { file ->
            val fileMimeType = file.mimeType()?.toString()
            val fileName = file.name

            val category = classifyFile(fileMimeType, fileName)
            if (category == FileCategory.UNSUPPORTED) throw UnsupportedFileTypeException()

            // Reject oversized files by stat size before readBytes(), which would otherwise
            // allocate a ByteArray large enough to OOM the process on multi-GB inputs.
            val rawSizeLimit = when (category) {
                FileCategory.TEXT -> MAX_TEXT_FILE_BYTES.toLong()
                FileCategory.PDF -> MAX_PDF_BYTES.toLong()
                FileCategory.IMAGE -> MAX_RAW_IMAGE_BYTES.toLong()
                FileCategory.UNSUPPORTED -> 0L
            }
            if (file.size() > rawSizeLimit) throw FileTooLargeException()

            val rawBytes = file.readBytes()

            when (category) {
                FileCategory.IMAGE -> {
                    val compressed = compressImageBytes(rawBytes, fileMimeType ?: "image/jpeg")
                    // compressImageBytes can fall back to the original bytes on failure or on
                    // platforms without compression — guard against Base64 OOM for oversized input.
                    if (compressed.size > MAX_IMAGE_BYTES) throw FileTooLargeException()
                    Attachment(
                        data = Base64.encode(compressed),
                        mimeType = "image/jpeg",
                        fileName = null,
                    )
                }

                FileCategory.TEXT -> Attachment(
                    data = Base64.encode(rawBytes),
                    mimeType = fileMimeType ?: "text/plain",
                    fileName = fileName,
                )

                FileCategory.PDF -> Attachment(
                    data = Base64.encode(rawBytes),
                    mimeType = "application/pdf",
                    fileName = fileName,
                )

                FileCategory.UNSUPPORTED -> throw UnsupportedFileTypeException()
            }
        }.toImmutableList()

        if (question != null) {
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.USER,
                            content = question,
                            attachments = attachments,
                            uiSubmission = uiSubmission,
                        ),
                    )
                }
            }
        }

        compactHistoryIfNeeded()

        val messages = chatHistory.value
        val systemPrompt = getActiveSystemPrompt()

        val fallbackEntries = getOrderedFallbackEntries().filter { hasValidInstanceApiKey(it.instanceId, it.service) }

        val historyChars = messages.sumOf { it.content.length } + (systemPrompt?.length ?: 0)

        var lastException: Exception? = null
        var fallbackServiceName: String? = null

        try {
            for ((index, entry) in fallbackEntries.withIndex()) {
                // Skip fallback services whose context window is too small for the current history
                // On-device models handle their own context limits, so skip this check for them
                if (!entry.service.isOnDevice) {
                    val creds = instanceCredentials(entry.instanceId, entry.service)
                    val entryWindowChars = ModelCatalog.estimateContextWindow(creds.modelId) * ESTIMATED_CHARS_PER_TOKEN
                    if (historyChars > entryWindowChars) {
                        lastException = ContextWindowExceededException()
                        _fallbackStatus.value = FallbackStatus(entry.service.displayName, ContextWindowExceededException().toUiError())
                        continue
                    }
                }

                val responseText = try {
                    retryApiCall {
                        askWithService(entry.service, messages, systemPrompt, entry.instanceId)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (isNonRetryableException(e)) throw e
                    // On-device services should not silently fall back — surface the error
                    if (entry.service.isOnDevice) throw e
                    lastException = e
                    _fallbackStatus.value = FallbackStatus(entry.service.displayName, e.toUiError())
                    continue
                }
                if (index > 0) {
                    fallbackServiceName = entry.service.displayName
                }
                val reasoning = _streamingReasoning.value
                val isReasoningOnly = responseText.isBlank() && !reasoning.isNullOrBlank()
                chatHistory.update {
                    it.toMutableList().apply {
                        add(History(role = History.Role.ASSISTANT, content = responseText.stripToolMarkup(), isThinking = isReasoningOnly, reasoningContent = reasoning, fallbackServiceName = fallbackServiceName))
                    }
                }
                saveCurrentConversation()
                return
            }

            throw lastException ?: OpenAICompatibleEmptyResponseException()
        } finally {
            _fallbackStatus.value = null
            _streamingContent.value = null
            _streamingReasoning.value = null
        }
    }

    override suspend fun askForConversation(
        conversationId: String,
        question: String?,
        files: List<PlatformFile>,
        uiSubmission: UiSubmission?,
    ) {
        val previousConversationId = _currentConversationId.value
        val previousHistory = chatHistory.value.toList()
        val previousExcludedSkillIds = _currentExcludedSkillIds.value

        if (savedConversations.value.any { it.id == conversationId }) {
            loadConversation(conversationId)
        } else {
            setCurrentConversationId(conversationId)
            _currentExcludedSkillIds.value = emptySet()
            chatHistory.value = emptyList()
        }

        askForConversationId = conversationId

        try {
            ask(question, files, uiSubmission)
        } finally {
            askForConversationId = null

            if (previousConversationId != null && savedConversations.value.any { it.id == previousConversationId }) {
                loadConversation(previousConversationId)
            } else {
                _currentConversationId.value = previousConversationId
                _currentExcludedSkillIds.value = previousExcludedSkillIds
                chatHistory.value = previousHistory
            }
        }
    }

    private suspend fun handleOpenAICompatibleChatWithTools(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val contextWindowTokens = ModelCatalog.estimateContextWindow(credentials.modelId)
        var currentMessages = trimMessagesForContext(
            buildOpenAIMessages(
                messages.filter { it.role != History.Role.TOOL_EXECUTING },
                systemPrompt,
            ),
            contextWindowTokens,
        )

        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        try {
            while (true) {
                iteration++
                if (iteration > maxToolIterations()) {
                    return makeFinalCallWithoutTools(service, credentials, currentMessages)
                }

                _streamingContent.value = null
                _streamingReasoning.value = null

                var (textContent, fullReasoning, toolCalls) = try {
                    retryApiCall {
                        if (appSettings.isStreamingEnabled()) {
                            _streamingContent.value = null
                            _streamingReasoning.value = null
                            val contentBuilder = StringBuilder()
                            val reasoningBuilder = StringBuilder()
                            val toolCallAccumulators = mutableMapOf<Int, MutableMap<String, String>>()

                            requests.openAICompatibleChatStream(service, credentials, currentMessages, tools)
                                .collect { chunk ->
                                    val choice = chunk.choices?.firstOrNull()
                                    val delta = choice?.delta ?: return@collect

                                    delta.content?.let { c ->
                                        contentBuilder.append(c)
                                        _streamingContent.value = contentBuilder.toString()
                                    }
                                    delta.reasoningContent?.let { r ->
                                        reasoningBuilder.append(r)
                                        _streamingReasoning.value = reasoningBuilder.toString()
                                    }
                                    delta.toolCalls?.forEach { tc ->
                                        val acc = toolCallAccumulators.getOrPut(tc.index) { mutableMapOf() }
                                        tc.id?.let { acc["id"] = it }
                                        tc.type?.let { acc["type"] = it }
                                        tc.function?.name?.let { acc["function_name"] = (acc["function_name"] ?: "") + it }
                                        tc.function?.arguments?.let { acc["function_arguments"] = (acc["function_arguments"] ?: "") + it }
                                    }
                                }

                            val calls = toolCallAccumulators.entries.map { (_, acc) ->
                                OpenAICompatibleChatResponseDto.ToolCall(
                                    id = acc["id"] ?: "",
                                    type = acc["type"] ?: "function",
                                    function = OpenAICompatibleChatResponseDto.FunctionCall(
                                        name = acc["function_name"] ?: "",
                                        arguments = acc["function_arguments"] ?: "",
                                    ),
                                )
                            }

                            Triple(
                                contentBuilder.toString().ifEmpty { null },
                                reasoningBuilder.toString().ifEmpty { null },
                                calls,
                            )
                        } else {
                            val response = requests.openAICompatibleChat(service, credentials, currentMessages, tools).getOrThrow()
                            val choice = response.choices.firstOrNull()
                            val message = choice?.message
                            Triple(
                                message?.effectiveContent,
                                message?.effectiveReasoning,
                                message?.toolCalls ?: emptyList(),
                            )
                        }
                    }
                } catch (e: Exception) {
                    _streamingContent.value = null
                    _streamingReasoning.value = null
                    throw e
                }

                if (toolCalls.isEmpty()) {
                    if (textContent != null) {
                        val inlineResult = parseInlineToolCalls(textContent)
                        if (inlineResult != null) {
                            toolCalls = inlineResult.toolCalls.map { tc ->
                                OpenAICompatibleChatResponseDto.ToolCall(
                                    id = tc.id,
                                    type = "function",
                                    function = OpenAICompatibleChatResponseDto.FunctionCall(
                                        name = tc.name,
                                        arguments = tc.arguments,
                                    ),
                                )
                            }
                            textContent = inlineResult.cleanContent.ifEmpty { null }
                        } else {
                            val stripped = textContent.stripToolMarkup()
                            return stripped.ifEmpty { "" }
                        }
                    } else {
                        return ""
                    }
                }

                val signatures = toolCalls.map { "${it.function.name}:${it.function.arguments.hashCode()}" }
                if (isRepeatingToolCalls(recentSignatures, signatures)) {
                    return makeFinalCallWithoutTools(service, credentials, currentMessages)
                }
                recentSignatures.addAll(signatures)

                history.update {
                    it.toMutableList().apply {
                        add(
                            History(
                                role = History.Role.ASSISTANT,
                                content = textContent?.stripToolMarkup() ?: "",
                                isThinking = textContent == null && fullReasoning != null,
                                toolCalls = toolCalls.map { tc ->
                                    ToolCallInfo(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
                                }.toImmutableList(),
                                reasoningContent = fullReasoning,
                            ),
                        )
                    }
                }

                val toolResults = executeToolCallsInParallel(toolCalls.map { Triple(it.id, it.function.name, it.function.arguments) })

                history.update { h ->
                    buildList<History>(h.size + toolResults.size) {
                        for (entry in h) {
                            if (entry.role != History.Role.TOOL_EXECUTING) {
                                val cleaned = entry.withoutAskQuestionsToolCall()
                                if (cleaned != null) add(cleaned)
                            }
                        }
                        for ((callId, name, result) in toolResults) {
                            if (name == ASK_QUESTIONS_TOOL_NAME) {
                                add(History(role = History.Role.USER, content = result))
                            } else {
                                add(
                                    History(
                                        role = History.Role.TOOL,
                                        content = result,
                                        toolCallId = callId,
                                        toolName = name,
                                    ),
                                )
                            }
                        }
                    }
                }

                currentMessages = trimMessagesForContext(
                    buildOpenAIMessages(
                        history.value.filter { it.role != History.Role.TOOL_EXECUTING },
                        systemPrompt,
                    ),
                    contextWindowTokens,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                return makeFinalCallWithoutTools(service, credentials, currentMessages)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                return ""
            }
        }
    }

    private suspend fun handleGeminiChatWithTools(credentials: ServiceCredentials, messages: List<History>, tools: List<Tool>, systemPrompt: String? = null, history: MutableStateFlow<List<History>> = chatHistory): String {
        val contextWindowTokens = ModelCatalog.estimateContextWindow(credentials.modelId)
        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        try {
            while (true) {
                iteration++

                if (iteration > maxToolIterations()) {
                    // Bail out: make a final Gemini call without tools
                    val currentMessages = history.value.filter { it.role != History.Role.TOOL_EXECUTING }
                    val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }
                    val bailoutResponse = retryApiCall {
                        requests.geminiChat(
                            credentials = credentials,
                            messages = geminiMessages,
                            systemInstruction = "Please synthesize your best answer based on the information you have gathered so far. $systemPrompt",
                        ).getOrThrow()
                    }
                    return bailoutResponse.extractText()
                }

                val currentMessages = history.value.filter { it.role != History.Role.TOOL_EXECUTING }
                val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }

                val response = retryApiCall {
                    requests.geminiChat(credentials = credentials, messages = geminiMessages, tools = tools, systemInstruction = systemPrompt).getOrThrow()
                }
                val parts = response.candidates.firstOrNull()?.content?.parts ?: return ""

                val partsWithFunctionCalls = parts.filter { it.functionCall != null }
                val toolCallInfos: List<ToolCallInfo>
                val textContent: String

                if (partsWithFunctionCalls.isEmpty()) {
                    val text = parts.filterNot { it.isThought }.joinToString("\n") { it.text ?: "" }
                    val inlineResult = parseInlineToolCalls(text)
                    if (inlineResult == null) {
                        return text
                    }
                    toolCallInfos = inlineResult.toolCalls
                    textContent = inlineResult.cleanContent
                } else {
                    toolCallInfos = partsWithFunctionCalls.map { part ->
                        val fc = part.functionCall!!
                        val argsJson = fc.args?.let { JsonObject(it).toString() } ?: "{}"
                        ToolCallInfo(
                            id = "gemini-${Uuid.random()}",
                            name = fc.name,
                            arguments = argsJson,
                            thoughtSignature = part.thoughtSignature,
                        )
                    }
                    textContent = parts.filterNot { it.isThought }.mapNotNull { it.text }.joinToString("\n")
                        .replace(invokeBlockRegex, "").trim()
                }

                val signatures = toolCallInfos.map { "${it.name}:${it.arguments.hashCode()}" }
                if (isRepeatingToolCalls(recentSignatures, signatures)) {
                    val bailoutMessages = currentMessages.map { it.toGeminiMessageDto() }
                    val bailoutResponse = retryApiCall {
                        requests.geminiChat(
                            credentials = credentials,
                            messages = bailoutMessages,
                            systemInstruction = "Please synthesize your best answer based on the information you have gathered so far. $systemPrompt",
                        ).getOrThrow()
                    }
                    return bailoutResponse.extractText()
                }
                recentSignatures.addAll(signatures)

                history.update {
                    it.toMutableList().apply {
                        add(
                            History(
                                role = History.Role.ASSISTANT,
                                content = textContent.stripToolMarkup(),
                                toolCalls = toolCallInfos.toImmutableList(),
                            ),
                        )
                    }
                }

                val toolResults = executeToolCallsInParallel(toolCallInfos.map { Triple(it.id, it.name, it.arguments) })

                history.update { h ->
                    val updated = buildList<History>(h.size + toolResults.size) {
                        for (entry in h) {
                            if (entry.role != History.Role.TOOL_EXECUTING) {
                                val cleaned = entry.withoutAskQuestionsToolCall()
                                if (cleaned != null) add(cleaned)
                            }
                        }
                        for ((callId, name, result) in toolResults) {
                            if (name == ASK_QUESTIONS_TOOL_NAME) {
                                add(History(role = History.Role.USER, content = result))
                            } else {
                                add(
                                    History(
                                        role = History.Role.TOOL,
                                        content = result,
                                        toolCallId = callId,
                                        toolName = name,
                                    ),
                                )
                            }
                        }
                    }
                    trimHistoryForContext(updated, systemPrompt?.length ?: 0, contextWindowTokens)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val currentMessages = history.value.filter { it.role != History.Role.TOOL_EXECUTING }
                val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }
                val bailoutResponse = retryApiCall {
                    requests.geminiChat(
                        credentials = credentials,
                        messages = geminiMessages,
                        systemInstruction = systemPrompt,
                    ).getOrThrow()
                }
                return bailoutResponse.extractText()
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                return ""
            }
        }
    }

    /**
     * Walks the message list and ensures tool-call sequence integrity.
     * OpenAI-compatible APIs require every `assistant` message with `tool_calls`
     * to be directly followed by `tool` role messages covering all call IDs.
     *
     * - Broken pairs (assistant with tool_calls but missing tool responses) are
     *   repaired by stripping tool_calls from the assistant (or dropping it if
     *   it has no content).
     * - Orphaned tool messages (no preceding assistant with matching IDs) are dropped.
     */
    private fun sanitizeToolSequences(
        messages: List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
    ): List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> {
        val result = mutableListOf<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            when {
                msg.role == "assistant" && !msg.tool_calls.isNullOrEmpty() -> {
                    val expectedIds = msg.tool_calls.map { it.id }.toSet()
                    val toolIdsFound = mutableSetOf<String>()
                    var j = i + 1
                    while (j < messages.size && messages[j].role == "tool") {
                        messages[j].tool_call_id?.let { toolIdsFound.add(it) }
                        j++
                    }
                    val allMatch = expectedIds.isNotEmpty() &&
                        toolIdsFound.containsAll(expectedIds) &&
                        toolIdsFound.size == expectedIds.size
                    if (allMatch) {
                        // Complete pair — keep everything
                        result.add(msg)
                        result.addAll(messages.subList(i + 1, j))
                    } else {
                        // Broken pair — keep assistant as text-only if it has content
                        if (msg.content != null) {
                            result.add(msg.copy(tool_calls = null))
                        }
                        // tool messages are implicitly dropped
                    }
                    i = j
                }

                msg.role == "tool" -> {
                    // Orphaned tool message — drop it
                    i++
                }

                else -> {
                    result.add(msg)
                    i++
                }
            }
        }
        return result
    }

    private fun buildOpenAIMessages(
        messages: List<History>,
        systemPrompt: String?,
    ): List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> = buildList {
        if (!systemPrompt.isNullOrEmpty()) {
            add(
                com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message(
                    role = "system",
                    content = JsonPrimitive(systemPrompt),
                ),
            )
        }
        addAll(
            messages.map { it.toOpenAICompatibleMessageDto() }
                .let { sanitizeToolSequences(it) },
        )
    }

    private fun buildAnthropicMessages(
        messages: List<History>,
    ): List<AnthropicChatRequestDto.Message> = buildList {
        var pendingToolResults = mutableListOf<JsonElement>()

        for (msg in messages) {
            when (msg.role) {
                History.Role.TOOL_EXECUTING -> { /* skip */ }

                History.Role.TOOL -> {
                    // Accumulate tool results; they'll be merged into a single user message
                    val blocks = msg.toAnthropicContentBlocks()
                    if (blocks is JsonArray) {
                        pendingToolResults.addAll(blocks)
                    }
                }

                else -> {
                    // Flush any pending tool results as a single user message before the next message
                    if (pendingToolResults.isNotEmpty()) {
                        add(
                            AnthropicChatRequestDto.Message(
                                role = "user",
                                content = JsonArray(pendingToolResults),
                            ),
                        )
                        pendingToolResults = mutableListOf()
                    }
                    add(
                        AnthropicChatRequestDto.Message(
                            role = if (msg.role == History.Role.ASSISTANT) "assistant" else "user",
                            content = msg.toAnthropicContentBlocks(),
                        ),
                    )
                }
            }
        }
        // Flush any trailing tool results
        if (pendingToolResults.isNotEmpty()) {
            add(
                AnthropicChatRequestDto.Message(
                    role = "user",
                    content = JsonArray(pendingToolResults),
                ),
            )
        }
    }

    private suspend fun handleAnthropicChatWithTools(
        credentials: ServiceCredentials,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val contextWindowTokens = ModelCatalog.estimateContextWindow(credentials.modelId)
        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        try {
            while (true) {
                iteration++

                val currentMessages = buildAnthropicMessages(
                    history.value.filter { it.role != History.Role.TOOL_EXECUTING },
                )

                if (iteration > maxToolIterations()) {
                    val bailoutResponse = retryApiCall {
                        requests.anthropicChat(
                            credentials = credentials,
                            messages = currentMessages,
                            systemInstruction = "Please synthesize your best answer based on the information you have gathered so far. $systemPrompt",
                        ).getOrThrow()
                    }
                    return bailoutResponse.extractText()
                }

                val response = retryApiCall {
                    requests.anthropicChat(
                        credentials = credentials,
                        messages = currentMessages,
                        tools = tools,
                        systemInstruction = systemPrompt,
                    ).getOrThrow()
                }

                val toolUseBlocks = response.content.filter { it.type == "tool_use" }
                val toolCallInfos: List<ToolCallInfo>
                val textContent: String

                if (toolUseBlocks.isEmpty()) {
                    val text = response.extractText()
                    val inlineResult = parseInlineToolCalls(text)
                    if (inlineResult == null) {
                        return text
                    }
                    toolCallInfos = inlineResult.toolCalls
                    textContent = inlineResult.cleanContent
                } else {
                    toolCallInfos = toolUseBlocks.map { block ->
                        val argsJson = block.input?.toString() ?: "{}"
                        ToolCallInfo(
                            id = block.id ?: "anthropic-${Uuid.random()}",
                            name = block.name ?: "unknown",
                            arguments = argsJson,
                        )
                    }
                    textContent = response.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("\n")
                        .replace(invokeBlockRegex, "").trim()
                }

                val signatures = toolCallInfos.map { "${it.name}:${it.arguments.hashCode()}" }
                if (isRepeatingToolCalls(recentSignatures, signatures)) {
                    val bailoutResponse = retryApiCall {
                        requests.anthropicChat(
                            credentials = credentials,
                            messages = currentMessages,
                            systemInstruction = "Please synthesize your best answer based on the information you have gathered so far. $systemPrompt",
                        ).getOrThrow()
                    }
                    return bailoutResponse.extractText()
                }
                recentSignatures.addAll(signatures)

                history.update {
                    it.toMutableList().apply {
                        add(
                            History(
                                role = History.Role.ASSISTANT,
                                content = textContent.stripToolMarkup(),
                                toolCalls = toolCallInfos.toImmutableList(),
                            ),
                        )
                    }
                }

                val toolResults = executeToolCallsInParallel(toolCallInfos.map { Triple(it.id, it.name, it.arguments) })

                history.update { h ->
                    val updated = buildList<History>(h.size + toolResults.size) {
                        for (entry in h) {
                            if (entry.role != History.Role.TOOL_EXECUTING) {
                                val cleaned = entry.withoutAskQuestionsToolCall()
                                if (cleaned != null) add(cleaned)
                            }
                        }
                        for ((callId, name, result) in toolResults) {
                            if (name == ASK_QUESTIONS_TOOL_NAME) {
                                add(History(role = History.Role.USER, content = result))
                            } else {
                                add(
                                    History(
                                        role = History.Role.TOOL,
                                        content = result,
                                        toolCallId = callId,
                                        toolName = name,
                                    ),
                                )
                            }
                        }
                    }
                    trimHistoryForContext(updated, systemPrompt?.length ?: 0, contextWindowTokens)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val currentMessages = buildAnthropicMessages(
                    history.value.filter { it.role != History.Role.TOOL_EXECUTING },
                )
                val bailoutResponse = retryApiCall {
                    requests.anthropicChat(
                        credentials = credentials,
                        messages = currentMessages,
                        systemInstruction = systemPrompt,
                    ).getOrThrow()
                }
                return bailoutResponse.extractText()
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                return ""
            }
        }
    }

    /**
     * Strips [ask_questions][ASK_QUESTIONS_TOOL_NAME] tool calls from an ASSISTANT entry.
     * Returns null if the entry becomes empty (no text, no remaining tool calls).
     */
    private fun History.withoutAskQuestionsToolCall(): History? {
        if (role != History.Role.ASSISTANT || toolCalls == null) return this
        val filtered = toolCalls.filter { it.name != ASK_QUESTIONS_TOOL_NAME }
        if (filtered.size == toolCalls.size) return this
        if (filtered.isEmpty() && content.isEmpty()) return null
        return copy(toolCalls = if (filtered.isEmpty()) null else filtered.toImmutableList())
    }

    /**
     * Detects if the current batch of tool calls is repeating a recent pattern.
     */
    private fun isRepeatingToolCalls(recentSignatures: List<String>, currentSignatures: List<String>): Boolean {
        if (currentSignatures.isEmpty()) return false
        // Count how many consecutive times the same signature set appeared at the tail
        val batchSize = currentSignatures.size
        var consecutiveCount = 0
        var i = recentSignatures.size - batchSize
        while (i >= 0) {
            val slice = recentSignatures.subList(i, i + batchSize)
            if (slice == currentSignatures) {
                consecutiveCount++
                i -= batchSize
            } else {
                break
            }
        }
        // +1 for the current batch that's about to be executed
        return consecutiveCount + 1 >= maxRepeatedToolCalls()
    }

    /**
     * Makes a final OpenAI-compatible API call without tools, asking the model to summarize.
     */
    private suspend fun makeFinalCallWithoutTools(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
    ): String {
        val bailoutMessages = messages.toMutableList().apply {
            add(
                com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message(
                    role = "user",
                    content = JsonPrimitive("Please synthesize your best answer based on the information you have gathered so far."),
                ),
            )
        }
        val response = retryApiCall {
            requests.openAICompatibleChat(service, credentials, bailoutMessages).getOrThrow()
        }
        return response.choices.firstOrNull()?.message?.effectiveContent ?: ""
    }

    /**
     * Executes tool calls in parallel, showing TOOL_EXECUTING indicators in the UI.
     * Returns a list of (callId, toolName, result).
     */
    private suspend fun executeToolCallsInParallel(
        toolCalls: List<Triple<String, String, String>>,
    ): List<Triple<String, String, String>> {
        // Add all TOOL_EXECUTING indicators first
        val executingIds = toolCalls.map { Uuid.random().toString() }
        for ((index, toolCall) in toolCalls.withIndex()) {
            val (_, name, _) = toolCall
            val toolDisplayName = toolExecutor.getToolDisplayName(name)
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            id = executingIds[index],
                            role = History.Role.TOOL_EXECUTING,
                            content = name,
                            toolName = toolDisplayName,
                        ),
                    )
                }
            }
        }

        // Execute all tools concurrently, ensuring indicators show for at least 2 seconds.
        // Snapshot the conversation id once so all parallel tool calls in this batch
        // see a stable value even if the user switches conversations mid-flight.
        val conversationIdSnapshot = _currentConversationId.value
        val startTime = Clock.System.now().toEpochMilliseconds()
        try {
            val results = coroutineScope {
                toolCalls.map { (callId, name, arguments) ->
                    async {
                        val result = toolExecutor.executeTool(name, arguments, conversationIdSnapshot)
                        Triple(callId, name, result)
                    }
                }.awaitAll()
            }
            val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
            if (elapsed < MIN_TOOL_DISPLAY_MS) {
                delay((MIN_TOOL_DISPLAY_MS - elapsed).milliseconds)
            }
            return results
        } finally {
            // Always remove TOOL_EXECUTING indicators, even on cancellation
            chatHistory.update { history ->
                history.filter { h -> h.id !in executingIds }
            }
        }
    }

    private fun isNonRetryableException(e: Exception): Boolean = e is AnthropicInsufficientCreditsException || e is OpenAICompatibleQuotaExhaustedException || e is OpenAICompatibleInvalidApiKeyException

    /**
     * Retries an API call with simple exponential backoff.
     */
    private suspend fun <T> retryApiCall(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_API_RETRIES) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isNonRetryableException(e)) throw e
                lastException = e
                if (attempt < MAX_API_RETRIES) {
                    delay((attempt + 1).seconds)
                }
            }
        }
        throw lastException!!
    }

    private fun estimateMessageChars(msg: com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message): Int {
        val contentChars = when (val content = msg.content) {
            is JsonArray -> {
                // Vision messages: only count text parts, not base64 image data
                content.sumOf { element ->
                    val obj = element as? JsonObject
                    val type = (obj?.get("type") as? JsonPrimitive)?.content
                    if (type == "text") {
                        (obj["text"] as? JsonPrimitive)?.content?.length ?: 0
                    } else {
                        100 // Fixed small cost for image references
                    }
                }
            }

            is JsonPrimitive -> content.content.length

            else -> content?.toString()?.length ?: 0
        }
        return contentChars + msg.role.length
    }

    /**
     * Trims messages to fit within the estimated context window by dropping oldest messages
     * (keeping the system prompt and most recent messages).
     */
    private fun trimMessagesForContext(
        messages: List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
        contextWindowTokens: Int = ModelCatalog.DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> {
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN
        val totalChars = messages.sumOf { estimateMessageChars(it) }
        if (totalChars <= maxChars) return messages

        // Keep system prompt (first message if role is "system") and trim from oldest non-system
        val systemMessages = messages.takeWhile { it.role == "system" }
        val nonSystemMessages = messages.drop(systemMessages.size)

        val systemChars = systemMessages.sumOf { estimateMessageChars(it) }
        val availableChars = maxChars - systemChars

        // Keep messages from the end until we exceed the budget
        val kept = mutableListOf<com.oak.app.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>()
        var usedChars = 0
        for (msg in nonSystemMessages.reversed()) {
            val msgChars = estimateMessageChars(msg)
            if (usedChars + msgChars > availableChars) break
            kept.add(0, msg)
            usedChars += msgChars
        }

        return sanitizeToolSequences(systemMessages + kept)
    }

    /**
     * Trims History entries to fit within the estimated context window by dropping oldest messages
     * (keeping the most recent). Used by Gemini and Anthropic tool loops where the system prompt
     * is sent separately (not as a message).
     */
    private fun trimHistoryForContext(
        history: List<History>,
        systemPromptChars: Int = 0,
        contextWindowTokens: Int = ModelCatalog.DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<History> {
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN
        val totalChars = history.sumOf { it.content.length } + systemPromptChars
        if (totalChars <= maxChars) return history

        val availableChars = maxChars - systemPromptChars

        // Keep messages from the end until we exceed the budget
        val kept = mutableListOf<History>()
        var usedChars = 0
        for (msg in history.reversed()) {
            val msgChars = msg.content.length
            if (usedChars + msgChars > availableChars) break
            kept.add(0, msg)
            usedChars += msgChars
        }

        return kept
    }

    /**
     * Auto-trigger compaction when history exceeds the threshold.
     * Delegates to [compactHistory] with defaults.
     */
    private suspend fun compactHistoryIfNeeded() {
        compactHistory(keepRecent = COMPACTION_KEEP_RECENT, focus = null)
    }

    /**
     * Compacts chat history by summarizing older messages via an LLM call.
     * Keeps recent exchanges verbatim and replaces older ones with a summary.
     * Also compresses oversized tool outputs before summarization.
     * Falls back to dropping old messages if summarization fails.
     *
     * @param keepRecent Number of recent user exchanges to keep verbatim.
     * @param focus What to emphasize in the summary ('all', 'decisions', 'code', 'facts', or null for 'all').
     * @return A map describing the result.
     */
    private suspend fun compactHistory(
        keepRecent: Int = COMPACTION_KEEP_RECENT,
        focus: String? = null,
    ): Map<String, Any> {
        val firstInstance = getConfiguredServiceInstances().firstOrNull()
        if (firstInstance == null) {
            return mapOf("success" to false, "error" to "No service configured")
        }
        val service = Service.fromId(firstInstance.serviceId)
        val selectedModelId = appSettings.getSelectedModelId(service)

        // For on-device services, use the local model's actual max context tokens
        // instead of ModelCatalog's default (100K) which is way too high for local models
        val contextWindowTokens = if (service.isOnDevice && localInferenceEngine != null) {
            val localModel = localInferenceEngine.getAvailableModels().find { it.id == selectedModelId }
            localModel?.maxContextTokens ?: ModelCatalog.estimateContextWindow(selectedModelId)
        } else {
            ModelCatalog.estimateContextWindow(selectedModelId)
        }

        var history = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
        val systemPromptChars = getActiveSystemPrompt()?.length ?: 0
        val totalChars = history.sumOf { it.content.length } + systemPromptChars
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN

        // If auto-triggered, check threshold; manual calls always proceed
        if (focus == null && totalChars <= (maxChars * COMPACTION_THRESHOLD).toInt()) {
            return mapOf("success" to true, "compacted" to false, "reason" to "Below compaction threshold")
        }

        // Step 1: Compress oversized tool outputs (>5K chars) to short summaries
        val toolResultCompressThreshold = 5_000
        var toolOutputsCompressed = 0
        history = history.map { msg ->
            if (msg.role == History.Role.TOOL && msg.content.length > toolResultCompressThreshold) {
                toolOutputsCompressed++
                val toolLabel = msg.toolName?.let { " ($it)" } ?: ""
                val compressed = msg.content.smartTruncate(500)
                msg.copy(
                    content = "[Tool result$toolLabel compressed: ${msg.content.length} chars → ${compressed.length} chars]\n$compressed",
                )
            } else {
                msg
            }
        }

        // Split history: older messages to summarize, recent to keep verbatim
        val userIndices = history.mapIndexedNotNull { index, h ->
            if (h.role == History.Role.USER) index else null
        }
        if (userIndices.isEmpty()) {
            return mapOf("success" to true, "compacted" to false, "reason" to "No user messages to compact")
        }
        val effectiveKeepRecent = keepRecent.coerceIn(1, userIndices.size)
        val cutoffIndex = userIndices[userIndices.size - effectiveKeepRecent]
        val olderMessages = history.subList(0, cutoffIndex)
        val recentMessages = history.subList(cutoffIndex, history.size)

        if (olderMessages.isEmpty()) {
            return mapOf("success" to true, "compacted" to false, "reason" to "No old messages to compact")
        }

        // Build a transcript of the older messages for summarization
        val transcript = buildString {
            for (msg in olderMessages) {
                when (msg.role) {
                    History.Role.USER -> appendLine("User: ${msg.content}")

                    History.Role.ASSISTANT -> appendLine("Assistant: ${msg.content}")

                    History.Role.TOOL -> {
                        val preview = msg.content.take(200).replace('\n', ' ')
                        appendLine("Tool${msg.toolName?.let { " ($it)" } ?: ""}: $preview")
                    }

                    else -> {}
                }
            }
        }

        val focusInstruction = when (focus) {
            "decisions" -> "Focus on key decisions made and why."
            "code" -> "Focus on code changes, file modifications, and technical details."
            "facts" -> "Focus on facts, user preferences, and important information."
            else -> "Preserve key facts, decisions, code changes, and any information the assistant would need to continue helping."
        }

        val summaryPrompt = "Summarize this conversation concisely. $focusInstruction Be brief but complete:\n\n$transcript"

        val summary = try {
            askSilently(summaryPrompt)
        } catch (_: Exception) {
            // Summarization failed — fall back to dropping old messages
            chatHistory.value = recentMessages
            return mapOf(
                "success" to true,
                "compacted" to true,
                "method" to "drop",
                "dropped_messages" to olderMessages.size,
                "tool_outputs_compressed" to toolOutputsCompressed,
            )
        }

        val summaryEntry = History(
            role = History.Role.ASSISTANT,
            content = "[Conversation summary: $summary]",
        )

        chatHistory.value = listOf(summaryEntry) + recentMessages

        return mapOf(
            "success" to true,
            "compacted" to true,
            "method" to "summarize",
            "summarized_messages" to olderMessages.size,
            "tool_outputs_compressed" to toolOutputsCompressed,
            "kept_recent" to effectiveKeepRecent,
        )
    }

    override suspend fun triggerCompaction(keepRecent: Int, focus: String?): Map<String, Any> = compactHistory(keepRecent = keepRecent, focus = focus)

    private fun trimToRecentExchanges(history: List<History>, maxExchanges: Int): List<History> {
        val userIndices = history.mapIndexedNotNull { index, h ->
            if (h.role == History.Role.USER) index else null
        }
        if (userIndices.size <= maxExchanges) return history
        val cutoffIndex = userIndices[userIndices.size - maxExchanges]
        return history.subList(cutoffIndex, history.size)
    }

    private suspend fun saveCurrentConversation() {
        val conversationId = askForConversationId ?: _currentConversationId.value ?: Uuid.random().toString().also {
            setCurrentConversationId(it)
        }

        val history = trimToRecentExchanges(chatHistory.value, 20)
        if (history.isEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()

        val existingConversation = savedConversations.value.find { it.id == conversationId }

        val title = existingConversation?.title?.ifEmpty { null }
            ?: deriveTitle(history)
        val conversation = Conversation(
            id = conversationId,
            messages = history
                .filter { it.role != History.Role.TOOL_EXECUTING }
                .map { h ->
                    Conversation.Message(
                        id = h.id,
                        role = when (h.role) {
                            History.Role.USER -> "user"
                            History.Role.ASSISTANT -> "assistant"
                            History.Role.TOOL -> "tool"
                            History.Role.TOOL_EXECUTING -> "tool" // Should not happen due to filter
                        },
                        content = h.content,
                        reasoningContent = h.reasoningContent,
                        attachments = h.attachments,
                        uiSubmission = h.uiSubmission,
                        isThinking = h.isThinking,
                        toolCalls = h.toolCalls?.map { tc ->
                            Conversation.ToolCallInfoData(tc.id, tc.name, tc.arguments, tc.thoughtSignature)
                        },
                        toolCallId = h.toolCallId,
                        toolName = h.toolName,
                    )
                },
            createdAt = existingConversation?.createdAt ?: now,
            updatedAt = now,
            title = title,
            type = existingConversation?.type ?: if (interactiveModeFlag) Conversation.TYPE_INTERACTIVE else Conversation.TYPE_CHAT,
            excludedSkillIds = _currentExcludedSkillIds.value,
        )

        conversationStorage.saveConversation(conversation)
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun supportedFileExtensions(): List<String> {
        val service = currentService()
        if (service.isOnDevice) return emptyList()
        return if (service.supportsPdf) supportedFileExtensions + "pdf" else supportedFileExtensions
    }

    private fun firstRunnableInstance(): ServiceInstance? = getConfiguredServiceInstances().firstOrNull { instance ->
        val service = Service.fromId(instance.serviceId)
        !service.isOnDevice || localInferenceEngine != null
    }

    override fun currentService(): Service = firstRunnableInstance()?.let { Service.fromId(it.serviceId) } ?: Service.OpenAICompatible

    private fun setCurrentConversationId(id: String?) {
        _currentConversationId.value = id
        appSettings.setCurrentConversationId(id)
    }

    // Conversation management
    override fun loadConversations() {
        conversationStorage.loadConversations()
    }

    override fun loadConversation(id: String) {
        // Never swap chatHistory while a generation is writing to it
        if (id != askForConversationId && askForConversationId != null) return

        val conversation = savedConversations.value.find { it.id == id } ?: return

        setCurrentConversationId(id)
        _currentExcludedSkillIds.value = conversation.excludedSkillIds
        chatHistory.value = conversation.messages.map { m ->
            // Prefer the modern `attachments` field. Fall back to the legacy single-file
            // fields for conversations saved before multi-attachment support.
            val attachments = when {
                m.attachments.isNotEmpty() -> m.attachments.toImmutableList()

                m.data != null && m.mimeType != null ->
                    persistentListOf(Attachment(data = m.data, mimeType = m.mimeType, fileName = m.fileName))

                else -> persistentListOf()
            }
            History(
                id = m.id,
                role = when (m.role) {
                    "user" -> History.Role.USER
                    "tool" -> History.Role.TOOL
                    else -> History.Role.ASSISTANT
                },
                content = m.content,
                reasoningContent = m.reasoningContent,
                attachments = attachments,
                uiSubmission = m.uiSubmission,
                isThinking = m.isThinking,
                toolCalls = m.toolCalls?.map { tc ->
                    ToolCallInfo(id = tc.id, name = tc.name, arguments = tc.arguments, thoughtSignature = tc.thoughtSignature)
                }?.toImmutableList(),
                toolCallId = m.toolCallId,
                toolName = m.toolName,
            )
        }
    }

    override suspend fun deleteConversation(id: String) {
        if (_currentConversationId.value == id) {
            setCurrentConversationId(null)
            chatHistory.value = emptyList()
        }
        conversationStorage.deleteConversation(id)
        // Drop the per-conversation shell session so a future conversation reusing
        // this id (very unlikely — random uuids) doesn't inherit stale state, and
        // memory is freed.
        sandboxController.closeSession(id)
    }

    override fun regenerate() {
        chatHistory.update { history ->
            val lastUserIndex = history.indexOfLast { it.role == History.Role.USER }
            if (lastUserIndex >= 0) {
                history.subList(0, lastUserIndex + 1)
            } else {
                history
            }
        }
    }

    override fun startNewChat() {
        setCurrentConversationId(null)
        _currentExcludedSkillIds.value = emptySet()
        chatHistory.value = emptyList()
    }

    override fun popLastExchange() {
        chatHistory.update { history ->
            val lastUserIndex = history.indexOfLast { it.role == History.Role.USER }
            if (lastUserIndex >= 0) history.take(lastUserIndex) else history
        }
    }

    override fun truncateFrom(messageId: String) {
        chatHistory.update { history ->
            val index = history.indexOfFirst { it.id == messageId }
            if (index >= 0) history.take(index) else history
        }
    }

    override fun restoreCurrentConversation() {
        // One-time migration for existing users: pin the latest conversation as the new
        // "current" pointer so the upgrade is non-disruptive.
        if (!appSettings.isCurrentConversationMigrated()) {
            val latest = savedConversations.value.maxByOrNull { it.updatedAt }
            if (latest != null) {
                loadConversation(latest.id)
            }
            appSettings.markCurrentConversationMigrated()
            return
        }

        // Already-loaded guard (covers re-entry from refreshSettings)
        val currentId = _currentConversationId.value
        if (currentId != null && chatHistory.value.isNotEmpty() &&
            savedConversations.value.any { it.id == currentId }
        ) {
            return
        }

        val persistedId = appSettings.getCurrentConversationId()
        if (persistedId != null && savedConversations.value.any { it.id == persistedId }) {
            loadConversation(persistedId)
        }
        // else: null id or stale id → leave history empty (this is the new-empty-chat state)
    }

    // Tool management
    override fun getToolDefinitions(): List<ToolInfo> = getPlatformToolDefinitions()
        .filter { it.id !in CommonTools.masterToggleControlledToolIds }
        .map { it.copy(isEnabled = appSettings.isToolEnabled(it.id, defaultEnabled = it.isEnabled)) }

    override fun setToolEnabled(toolId: String, enabled: Boolean) {
        appSettings.setToolEnabled(toolId, enabled)
    }

    // MCP servers
    override fun getMcpServers(): List<McpServerConfig> = mcpServerManager.getServers()

    override suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>): McpServerConfig = mcpServerManager.addServer(name, url, headers)

    override fun removeMcpServer(serverId: String) {
        mcpServerManager.removeServer(serverId)
    }

    override fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        mcpServerManager.setServerEnabled(serverId, enabled)
    }

    override suspend fun connectMcpServer(serverId: String): Result<List<ToolInfo>> {
        val result = mcpServerManager.connectAndDiscoverTools(serverId)
        return result.map { mcpServerManager.getToolsForServer(serverId) }
    }

    override fun getMcpToolsForServer(serverId: String): List<ToolInfo> = mcpServerManager.getToolsForServer(serverId)

    override fun isMcpServerConnected(serverId: String): Boolean = mcpServerManager.isConnected(serverId)

    override suspend fun connectEnabledMcpServers() {
        mcpServerManager.connectEnabledServers()
    }

    // SSH Servers
    override fun getSshServers(): List<SshServerConfig> = sshServerManager.getServers()

    override suspend fun addSshServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        authType: SshAuthType,
        password: String,
        privateKey: String,
        passphrase: String,
    ): SshServerConfig = sshServerManager.addServer(
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        password = password,
        privateKey = privateKey,
        passphrase = passphrase,
    )

    override suspend fun removeSshServer(serverId: String) = sshServerManager.removeServer(serverId)

    override suspend fun setSshServerEnabled(serverId: String, enabled: Boolean) = sshServerManager.setServerEnabled(serverId, enabled)

    override suspend fun connectSshServer(serverId: String): Result<Unit> = sshServerManager.connectServer(serverId)

    override fun isSshServerConnected(serverId: String): Boolean = sshServerManager.isConnected(serverId)

    override suspend fun disconnectSshServer(serverId: String) {
        sshServerManager.disconnectClient(serverId)
    }

    override suspend fun connectEnabledSshServers() = sshServerManager.connectEnabledServers()

    // Soul (system prompt)
    override fun getSoulText(): String = appSettings.getSoulText()

    override fun setSoulText(text: String) {
        appSettings.setSoulText(text)
    }

    override suspend fun getActiveSystemPrompt(variant: SystemPromptVariant): String? {
        val soul = appSettings.getSoulText().ifEmpty { getString(Res.string.default_soul) }
        val memoryEnabled = appSettings.isMemoryEnabled()
        val schedulingEnabled = appSettings.isSchedulingEnabled()

        val memoryInstructions = if (memoryEnabled) {
            appSettings.getMemoryInstructions().ifEmpty { null }
        } else {
            null
        }

        val memories = if (memoryEnabled) memoryStore.getAllMemories() else emptyList()
        val byCategory = memories.groupBy { it.category }

        val tasksSplit = if (schedulingEnabled) taskStore.getPendingTasksPartitioned() else PendingTaskPartition(emptyList(), emptyList())
        val pendingTasks = tasksSplit.scheduled
        val heartbeatAdditions = tasksSplit.heartbeatAdditions

        // Surface connected email accounts so the AI knows they exist in regular chat,
        // not just during heartbeats. Only the remote variant uses this — email tools
        // aren't in the local allowlist. Gated on the email toggle: if the user has email
        // off, the AI shouldn't reference the accounts.
        val emailAccounts = if (variant == SystemPromptVariant.CHAT_REMOTE && appSettings.isEmailEnabled()) {
            emailStore.getAccounts().map { account ->
                val state = emailStore.getSyncState(account.id)
                EmailAccountSummary(
                    email = account.email,
                    unreadCount = state.unreadCount,
                    lastSyncEpochMs = state.lastSyncEpochMs,
                    lastError = state.lastError,
                )
            }
        } else {
            emptyList()
        }

        val service = currentService()
        // On-device services store the active model ID per-instance, not globally, so
        // `getSelectedModelId` comes back blank for LiteRT. Fall back to the first
        // configured on-device instance's model ID in that case.
        val modelId = appSettings.getSelectedModelId(service).ifBlank {
            if (service.isOnDevice) {
                getConfiguredServiceInstances()
                    .firstOrNull { Service.fromId(it.serviceId).isOnDevice }
                    ?.let { appSettings.getInstanceModelId(it.instanceId) }
                    .orEmpty()
            } else {
                ""
            }
        }
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val localDateTime = now.toLocalDateTime(timeZone)
        val offset = timeZone.offsetAt(now)
        val runtime = ChatPromptRuntimeContext(
            nowLocalIsoWithOffset = "$localDateTime$offset",
            timeZoneId = timeZone.id,
            nowUtcIsoString = now.toString(),
            platform = currentPlatform.displayName,
            modelId = modelId,
            providerName = service.displayName,
        )

        val isLimited = !supportsTools(modelId)
        val uiMode = when {
            interactiveModeFlag -> ChatPromptUiMode.INTERACTIVE_UI
            appSettings.isDynamicUiEnabled() && !isLimited -> ChatPromptUiMode.DYNAMIC_UI
            else -> ChatPromptUiMode.NONE
        }

        val excludedIds = _currentExcludedSkillIds.value
        val activeSkills = getSkills().filter { skill ->
            skill.isEnabled && skill.id !in excludedIds
        }.map { skill ->
            // Build dynamic email skill content based on connected accounts
            if (skill.id == Skill.EMAIL_SKILL_ID && emailAccounts.isNotEmpty()) {
                skill.copy(content = buildEmailSkillContent(emailAccounts))
            } else {
                skill
            }
        }

        return buildChatSystemPrompt(
            variant = variant,
            soul = soul,
            memoryInstructions = memoryInstructions,
            generalMemories = byCategory[MemoryCategory.GENERAL].orEmpty(),
            preferenceMemories = byCategory[MemoryCategory.PREFERENCE].orEmpty(),
            learningMemories = byCategory[MemoryCategory.LEARNING].orEmpty(),
            errorMemories = byCategory[MemoryCategory.ERROR].orEmpty(),
            pendingTasks = pendingTasks,
            heartbeatAdditions = heartbeatAdditions,
            emailAccounts = emailAccounts,
            runtime = runtime,
            uiMode = uiMode,
            activeSkills = activeSkills,
        ).ifEmpty { null }
    }

    private fun buildEmailSkillContent(accounts: List<EmailAccountSummary>): String = buildString {
        append("The user has these email accounts connected. Use them via the existing email tools — ")
        append("do NOT suggest adding, re-authenticating, or connecting a new account unless the user explicitly asks.\n")
        append("**Sending policy**: before calling `compose_email` or `reply_email`, present the full draft (to, subject, body) in chat and get explicit confirmation (\"send it\" / \"looks good\" / \"yes\"). Never call the send tools on the same turn you draft — the user must have a chance to correct tone, recipients, or content first. If the user later says \"change X and send\", re-present the updated draft and confirm again.\n")
        for (account in accounts) {
            append("- **")
            append(account.email)
            append("**: ")
            if (account.lastError != null) {
                append("sync failing — ")
                append(account.lastError)
            } else {
                append(account.unreadCount)
                append(" unread")
                if (account.lastSyncEpochMs > 0) {
                    append(" (last sync: ")
                    append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs))
                    append(')')
                }
            }
            append('\n')
        }
    }

    override fun isDynamicUiEnabled(): Boolean = appSettings.isDynamicUiEnabled()

    override fun setDynamicUiEnabled(enabled: Boolean) {
        appSettings.setDynamicUiEnabled(enabled)
    }

    // Skills

    /** Cached skill list — populated on first read, invalidated by write operations. */
    private var _cachedSkills: List<Skill>? = null

    override fun getSkills(): List<Skill> {
        _cachedSkills?.let { return it }
        val json = appSettings.getSkillsJson()
        val result = Skill.fromJson(json, SharedJson)
        if (json.isBlank() || json == "[]") {
            saveSkills(result)
        } else {
            // Check if built-ins were merged in (upgrade scenario)
            val storedIds = try {
                SharedJson.decodeFromString<List<Skill>>(json).map { it.id }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
            if (Skill.BUILT_IN_SKILLS.any { it.id !in storedIds }) {
                saveSkills(result)
            }
        }
        _cachedSkills = result
        return result
    }

    private fun invalidateSkillCache() {
        _cachedSkills = null
    }

    override fun setSkillEnabled(skillId: String, enabled: Boolean) {
        val skills = getSkills().toMutableList()
        val index = skills.indexOfFirst { it.id == skillId }
        if (index >= 0) {
            skills[index] = skills[index].copy(isEnabled = enabled)
            saveSkills(skills)
            invalidateSkillCache()
        }
        if (!enabled) {
            val currentExcluded = _currentExcludedSkillIds.value.toMutableSet()
            if (currentExcluded.remove(skillId)) {
                _currentExcludedSkillIds.value = currentExcluded
            }
            updateAllConversationExcludedIds { excluded ->
                if (skillId in excluded) excluded - skillId else null
            }
        }
    }

    override fun removeSkill(skillId: String) {
        val skills = getSkills()
        val skill = skills.find { it.id == skillId }
        if (skill?.isBuiltIn == true) return
        saveSkills(skills.filter { it.id != skillId })
        invalidateSkillCache()
        val currentExcluded = _currentExcludedSkillIds.value.toMutableSet()
        if (currentExcluded.remove(skillId)) {
            _currentExcludedSkillIds.value = currentExcluded
        }
        updateAllConversationExcludedIds { excluded ->
            if (skillId in excluded) excluded - skillId else null
        }
    }

    override fun importSkill(skill: Skill) {
        val skills = getSkills().toMutableList()
        val existingIndex = skills.indexOfFirst { it.id == skill.id }
        if (existingIndex >= 0) {
            skills[existingIndex] = skill
        } else {
            skills.add(skill)
        }
        saveSkills(skills)
        invalidateSkillCache()
    }

    override fun getExcludedSkillIds(): Set<String> = _currentExcludedSkillIds.value

    override fun excludeSkill(skillId: String) {
        val excluded = _currentExcludedSkillIds.value.toMutableSet()
        excluded.add(skillId)
        _currentExcludedSkillIds.value = excluded
    }

    override fun includeSkill(skillId: String) {
        val excluded = _currentExcludedSkillIds.value.toMutableSet()
        excluded.remove(skillId)
        _currentExcludedSkillIds.value = excluded
    }

    override fun getSkillEnabledTools(): Set<String> {
        val excludedIds = _currentExcludedSkillIds.value
        return getSkills()
            .filter { it.isEnabled && it.id !in excludedIds }
            .flatMap { it.requiredTools }
            .toSet()
    }

    /**
     * Batch-update [Conversation.excludedSkillIds] across all saved conversations.
     * [transform] receives the current excluded set and returns the updated set,
     * or null if no change is needed for that conversation.
     */
    private fun updateAllConversationExcludedIds(transform: (Set<String>) -> Set<String>?) {
        for (conversation in savedConversations.value) {
            val updated = transform(conversation.excludedSkillIds) ?: continue
            conversationStorage.saveConversation(conversation.copy(excludedSkillIds = updated))
        }
    }

    private fun saveSkills(skills: List<Skill>) {
        val json = SharedJson.encodeToString(skills)
        appSettings.setSkillsJson(json)
    }

    override fun getThemeMode(): ThemeMode = appSettings.getThemeMode()

    override fun setThemeMode(mode: ThemeMode) {
        appSettings.setThemeMode(mode)
    }

    override fun isUseDynamicColorsEnabled(): Boolean = appSettings.isUseDynamicColorsEnabled()

    override fun setUseDynamicColorsEnabled(enabled: Boolean) {
        appSettings.setUseDynamicColorsEnabled(enabled)
    }

    override fun getFontFamily(): OakFontFamily = appSettings.getFontFamily()

    override fun setFontFamily(family: OakFontFamily) {
        appSettings.setFontFamily(family)
    }

    override fun getAiFontFamily(): OakFontFamily = appSettings.getAiFontFamily()

    override fun setAiFontFamily(family: OakFontFamily) {
        appSettings.setAiFontFamily(family)
    }

    private var interactiveModeFlag = appSettings.getCurrentInteractiveMode()

    override fun setInteractiveMode(enabled: Boolean) {
        interactiveModeFlag = enabled
        appSettings.setCurrentInteractiveMode(enabled)
    }

    override fun isInteractiveModeActive(): Boolean = interactiveModeFlag

    override fun isMemoryEnabled(): Boolean = appSettings.isMemoryEnabled()

    override fun setMemoryEnabled(enabled: Boolean) {
        appSettings.setMemoryEnabled(enabled)
    }

    override fun getMemories(): List<MemoryEntry> = memoryStore.getAllMemories()

    override suspend fun deleteMemory(key: String) {
        memoryStore.forget(key)
    }

    override suspend fun updateMemoryContent(key: String, content: String) {
        memoryStore.updateContent(key, content)
    }

    override fun isSchedulingEnabled(): Boolean = appSettings.isSchedulingEnabled()

    override fun setSchedulingEnabled(enabled: Boolean) {
        appSettings.setSchedulingEnabled(enabled)
    }

    override fun getScheduledTasks(): List<ScheduledTask> = taskStore.getAllTasks()

    override suspend fun cancelScheduledTask(id: String) {
        taskStore.removeTask(id)
    }

    override fun isDaemonEnabled(): Boolean = appSettings.isDaemonEnabled()

    override fun setDaemonEnabled(enabled: Boolean) {
        appSettings.setDaemonEnabled(enabled)
    }

    override fun isSandboxEnabled(): Boolean = appSettings.isSandboxEnabled()

    override fun setSandboxEnabled(enabled: Boolean) {
        appSettings.setSandboxEnabled(enabled)
    }

    override fun isStorageAccessEnabled(): Boolean = appSettings.isStorageAccessEnabled()

    override fun setStorageAccessEnabled(enabled: Boolean) {
        appSettings.setStorageAccessEnabled(enabled)
    }

    override fun getHeartbeatConfig(): HeartbeatConfig = heartbeatManager.getConfig()

    override fun setHeartbeatEnabled(enabled: Boolean) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(enabled = enabled))
    }

    override fun setHeartbeatIntervalMinutes(minutes: Int) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(intervalMinutes = minutes))
    }

    override fun setHeartbeatActiveHours(start: Int, end: Int) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(activeHoursStart = start, activeHoursEnd = end))
    }

    override fun getHeartbeatPrompt(): String = appSettings.getHeartbeatPrompt()

    override fun setHeartbeatPrompt(text: String) {
        appSettings.setHeartbeatPrompt(text)
    }

    override fun getHeartbeatLog(): List<HeartbeatLogEntry> = heartbeatManager.getHeartbeatLog()

    override fun getHeartbeatInstanceId(): String? = heartbeatManager.getConfig().heartbeatInstanceId

    override fun setHeartbeatInstanceId(instanceId: String?) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(heartbeatInstanceId = instanceId))
    }

    override fun isEmailEnabled(): Boolean = appSettings.isEmailEnabled()

    override fun setEmailEnabled(enabled: Boolean) {
        appSettings.setEmailEnabled(enabled)
    }

    override fun getEmailAccounts(): List<EmailAccount> = emailStore.getAccounts()

    override suspend fun removeEmailAccount(id: String) {
        emailStore.removeAccount(id)
    }

    override fun getEmailPollIntervalMinutes(): Int = appSettings.getEmailPollIntervalMinutes()

    override fun getPendingEmailCount(): Int = emailStore.getPending().size

    override fun getEmailSyncStates(): Map<String, EmailSyncState> = emailStore.getAllSyncStates()

    override suspend fun pollEmailAccount(accountId: String) {
        val account = emailStore.getAccount(accountId) ?: return
        emailPoller.poll(account)
    }

    override fun setEmailPollIntervalMinutes(minutes: Int) {
        appSettings.setEmailPollIntervalMinutes(minutes)
    }

    override fun isSmsEnabled(): Boolean = appSettings.isSmsEnabled()

    override fun setSmsEnabled(enabled: Boolean) {
        appSettings.setSmsEnabled(enabled)
    }

    override fun getSmsPollIntervalMinutes(): Int = appSettings.getSmsPollIntervalMinutes()

    override fun setSmsPollIntervalMinutes(minutes: Int) {
        appSettings.setSmsPollIntervalMinutes(minutes)
    }

    override fun getPendingSmsCount(): Int = smsStore.getPending().size

    override fun getSmsSyncState(): SmsSyncState = smsStore.getSyncState()

    override fun hasSmsPermission(): Boolean = smsReader.hasPermission()

    override suspend fun requestSmsPermission(): Boolean = smsPermissionController.requestPermission()

    override suspend fun pollSms() {
        smsPoller.poll()
    }

    override fun isSmsSendEnabled(): Boolean = appSettings.isSmsSendEnabled()

    override fun setSmsSendEnabled(enabled: Boolean) {
        appSettings.setSmsSendEnabled(enabled)
    }

    override fun hasSmsSendPermission(): Boolean = smsSender.hasPermission()

    override suspend fun requestSmsSendPermission(): Boolean = smsSendPermissionController.requestPermission()

    override val smsDrafts: StateFlow<List<SmsDraft>> = smsDraftStore.drafts

    // Flips the draft to SENDING, delegates to SmsSender, then updates to SENT/FAILED.
    // Explicit user-triggered (never AI-triggered) — the banner is the gate.
    override suspend fun sendSmsDraft(draftId: String): Boolean {
        val draft = smsDraftStore.getDraft(draftId) ?: return false
        if (draft.status != SmsDraftStatus.PENDING) return false
        smsDraftStore.updateStatus(draftId, SmsDraftStatus.SENDING)
        return when (val result = smsSender.send(draft.address, draft.body)) {
            is SmsSendResult.Success -> {
                smsDraftStore.updateStatus(draftId, SmsDraftStatus.SENT)
                true
            }

            is SmsSendResult.Failure -> {
                smsDraftStore.updateStatus(draftId, SmsDraftStatus.FAILED, result.message)
                false
            }
        }
    }

    override suspend fun discardSmsDraft(draftId: String) {
        smsDraftStore.removeDraft(draftId)
    }

    override fun isNotificationsEnabled(): Boolean = appSettings.isNotificationsEnabled()

    override fun setNotificationsEnabled(enabled: Boolean) {
        appSettings.setNotificationsEnabled(enabled)
    }

    override fun isNotificationListenerAccessGranted(): Boolean = notificationListenerController.isAccessGranted()

    override fun openNotificationListenerSettings() {
        notificationListenerController.openAccessSettings()
    }

    override fun getPendingNotificationCount(): Int = notificationStore.getPending().size

    override fun getNotificationSyncState(): NotificationSyncState = notificationStore.getSyncState()

    override suspend fun clearPendingNotifications() {
        notificationStore.clearPending()
    }

    override fun getUiScale(): Float = appSettings.getUiScale()

    override fun setUiScale(scale: Float) {
        appSettings.setUiScale(scale)
    }

    override fun exportSettingsToJson(sections: Set<ImportSection>): String {
        val toolIds = getPlatformToolDefinitions().map { it.id }
        val jsonObject = appSettings.exportToJson(toolIds, sections)
        return prettyJson.encodeToString(JsonObject.serializer(), jsonObject)
    }

    override fun getExportPreview(): Map<ImportSection, String?> {
        val toolIds = getPlatformToolDefinitions().map { it.id }
        val jsonObject = appSettings.exportToJson(toolIds)
        return detectExportableSections(jsonObject)
    }

    override fun importSettingsFromJson(json: String, sections: Set<ImportSection>, replace: Boolean): Int {
        val jsonObject = SharedJson.parseToJsonElement(json).jsonObject
        val toolIds = getPlatformToolDefinitions().map { it.id }
        return appSettings.importFromJson(jsonObject, toolIds, sections, replace)
    }

    override suspend fun askWithTools(prompt: String, instanceId: String?): String {
        // Selection: explicit instance > first remote > first on-device. The simple-tool
        // allowlist works at any context size, so on-device is always eligible for fallback.
        val instances = getConfiguredServiceInstances()
        val targetInstance = instanceId?.let { id -> instances.find { it.instanceId == id } }
            ?: instances.firstOrNull { !Service.fromId(it.serviceId).isOnDevice }
            ?: instances.firstOrNull { Service.fromId(it.serviceId).isOnDevice }
            ?: return ""
        val service = Service.fromId(targetInstance.serviceId)
        val messages = listOf(History(role = History.Role.USER, content = prompt))
        val systemPrompt = getActiveSystemPrompt()
        // Use a local history to avoid polluting the current conversation's chatHistory
        val localHistory = MutableStateFlow(messages)
        return askWithService(service, messages, systemPrompt, targetInstance.instanceId, localHistory)
    }

    override suspend fun askSilently(question: String): String {
        val instance = firstRunnableInstance() ?: return ""
        val service = Service.fromId(instance.serviceId)
        val messages = listOf(History(role = History.Role.USER, content = question))

        if (service.isOnDevice) {
            // Throwaway history — we don't want tool-execution rows leaking into the
            // visible chatHistory for a "silent" call. LOCAL variant of the system
            // prompt so small on-device models get the right section set.
            val localPrompt = getActiveSystemPrompt(SystemPromptVariant.CHAT_LOCAL)
            return askWithLocalEngine(messages, localPrompt, instance.instanceId, MutableStateFlow(messages))
        }

        val systemPrompt = getActiveSystemPrompt()

        val creds = instanceCredentials(instance.instanceId, service)

        val responseText = when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                response.extractText()
            }

            Service.Anthropic -> {
                val anthropicMessages = buildAnthropicMessages(messages)
                val response = requests.anthropicChat(creds, anthropicMessages, systemInstruction = systemPrompt).getOrThrow()
                response.extractText()
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                val response = requests.openAICompatibleChat(service, creds, openAIMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.effectiveContent ?: ""
            }
        }

        return responseText
    }

    override suspend fun askSilentlyWithInstance(instanceId: String, prompt: String, timeoutMs: Long): String {
        val instance = getConfiguredServiceInstances().find { it.instanceId == instanceId }
            ?: return askSilently(prompt)
        val service = Service.fromId(instance.serviceId)
        val messages = listOf(History(role = History.Role.USER, content = prompt))

        if (service.isOnDevice) {
            return askWithLocalEngine(messages, null, instanceId, MutableStateFlow(messages))
        }

        val creds = instanceCredentials(instanceId, service)
        val reqTimeout = if (timeoutMs > 0) timeoutMs else null

        return when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(creds, geminiMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.extractText()
            }

            Service.Anthropic -> {
                val anthropicMessages = buildAnthropicMessages(messages)
                val response = requests.anthropicChat(creds, anthropicMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.extractText()
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, null)
                val response = requests.openAICompatibleChat(service, creds, openAIMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.choices.firstOrNull()?.message?.effectiveContent ?: ""
            }
        }
    }

    private val _hasUnreadHeartbeat = MutableStateFlow(false)
    override val hasUnreadHeartbeat: StateFlow<Boolean> = _hasUnreadHeartbeat

    override fun clearUnreadHeartbeat() {
        _hasUnreadHeartbeat.value = false
    }

    private val _openHeartbeatRequested = MutableStateFlow(false)
    override val openHeartbeatRequested: StateFlow<Boolean> = _openHeartbeatRequested

    override fun requestOpenHeartbeat() {
        _openHeartbeatRequested.value = true
    }

    override fun consumeOpenHeartbeatRequest() {
        _openHeartbeatRequested.value = false
    }

    override suspend fun addAssistantMessage(content: String) {
        val now = Clock.System.now().toEpochMilliseconds()

        val existing = savedConversations.value.find { it.type == Conversation.TYPE_HEARTBEAT }
        val heartbeatId = existing?.id ?: Uuid.random().toString()

        val newMessage = Conversation.Message(
            id = Uuid.random().toString(),
            role = "assistant",
            content = content,
        )

        val messages = ((existing?.messages ?: emptyList()) + newMessage).takeLast(MAX_HEARTBEAT_MESSAGES)

        val conversation = Conversation(
            id = heartbeatId,
            messages = messages,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            type = Conversation.TYPE_HEARTBEAT,
        )

        _hasUnreadHeartbeat.value = true
        conversationStorage.saveConversation(conversation)
    }

    private fun deriveTitle(history: List<History>): String {
        val firstUserMessage = history.firstOrNull { it.role == History.Role.USER }?.content ?: return ""
        return if (firstUserMessage.length <= 50) {
            firstUserMessage
        } else {
            val truncated = firstUserMessage.take(50)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > 20) truncated.substring(0, lastSpace) + "..." else truncated + "..."
        }
    }

    // On-device inference (LiteRT)

    override fun isLocalInferenceAvailable(): Boolean = localInferenceEngine != null

    override fun getLocalEngineState(): StateFlow<EngineState>? = localInferenceEngine?.engineState

    override fun getLocalDownloadingModelId(): StateFlow<String?>? = localInferenceEngine?.downloadingModelId

    override fun getLocalDownloadProgress(): StateFlow<Float?>? = localInferenceEngine?.downloadProgress

    override fun getLocalDownloadError(): StateFlow<DownloadError?>? = localInferenceEngine?.downloadError

    override fun getLocalDownloadedModels(): List<DownloadedModel> = localInferenceEngine?.getDownloadedModels() ?: emptyList()

    override fun getLocalAvailableModels(): List<LocalModel> = localInferenceEngine?.getAvailableModels() ?: emptyList()

    override fun getLocalFreeSpaceBytes(): Long = localInferenceEngine?.getFreeSpaceBytes() ?: 0L

    override fun getTotalDeviceMemoryBytes(): Long = getTotalMemoryBytes()

    override fun getModelContextTokens(modelId: String): Int = appSettings.getModelContextTokens(modelId)

    override fun setModelContextTokens(modelId: String, contextTokens: Int) {
        appSettings.setModelContextTokens(modelId, contextTokens)
    }

    override suspend fun releaseLocalEngine() {
        localInferenceEngine?.release()
    }

    override fun startLocalModelDownload(model: LocalModel) {
        localInferenceEngine?.startDownload(model)
    }

    override fun cancelLocalModelDownload() {
        localInferenceEngine?.cancelDownload()
    }

    override suspend fun deleteLocalModel(modelId: String) {
        localInferenceEngine?.deleteModel(modelId)
    }

    override fun getLocalActiveBackend(): StateFlow<String?>? = localInferenceEngine?.activeBackend

    override fun getBackendPreference(): String = appSettings.getBackendPreference()

    override fun setBackendPreference(pref: String) {
        appSettings.setBackendPreference(pref)
    }
}
