package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.provider.image.GeneratedImage
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutorTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun localToolsDoNotCreateGatewayClientWhenGatewayDisabled() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
        )

        val tools = executor.availableTools()
        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_1"), "time", "{}"),
        )

        assertEquals(listOf("time", "image_generation"), tools.map { it.name })
        assertEquals("""{"currentTime":"2026-06-01T00:00:00Z"}""", execution.messageContent)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun localToolsAcceptCaseInsensitiveAliases() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_alias"), "TIME", "{}"),
        )

        assertEquals("""{"currentTime":"2026-06-01T00:00:00Z"}""", execution.messageContent)
        assertEquals("time", repository.savedResults.value.single().toolName)
    }

    @Test
    fun executeImageGenerationUsesSavedImagePreferencesAndReturnsImages() = runTest {
        val toolRepository = RecordingToolInvocationRepository()
        val imageRepository = RecordingImageGenerationRepository()
        val imageStorage = RecordingImageStorage()
        val provider = imageProviderConfig()
        val providerRepository = RecordingProviderConfigRepository(
            providers = listOf(provider),
            apiKeys = mapOf(provider.id to "image-key"),
        )
        val preferencesRepository = RecordingImageGenerationPreferencesRepository(
            ImageGenerationPreferences(providerId = provider.id.value, model = "gpt-image-2"),
        )
        val imageProvider = RecordingImageGenerationProvider(
            ImageGenerationProviderResponse(images = listOf(base64Image(byteArrayOf(1, 2, 3)))),
        )
        val executor = toolExecutor(
            toolInvocationRepository = toolRepository,
            providerRepository = providerRepository,
            preferencesRepository = preferencesRepository,
            imageGenerationRepository = imageRepository,
            imageProvider = imageProvider,
            imageStorage = imageStorage,
            clock = clock,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_image"),
                "generate-image",
                """{"prompt":"Draw a cat","size":"1024x1024","quality":"auto","count":1}""",
            ),
        )

        assertEquals("image_generation", toolRepository.savedResults.value.single().toolName)
        assertEquals("image-key", imageProvider.requests.single().apiKey)
        assertEquals("gpt-image-2", imageProvider.requests.single().model)
        assertEquals("Draw a cat", imageProvider.requests.single().prompt)
        assertEquals(1, imageStorage.savedBytes.size)
        assertEquals(1, execution.contentParts.size)
        assertTrue(execution.contentParts.single().toString().contains("original/"))
        assertTrue(execution.messageContent.contains(""""model":"gpt-image-2""""))
        assertTrue(execution.messageContent.contains(""""markdown":"![generated image](original/"""))
        assertEquals(ImageGenerationStatus.Completed, imageRepository.saved.value.last().status)
    }

    @Test
    fun remoteToolsCreateGatewayClientOnlyWhenGatewayEnabled() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            var created = false
            val executor = toolExecutor(
                gatewaySettingsProvider = { GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token") },
                gatewayClientProvider = {
                    created = true
                    GatewayClient()
                },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(true, created)
            assertEquals(listOf("time", "image_generation", "web_search"), tools.map { it.name })
            assertEquals("/v1/tools/manifest", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsAcceptCaseInsensitiveGatewayScheme() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val uppercaseBaseUrl = server.url("/").toString().replaceFirst("http", "HTTP")
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = " $uppercaseBaseUrl ", apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(listOf("time", "image_generation", "web_search"), tools.map { it.name })
            assertEquals("/v1/tools/manifest", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsReuseCachedManifest() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val first = executor.availableTools()
            val second = executor.availableTools()

            assertEquals(listOf("time", "image_generation", "web_search"), first.map { it.name })
            assertEquals(first, second)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsInvalidateCacheWhenGatewaySettingsChange() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.enqueue(manifestResponse(toolName = "code_sandbox", permissionLevel = "Execute"))
        server.start()
        try {
            var token = "token-1"
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = token)
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val first = executor.availableTools()
            token = "token-2"
            val second = executor.availableTools()

            assertEquals(listOf("time", "image_generation", "web_search"), first.map { it.name })
            assertEquals(listOf("time", "image_generation", "code_sandbox"), second.map { it.name })
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsRefreshExpiredCache() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.enqueue(manifestResponse(toolName = "code_sandbox", permissionLevel = "Execute"))
        server.start()
        try {
            val mutableClock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = mutableClock,
            )

            val first = executor.availableTools()
            mutableClock.advanceBy(Duration.ofMinutes(6))
            val second = executor.availableTools()

            assertEquals(listOf("time", "image_generation", "web_search"), first.map { it.name })
            assertEquals(listOf("time", "image_generation", "code_sandbox"), second.map { it.name })
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsCoalesceConcurrentManifestRequests() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val results = (1..5)
                .map { async { executor.availableTools().map { it.name } } }
                .awaitAll()

            assertEquals(List(5) { listOf("time", "image_generation", "web_search") }, results)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsExposeOnlyClientExecutableGatewayTools() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "version": 1,
                  "generatedAt": "2026-06-01T00:00:00Z",
                  "tools": [
                    {
                      "name": "web_search",
                      "description": "Search",
                      "permissionLevel": "Network",
                      "inputSchema": {}
                    },
                    {
                      "name": "future_gateway_tool",
                      "description": "Not implemented on Android yet",
                      "permissionLevel": "HighRisk",
                      "inputSchema": {}
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.start()
        try {
            val executor = toolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(listOf("time", "image_generation", "web_search"), tools.map { it.name })
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun executeWithKnownDescriptorReportsDisabledGatewayWithoutReloadingManifest() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
            gatewayClientProvider = { error("GatewayClient should not be created for known descriptor failure") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_2"), "web_search", """{"query":"AI"}"""),
            descriptor = descriptor,
        )

        assertEquals(ToolPermissionLevel.Network, execution.result.permissionLevel)
        assertEquals("gateway_disabled", execution.result.error?.code)
        assertEquals("工具网关未启用。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeWithOfficialDescriptorDoesNotCreateGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = { GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = "token") },
            gatewayClientProvider = { error("Official hosted tools should not create GatewayClient") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Official hosted web search.",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Official,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_official"), "web_search", "{}"),
            descriptor = descriptor,
        )

        assertEquals(ToolPermissionLevel.ReadOnly, execution.result.permissionLevel)
        assertEquals("hosted_tool_not_executable_locally", execution.result.error?.code)
        assertEquals("官方 Hosted Tool 由 Provider 执行，本地不执行。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSearchReportsInvalidGatewayUrlBeforeCreatingGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "gateway.local", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for invalid gateway URL") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 20,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_5"), "web_search", """{"query":"AI"}"""),
            descriptor = descriptor,
        )

        assertEquals("invalid_gateway_url", execution.result.error?.code)
        assertEquals("工具网关地址无效。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSearchReportsMissingGatewayTokenBeforeCreatingGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = " ")
            },
            gatewayClientProvider = { error("GatewayClient should not be created without API token") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 20,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_6"), "web_search", """{"query":"AI"}"""),
            descriptor = descriptor,
        )

        assertEquals("gateway_token_required", execution.result.error?.code)
        assertEquals("Gateway API token 未配置。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSandboxRejectsInvalidTimeoutBeforeCreatingGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for invalid sandbox timeout") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "code_sandbox",
            displayName = "Code Sandbox",
            description = "Remote sandbox",
            permissionLevel = ToolPermissionLevel.Execute,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 10,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call_3"),
                name = "code_sandbox",
                arguments = """{"language":"python","code":"print(1)","timeoutSeconds":11}""",
            ),
            descriptor = descriptor,
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("Sandbox timeoutSeconds 必须在 1 到 10 秒之间。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSearchReportsInvalidToolArgumentsForMalformedJson() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for malformed tool arguments") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 20,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call_4"),
                name = "web_search",
                arguments = "{",
            ),
            descriptor = descriptor,
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals(1, repository.savedResults.value.size)
    }
}

private fun manifestResponse(toolName: String, permissionLevel: String): MockResponse =
    MockResponse().setResponseCode(200).setBody(
        """
        {
          "version": 1,
          "generatedAt": "2026-06-01T00:00:00Z",
          "tools": [
            {
              "name": "$toolName",
              "description": "Remote tool",
              "permissionLevel": "$permissionLevel",
              "inputSchema": {}
            }
          ]
        }
        """.trimIndent(),
    )

private fun toolExecutor(
    clock: Clock,
    gatewaySettingsProvider: suspend () -> GatewaySettings = {
        GatewaySettings(enabled = false, baseUrl = "", apiToken = "")
    },
    gatewayClientProvider: () -> GatewayClient = { GatewayClient() },
    toolInvocationRepository: ToolInvocationRepository = RecordingToolInvocationRepository(),
    providerRepository: ProviderConfigRepository = RecordingProviderConfigRepository(),
    preferencesRepository: ImageGenerationPreferencesRepository = RecordingImageGenerationPreferencesRepository(),
    imageGenerationRepository: ImageGenerationRepository = RecordingImageGenerationRepository(),
    imageProvider: ImageGenerationProvider = RecordingImageGenerationProvider(),
    imageStorage: ImageStorage = RecordingImageStorage(),
): ToolExecutor =
    ToolExecutor(
        gatewaySettingsProvider = gatewaySettingsProvider,
        gatewayClientProvider = gatewayClientProvider,
        toolInvocationRepository = toolInvocationRepository,
        providerRepository = providerRepository,
        preferencesRepository = preferencesRepository,
        imageGenerationRepository = imageGenerationRepository,
        imageProvider = imageProvider,
        imageStorage = imageStorage,
        clock = clock,
    )

private fun imageProviderConfig(): ProviderConfig =
    ProviderConfig(
        id = ProviderId("image-provider"),
        name = "Image Provider",
        type = ProviderType.NewApi,
        baseUrl = "https://example.test/v1",
        apiKeyRef = "image-key-ref",
        headers = emptyMap(),
        models = listOf(
            ModelConfig(
                id = "gpt-image-2",
                displayName = "gpt-image-2",
                capability = ModelCapability(
                    model = "gpt-image-2",
                    text = false,
                    vision = false,
                    imageGeneration = true,
                    toolCalling = false,
                    structuredOutput = false,
                    longContext = false,
                    maxContextTokens = null,
                ),
            ),
        ),
        defaultModel = "gpt-image-2",
        enabled = true,
    )

private fun base64Image(bytes: ByteArray): GeneratedImage =
    GeneratedImage(
        base64 = Base64.getEncoder().encodeToString(bytes),
        url = null,
        revisedPrompt = null,
    )

private class RecordingToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}

private class RecordingProviderConfigRepository(
    providers: List<ProviderConfig> = emptyList(),
    private val apiKeys: Map<ProviderId, String> = emptyMap(),
) : ProviderConfigRepository {
    private val providers = MutableStateFlow(providers)

    override fun observeProviders(): Flow<List<ProviderConfig>> = providers

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providers.value.firstOrNull { it.id == id }

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = apiKeys[providerId]

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) {
        providers.value = providers.value.filterNot { it.id == id }
    }
}

private class RecordingImageGenerationPreferencesRepository(
    initialPreferences: ImageGenerationPreferences = ImageGenerationPreferences(),
) : ImageGenerationPreferencesRepository {
    private val preferences = MutableStateFlow(initialPreferences)

    override fun observePreferences(): MutableStateFlow<ImageGenerationPreferences> = preferences

    override suspend fun savePreferences(providerId: String?, model: String?) {
        preferences.value = ImageGenerationPreferences(providerId = providerId, model = model)
    }
}

private class RecordingImageGenerationRepository : ImageGenerationRepository {
    val saved = MutableStateFlow<List<ImageGeneration>>(emptyList())

    override fun observeImageGenerations(): Flow<List<ImageGeneration>> = saved

    override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
        saved.value.firstOrNull { it.id == id }

    override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
        saved.value = saved.value.filterNot { it.id == imageGeneration.id } + imageGeneration
    }

    override suspend fun deleteImageGeneration(id: ImageGenerationId) {
        saved.value = saved.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllImageGenerations() {
        saved.value = emptyList()
    }
}

private class RecordingImageGenerationProvider(
    private val response: ImageGenerationProviderResponse = ImageGenerationProviderResponse(emptyList()),
) : ImageGenerationProvider {
    val requests = mutableListOf<ImageGenerationProviderRequest>()

    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse {
        requests += request
        return response
    }
}

private class RecordingImageStorage : ImageStorage {
    val savedBytes = mutableListOf<ByteArray>()

    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths {
        savedBytes += bytes
        return StoredImagePaths(
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumb/${id.value}.png",
        )
    }

    override suspend fun deleteAllImages() = Unit
}

private class MutableClock(
    private var currentInstant: Instant,
) : Clock() {
    override fun instant(): Instant = currentInstant

    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
