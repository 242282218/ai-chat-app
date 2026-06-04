package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.domain.usecase.GenerateImageRequest
import com.aichat.workbench.domain.usecase.GenerateImageUseCase
import com.aichat.workbench.provider.defaultImageModel
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.gateway.GatewayHttpException
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.gateway.SearchResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.local.InvalidLocalToolArgumentsException
import com.aichat.workbench.tool.local.LocalToolExecutor
import com.aichat.workbench.tool.local.LocalToolUnavailableException
import com.aichat.workbench.tool.local.defaultLocalTools
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ToolExecution(
    val result: ToolResult,
    val messageContent: String,
    val contentParts: List<MessagePart> = emptyList(),
)

private data class ExecutedToolOutput(
    val output: ToolOutput,
    val contentParts: List<MessagePart> = emptyList(),
)

class ToolExecutor(
    private val gatewaySettingsProvider: suspend () -> GatewaySettings,
    gatewayClientProvider: () -> GatewayClient,
    private val toolInvocationRepository: ToolInvocationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val preferencesRepository: ImageGenerationPreferencesRepository,
    private val imageGenerationRepository: ImageGenerationRepository,
    private val imageProvider: ImageGenerationProvider,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
    private val localToolExecutor: LocalToolExecutor = LocalToolExecutor(defaultLocalTools(clock)),
) {
    private val gatewayClient: GatewayClient by lazy(gatewayClientProvider)
    private val remoteToolsMutex = Mutex()
    private var remoteToolsCache: RemoteToolsCache? = null

    suspend fun availableTools(): List<ToolDescriptor> =
        localTools() + remoteTools()

    suspend fun descriptorFor(name: String): ToolDescriptor? =
        availableTools().firstOrNull { it.name == name.canonicalToolName() }

    suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): ToolExecution =
        execute(conversationId, toolCall, descriptorFor(toolCall.name))

    suspend fun execute(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution {
        val toolDescriptor = descriptor
            ?: return saveFailure(conversationId, toolCall, ToolPermissionLevel.HighRisk, "unknown_tool", "未知工具。")
        if (toolDescriptor.source == ToolSource.Official) {
            return saveFailure(
                conversationId = conversationId,
                toolCall = toolCall,
                permissionLevel = toolDescriptor.permissionLevel,
                code = "hosted_tool_not_executable_locally",
                message = "官方 Hosted Tool 由 Provider 执行，本地不执行。",
            )
        }
        val startedAt = clock.instant()
        return runCatching {
            when (toolDescriptor.name) {
                in LOCAL_EXECUTABLE_TOOL_NAMES -> localToolExecutor
                    .execute(conversationId, toolCall)
                    .let { ExecutedToolOutput(it.output, it.contentParts) }
                "web_search" -> ExecutedToolOutput(ToolOutput.Json(executeSearch(toolCall.arguments).toJson()))
                "code_sandbox" -> ExecutedToolOutput(ToolOutput.Json(executeSandbox(toolCall.arguments).toJson()))
                "image_generation" -> executeImageGeneration(conversationId, toolCall.arguments)
                else -> error("工具尚未实现：${toolCall.name}")
            }
        }.fold(
            onSuccess = { executed ->
                val output = executed.output
                val result = ToolResult(
                    id = toolCall.id,
                    toolName = toolDescriptor.name,
                    permissionLevel = toolDescriptor.permissionLevel,
                    inputSummary = toolCall.arguments.toInputSummary(),
                    output = output,
                    status = ToolStatus.Completed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = null,
                )
                toolInvocationRepository.saveToolResult(conversationId, result)
                ToolExecution(result, output.asModelContent(), executed.contentParts)
            },
            onFailure = { error ->
                saveFailure(
                    conversationId = conversationId,
                    toolCall = toolCall,
                    permissionLevel = toolDescriptor.permissionLevel,
                    code = error.toToolErrorCode(),
                    message = error.message ?: "工具执行失败。",
                    startedAt = startedAt,
                )
            },
        )
    }

    suspend fun deny(conversationId: ConversationId, toolCall: ToolCall): ToolExecution =
        deny(conversationId, toolCall, descriptorFor(toolCall.name))

    suspend fun deny(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        saveFailure(
            conversationId = conversationId,
            toolCall = toolCall,
            permissionLevel = descriptor?.permissionLevel ?: ToolPermissionLevel.HighRisk,
            code = "tool_denied",
            message = "用户拒绝执行工具。",
        )

    private fun localTools(): List<ToolDescriptor> =
        localToolExecutor.descriptors + BuiltInToolRegistry.tools.filter { it.name in LOCAL_TOOL_NAMES }

    private suspend fun remoteTools(): List<ToolDescriptor> {
        val settings = gatewaySettingsProvider()
        if (!settings.enabled || !settings.baseUrl.isValidGatewayUrl()) {
            remoteToolsCache = null
            return emptyList()
        }
        val cacheKey = settings.toCacheKey()
        remoteToolsCache
            ?.takeIf { it.cacheKey == cacheKey && clock.instant().isBefore(it.expiresAt) }
            ?.let { return it.tools }

        return remoteToolsMutex.withLock {
            remoteToolsCache
                ?.takeIf { it.cacheKey == cacheKey && clock.instant().isBefore(it.expiresAt) }
                ?.let { return@withLock it.tools }

            runCatching { gatewayClient.toolManifest(cacheKey.baseUrl).tools.filterExecutableRemoteTools() }
                .onSuccess { tools ->
                    remoteToolsCache = RemoteToolsCache(
                        cacheKey = cacheKey,
                        tools = tools,
                        expiresAt = clock.instant().plus(REMOTE_TOOLS_CACHE_TTL),
                    )
                }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun executeSearch(arguments: String): SearchResponse {
        val settings = requireGatewaySettings()
        val args = decodeToolArguments<SearchArguments>(arguments)
        val query = args.query.trim()
        if (query.isBlank()) {
            throw InvalidToolArgumentsException("搜索关键词不能为空。")
        }
        return gatewayClient.search(settings.baseUrl, query, settings.apiToken)
    }

    private suspend fun executeSandbox(arguments: String): SandboxRunResponse {
        val settings = requireGatewaySettings()
        val args = decodeToolArguments<SandboxArguments>(arguments)
        if (args.code.isBlank()) {
            throw InvalidToolArgumentsException("沙箱代码不能为空。")
        }
        val language = args.language.trim().lowercase().ifBlank { DEFAULT_SANDBOX_LANGUAGE }
        if (language != DEFAULT_SANDBOX_LANGUAGE) {
            throw InvalidToolArgumentsException("仅支持 python 沙箱运行。")
        }
        val timeoutSeconds = args.timeoutSeconds ?: DEFAULT_SANDBOX_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_SANDBOX_TIMEOUT_SECONDS..MAX_SANDBOX_TIMEOUT_SECONDS) {
            throw InvalidToolArgumentsException("Sandbox timeoutSeconds 必须在 1 到 10 秒之间。")
        }
        return gatewayClient.runSandbox(
            baseUrl = settings.baseUrl,
            language = language,
            code = args.code,
            timeoutSeconds = timeoutSeconds,
            apiToken = settings.apiToken,
        )
    }

    private suspend fun executeImageGeneration(
        conversationId: ConversationId,
        arguments: String,
    ): ExecutedToolOutput {
        val args = decodeToolArguments<ImageGenerationArguments>(arguments)
        val prompt = args.prompt.trim()
        if (prompt.isBlank()) {
            throw InvalidToolArgumentsException("图片提示词不能为空。")
        }
        val provider = selectImageProvider()
        val apiKey = providerRepository.getApiKey(provider.id)
        if (provider.requiresApiKey()) {
            require(!apiKey.isNullOrBlank()) { "API Key 缺失。" }
        }
        val model = args.model?.trim()?.takeIf { it.isNotBlank() }
            ?: imagePreferencesModel(provider)
            ?: provider.defaultImageModel()
        if (model.isBlank()) {
            throw InvalidToolArgumentsException("图片 Model 不能为空。")
        }
        val count = args.count ?: 1
        if (count !in 1..4) {
            throw InvalidToolArgumentsException("图片数量必须在 1 到 4 之间。")
        }
        val images = GenerateImageUseCase(
            repository = imageGenerationRepository,
            imageProvider = imageProvider,
            imageStorage = imageStorage,
            clock = clock,
        )(
            GenerateImageRequest(
                conversationId = conversationId,
                provider = provider,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                size = args.size?.trim()?.ifBlank { null },
                quality = args.quality?.trim()?.ifBlank { null },
                count = count,
            ),
        )
        val output = ImageGenerationOutput(
            prompt = prompt,
            providerId = provider.id.value,
            model = model,
            count = images.size,
            images = images.map { it.toOutput() },
            markdown = images.toMarkdown(),
        )
        return ExecutedToolOutput(
            output = ToolOutput.Json(toolJson.encodeToString(output)),
            contentParts = images.toMessageParts(),
        )
    }

    private suspend fun selectImageProvider(): ProviderConfig {
        val preferences = preferencesRepository.observePreferences().value
        val providers = providerRepository.observeProviders().first()
            .filter { it.enabled && it.supportsImageGeneration() }
        val preferred = preferences.providerId
            ?.let { id -> providers.firstOrNull { it.id.value == id } }
        return preferred ?: providers.firstOrNull() ?: error("模型服务未配置。")
    }

    private fun imagePreferencesModel(provider: ProviderConfig): String? {
        val preferences = preferencesRepository.observePreferences().value
        return preferences.model
            ?.takeIf { preferences.providerId == provider.id.value }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun requireGatewaySettings(): GatewaySettings {
        val settings = gatewaySettingsProvider()
        if (!settings.enabled) {
            throw GatewaySettingsException("gateway_disabled", "工具网关未启用。")
        }
        if (!settings.baseUrl.isValidGatewayUrl()) {
            throw GatewaySettingsException("invalid_gateway_url", "工具网关地址无效。")
        }
        if (settings.apiToken.isBlank()) {
            throw GatewaySettingsException("gateway_token_required", "Gateway API token 未配置。")
        }
        return settings
    }

    private inline fun <reified T> decodeToolArguments(arguments: String): T =
        try {
            toolJson.decodeFromString(arguments)
        } catch (error: SerializationException) {
            throw InvalidToolArgumentsException("工具参数 JSON 无效。", error)
        }

    private suspend fun saveFailure(
        conversationId: ConversationId,
        toolCall: ToolCall,
        permissionLevel: ToolPermissionLevel,
        code: String,
        message: String,
        startedAt: java.time.Instant = clock.instant(),
    ): ToolExecution {
        val output = ToolOutput.Json(toolJson.encodeToString(ToolErrorOutput(code, message)))
        val result = ToolResult(
            id = toolCall.id,
            toolName = toolCall.name,
            permissionLevel = permissionLevel,
            inputSummary = toolCall.arguments.toInputSummary(),
            output = output,
            status = ToolStatus.Failed,
            startedAt = startedAt,
            finishedAt = clock.instant(),
            error = ToolError(code, message),
        )
        toolInvocationRepository.saveToolResult(conversationId, result)
        return ToolExecution(result, output.asModelContent())
    }

    private fun SearchResponse.toJson(): String =
        toolJson.encodeToString(
            SearchOutput(
                query = query,
                fetchedAt = fetchedAt.toString(),
                results = results.map { it.toOutput() },
            ),
        )

    private fun SearchResult.toOutput(): SearchResultOutput =
        SearchResultOutput(
            title = title,
            summary = summary,
            url = url,
            source = source,
            publishedAt = publishedAt?.toString(),
        )

    private fun SandboxRunResponse.toJson(): String =
        toolJson.encodeToString(
            SandboxOutput(
                language = language,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                durationMs = durationMs,
                timedOut = timedOut,
                truncated = truncated,
            ),
        )

    private fun ToolOutput.asModelContent(): String =
        when (this) {
            is ToolOutput.Text -> text
            is ToolOutput.Json -> value
        }

    private fun String.toInputSummary(): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        val preview = normalized.take(120)
        return if (normalized.length > preview.length) "$preview..." else preview
    }

    private fun Throwable.toToolErrorCode(): String =
        when (this) {
            is GatewayHttpException -> gatewayCode
            is GatewaySettingsException -> code
            is InvalidLocalToolArgumentsException -> "invalid_tool_arguments"
            is LocalToolUnavailableException -> "tool_unavailable"
            is InvalidToolArgumentsException -> "invalid_tool_arguments"
            else -> "tool_failed"
        }

    private fun String.isValidGatewayUrl(): Boolean {
        val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
        return uri.host != null && uri.scheme?.lowercase() in setOf("http", "https")
    }

    private fun GatewaySettings.toCacheKey(): GatewaySettingsCacheKey =
        GatewaySettingsCacheKey(
            enabled = enabled,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiToken = apiToken.trim(),
        )

    private fun List<ToolDescriptor>.filterExecutableRemoteTools(): List<ToolDescriptor> =
        filter { it.name in EXECUTABLE_REMOTE_TOOL_NAMES }

    private fun ImageGeneration.toOutput(): ImageGenerationResultOutput =
        ImageGenerationResultOutput(
            id = id.value,
            originalPath = originalPath,
            thumbnailPath = thumbnailPath,
            status = status.name.lowercase(),
            errorSummary = errorSummary,
        )

    private fun List<ImageGeneration>.toMarkdown(): String =
        mapNotNull { image ->
            image.originalPath?.let { path -> "![generated image]($path)" }
        }.joinToString("\n")

    private fun List<ImageGeneration>.toMessageParts(): List<MessagePart> =
        mapNotNull { image ->
            image.originalPath?.let { path -> MessagePart.Image(uri = path, mimeType = "image/png") }
        }
}

private data class RemoteToolsCache(
    val cacheKey: GatewaySettingsCacheKey,
    val tools: List<ToolDescriptor>,
    val expiresAt: Instant,
)

private data class GatewaySettingsCacheKey(
    val enabled: Boolean,
    val baseUrl: String,
    val apiToken: String,
)

private class InvalidToolArgumentsException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private class GatewaySettingsException(
    val code: String,
    message: String,
) : RuntimeException(message)

private val REMOTE_TOOLS_CACHE_TTL: Duration = Duration.ofMinutes(5)
private val LOCAL_TOOL_NAMES = setOf("image_generation")
private val LOCAL_EXECUTABLE_TOOL_NAMES = setOf("time", "text_transform", "code_diff_preview")
private const val DEFAULT_SANDBOX_LANGUAGE = "python"
private const val DEFAULT_SANDBOX_TIMEOUT_SECONDS = 3
private const val MIN_SANDBOX_TIMEOUT_SECONDS = 1
private const val MAX_SANDBOX_TIMEOUT_SECONDS = 10
private val EXECUTABLE_REMOTE_TOOL_NAMES = setOf("web_search", "code_sandbox")

private val toolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
private data class SearchArguments(val query: String = "")

@Serializable
private data class SandboxArguments(
    val language: String = "python",
    val code: String = "",
    val timeoutSeconds: Int? = null,
)

@Serializable
private data class ImageGenerationArguments(
    val prompt: String = "",
    val model: String? = null,
    val size: String? = null,
    val quality: String? = null,
    val count: Int? = null,
)

@Serializable
private data class ToolErrorOutput(val code: String, val message: String)

@Serializable
private data class ImageGenerationOutput(
    val prompt: String,
    val providerId: String,
    val model: String,
    val count: Int,
    val images: List<ImageGenerationResultOutput>,
    val markdown: String,
)

@Serializable
private data class ImageGenerationResultOutput(
    val id: String,
    val originalPath: String?,
    val thumbnailPath: String?,
    val status: String,
    val errorSummary: String? = null,
)

@Serializable
private data class SearchOutput(
    val query: String,
    val fetchedAt: String,
    val results: List<SearchResultOutput>,
)

@Serializable
private data class SearchResultOutput(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

@Serializable
private data class SandboxOutput(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)
