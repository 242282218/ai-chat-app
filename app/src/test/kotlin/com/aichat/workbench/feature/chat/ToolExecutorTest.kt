package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.provider.image.GeneratedImage
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.local.AuthorizedFileReadRequest
import com.aichat.workbench.tool.local.AuthorizedFileReadResult
import com.aichat.workbench.tool.local.AuthorizedFileReader
import com.aichat.workbench.tool.local.LocalScriptRunRequest
import com.aichat.workbench.tool.local.LocalScriptRunResult
import com.aichat.workbench.tool.local.LocalScriptRunner
import com.aichat.workbench.tool.local.LocalToolExecutor
import com.aichat.workbench.tool.local.ProviderConnectionTestResult
import com.aichat.workbench.tool.local.ProviderConnectionTestRunner
import com.aichat.workbench.tool.local.defaultLocalTools
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.LocalSearchHttpException
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class ToolExecutorTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)
    private val localToolNames = listOf(
        "time",
        "text_transform",
        "code_diff_preview",
        "local_js",
        "file_read",
        "web_search_local",
        "provider_connection_test",
        "image_upload_to_model",
        "image_generation",
    )

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

        assertEquals(localToolNames, tools.map { it.name })
        assertEquals("""{"currentTime":"2026-06-01T00:00:00Z"}""", execution.messageContent)
        val saved = repository.savedResults.value.single()
        assertEquals("conversation", saved.conversationId?.value)
        assertEquals("{}", saved.rawInputJson)
        assertEquals("""{"currentTime":"2026-06-01T00:00:00Z"}""", saved.rawOutputJson)
        assertEquals(0L, saved.durationMs)
    }

    @Test
    fun localToolsExposeRiskAndAccessMetadata() = runTest {
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
        )

        val tools = executor.availableTools().associateBy { it.name }

        listOf("time", "text_transform", "code_diff_preview").forEach { name ->
            val tool = requireNotNull(tools[name])
            assertEquals(ToolRiskLevel.Low, tool.riskLevel)
            assertFalse(tool.requiresNetwork)
            assertFalse(tool.requiresFileAccess)
            assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, tool.defaultPermissionPolicy)
        }
        listOf("web_search_local", "image_generation", "provider_connection_test").forEach { name ->
            val tool = requireNotNull(tools[name])
            assertEquals(ToolRiskLevel.Medium, tool.riskLevel)
            assertTrue(tool.requiresNetwork)
            assertFalse(tool.requiresFileAccess)
            assertEquals(ToolPermissionPolicy.AskEveryTime, tool.defaultPermissionPolicy)
        }
        assertEquals(ToolRiskLevel.High, requireNotNull(tools["local_js"]).riskLevel)
        assertFalse(requireNotNull(tools["local_js"]).requiresNetwork)
        assertFalse(requireNotNull(tools["local_js"]).requiresFileAccess)
        assertEquals(ToolRiskLevel.High, requireNotNull(tools["file_read"]).riskLevel)
        assertFalse(requireNotNull(tools["file_read"]).requiresNetwork)
        assertTrue(requireNotNull(tools["file_read"]).requiresFileAccess)
        assertEquals(ToolRiskLevel.High, requireNotNull(tools["image_upload_to_model"]).riskLevel)
        assertTrue(requireNotNull(tools["image_upload_to_model"]).requiresNetwork)
        assertTrue(requireNotNull(tools["image_upload_to_model"]).requiresFileAccess)
        assertEquals(ToolPermissionPolicy.AskEveryTime, requireNotNull(tools["image_upload_to_model"]).defaultPermissionPolicy)
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
    fun availableToolsFiltersDisabledTools() = runTest {
        val executor = toolExecutor(
            clock = clock,
            toolSettingsProvider = {
                mapOf("time" to ToolRuntimeSetting(toolName = "time", enabled = false))
            },
        )

        val tools = executor.availableTools()

        assertFalse(tools.any { it.name == "time" })
        assertTrue(tools.any { it.name == "text_transform" })
    }

    @Test
    fun executeDisabledToolSavesDisabledFailure() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            toolSettingsProvider = {
                mapOf("time" to ToolRuntimeSetting(toolName = "time", enabled = false))
            },
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_disabled"), "time", "{}"),
        )

        assertEquals("tool_disabled", execution.result.error?.code)
        assertEquals("工具已禁用。", execution.result.error?.message)
        assertEquals("time", repository.savedResults.value.single().toolName)
    }

    @Test
    fun executeDisabledToolAliasSavesCanonicalToolName() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            toolSettingsProvider = {
                mapOf("web_search_local" to ToolRuntimeSetting(toolName = "web_search_local", enabled = false))
            },
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_disabled_alias"), "local-search", """{"query":"AI"}"""),
        )

        assertEquals("tool_disabled", execution.result.error?.code)
        assertEquals("web_search_local", repository.savedResults.value.single().toolName)
    }

    @Test
    fun executeImageUploadToModelRequiresChatInputConfirmation() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_image_upload"),
                "image-upload",
                """{"imageUri":"content://image/1","purpose":"describe it"}""",
            ),
        )

        val saved = repository.savedResults.value.single()
        assertEquals("image_upload_to_model", saved.toolName)
        assertEquals(ToolPermissionLevel.HighRisk, saved.permissionLevel)
        assertEquals("image_upload_requires_chat_confirmation", execution.result.error?.code)
        assertTrue(execution.messageContent.contains("聊天输入栏选择图片"))
        assertTrue(execution.messageContent.contains("工具不能自动读取或上传本地图片"))
    }

    @Test
    fun requiresConfirmationHonorsNetworkPermissionPolicyOnly() = runTest {
        val executor = toolExecutor(
            clock = clock,
            toolSettingsProvider = {
                mapOf(
                    "web_search_local" to ToolRuntimeSetting(
                        toolName = "web_search_local",
                        permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                    ),
                    "file_read" to ToolRuntimeSetting(
                        toolName = "file_read",
                        permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                    ),
                )
            },
        )
        val tools = executor.availableTools().associateBy { it.name }

        assertFalse(executor.requiresConfirmation(requireNotNull(tools["web_search_local"])))
        assertTrue(executor.requiresConfirmation(requireNotNull(tools["file_read"])))
        assertFalse(executor.requiresConfirmation(requireNotNull(tools["time"])))
    }

    @Test
    fun executeTextTransformFormatsJsonLocally() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_text"),
                "text-transform",
                """{"operation":"json_format","text":"{\"b\":1,\"a\":[true]}"}""",
            ),
        )

        assertEquals("text_transform", repository.savedResults.value.single().toolName)
        assertTrue(execution.messageContent.contains(""""operation":"json_format""""))
        assertTrue(execution.messageContent.contains("""\n"""))
        assertTrue(execution.messageContent.contains(""""validJson":true"""))
    }

    @Test
    fun executeCodeDiffPreviewReturnsReadOnlyDiff() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_diff"),
                "code_diff_preview",
                """{"fileName":"Main.kt","original":"fun main() {\n    println(1)\n}","modified":"fun main() {\n    println(2)\n}"}""",
            ),
        )

        val result = repository.savedResults.value.single()
        assertEquals("code_diff_preview", result.toolName)
        assertEquals(ToolPermissionLevel.ReadOnly, result.permissionLevel)
        assertTrue(execution.messageContent.contains("--- Main.kt"))
        assertTrue(execution.messageContent.contains("-    println(1)"))
        assertTrue(execution.messageContent.contains("+    println(2)"))
    }

    @Test
    fun executeCodeDiffPreviewRejectsEmptyOriginalAndModified() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_empty_diff"),
                "code_diff_preview",
                """{"fileName":"snippet.kt","original":"","modified":""}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("original 和 modified 不能同时为空。", execution.result.error?.message)
        assertEquals("code_diff_preview", repository.savedResults.value.single().toolName)
    }

    @Test
    fun executeLocalJsRunsWithExplicitJsonInput() = runTest {
        val runner = RecordingScriptRunner(
            result = LocalScriptRunResult(
                output = """{"sum":3}""",
                durationMs = 12,
                timedOut = false,
                truncated = false,
            ),
        )
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            scriptRunner = runner,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_js"),
                "local-js",
                """{"code":"return { sum: input.a + input.b };","inputJson":"{\"a\":1,\"b\":2}","timeoutMillis":500,"outputLimitBytes":128}""",
            ),
        )

        val result = repository.savedResults.value.single()
        assertEquals("local_js", result.toolName)
        assertEquals(ToolPermissionLevel.HighRisk, result.permissionLevel)
        assertEquals("""{"a":1,"b":2}""", runner.requests.single().inputJson)
        assertEquals(500, runner.requests.single().timeoutMillis)
        assertTrue(execution.messageContent.contains(""""output":"{\"sum\":3}""""))
        assertTrue(execution.messageContent.contains(""""timedOut":false"""))
    }

    @Test
    fun executeLocalJsReturnsTimedOutResultAsToolOutput() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            scriptRunner = RecordingScriptRunner(
                result = LocalScriptRunResult(
                    output = "",
                    durationMs = 1000,
                    timedOut = true,
                    truncated = false,
                ),
            ),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_js_timeout"),
                "local_js",
                """{"code":"while (true) {}","timeoutMillis":1000}""",
            ),
        )

        assertEquals("local_js", repository.savedResults.value.single().toolName)
        assertEquals(null, repository.savedResults.value.single().error)
        assertTrue(execution.messageContent.contains(""""timedOut":true"""))
    }

    @Test
    fun executeLocalJsCancellationSavesCancelledToolHistoryAndRethrows() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            scriptRunner = RecordingScriptRunner(error = CancellationException("user stopped")),
        )

        assertFailsWith<CancellationException> {
            executor.execute(
                conversationId = ConversationId("conversation"),
                toolCall = ToolCall(
                    ToolCallId("call_js_cancelled"),
                    "local_js",
                    """{"code":"return 1;"}""",
                ),
            )
        }

        val saved = repository.savedResults.value.single()
        assertEquals("local_js", saved.toolName)
        assertEquals(ToolStatus.Cancelled, saved.status)
        assertEquals("tool_cancelled", saved.error?.code)
        assertEquals("工具执行已取消。", saved.error?.message)
        assertEquals(clock.instant(), saved.canceledAt)
    }

    @Test
    fun executeLocalJsReportsUnsupportedDevice() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            scriptRunner = RecordingScriptRunner(supported = false),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_js_unsupported"),
                "local_js",
                """{"code":"return 1;"}""",
            ),
        )

        assertEquals("tool_unavailable", execution.result.error?.code)
        assertEquals("当前设备不支持本地 JavaScript 沙箱。", execution.result.error?.message)
    }

    @Test
    fun executeLocalJsRejectsOversizedOutputLimit() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_js_invalid"),
                "local_js",
                """{"code":"return 1;","outputLimitBytes":32}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("outputLimitBytes 必须在 64 到 32768 之间。", execution.result.error?.message)
    }

    @Test
    fun executeFileReadReadsAuthorizedTextContent() = runTest {
        val fileReader = RecordingFileReader(
            result = AuthorizedFileReadResult(
                fileName = "notes.md",
                mimeType = "text/markdown",
                sizeBytes = 42,
                content = "# Title\nFirst line\nSecond line",
                truncated = false,
                unsupportedReason = null,
            ),
        )
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            fileReader = fileReader,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_file"),
                "read-file",
                """{"uri":"content://docs/notes","maxBytes":4096}""",
            ),
        )

        assertEquals("file_read", repository.savedResults.value.single().toolName)
        assertEquals(ToolPermissionLevel.HighRisk, repository.savedResults.value.single().permissionLevel)
        assertEquals("content://docs/notes", fileReader.requests.single().uri)
        assertEquals(4096, fileReader.requests.single().maxBytes)
        assertTrue(execution.messageContent.contains(""""fileName":"notes.md""""))
        assertTrue(execution.messageContent.contains(""""status":"completed""""))
        assertTrue(execution.messageContent.contains(""""preview":"# Title\nFirst line\nSecond line""""))
        assertFalse(execution.messageContent.contains(""""content""""))
        assertFalse(repository.savedResults.value.single().rawOutputJson.orEmpty().contains(""""content""""))
        assertTrue(execution.messageContent.contains(""""sentToModel":false"""))
    }

    @Test
    fun executeFileReadReturnsUnsupportedFileMetadata() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            fileReader = RecordingFileReader(
                result = AuthorizedFileReadResult(
                    fileName = "brief.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 1200,
                    content = null,
                    truncated = false,
                    unsupportedReason = "PDF 第一阶段暂不支持解析，请选择文本、Markdown、JSON 或代码文件。",
                ),
            ),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_file_pdf"),
                "file_read",
                """{"uri":"content://docs/pdf"}""",
            ),
        )

        assertEquals("file_read", repository.savedResults.value.single().toolName)
        assertTrue(execution.messageContent.contains(""""status":"unsupported""""))
        assertTrue(execution.messageContent.contains("PDF 第一阶段暂不支持解析"))
    }

    @Test
    fun executeFileReadReturnsTruncatedStatus() = runTest {
        val repository = RecordingToolInvocationRepository()
        val fileReader = RecordingFileReader(
            result = AuthorizedFileReadResult(
                fileName = "large.md",
                mimeType = "text/markdown",
                sizeBytes = 300_000,
                content = (1..45).joinToString("\n") { "line-$it" },
                truncated = true,
                unsupportedReason = null,
            ),
        )
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            fileReader = fileReader,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_file_truncated"),
                "file_read",
                """{"uri":"content://docs/large.md"}""",
            ),
        )

        assertEquals(64 * 1024, fileReader.requests.single().maxBytes)
        assertEquals("file_read", repository.savedResults.value.single().toolName)
        assertTrue(execution.messageContent.contains(""""status":"truncated""""))
        assertTrue(execution.messageContent.contains(""""truncated":true"""))
        assertTrue(execution.messageContent.contains("line-40"))
    }

    @Test
    fun executeFileReadRejectsNonPickerUri() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_file_invalid"),
                "file_read",
                """{"uri":"file:///sdcard/Download/notes.md"}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("只能读取 Android 文件选择器授权的 content:// URI。", execution.result.error?.message)
    }

    @Test
    fun executeFileReadRejectsInvalidSizeLimit() = runTest {
        val repository = RecordingToolInvocationRepository()
        val fileReader = RecordingFileReader()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            fileReader = fileReader,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_file_invalid_size"),
                "file_read",
                """{"uri":"content://docs/notes.md","maxBytes":512}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("maxBytes 必须在 1024 到 262144 之间。", execution.result.error?.message)
        assertEquals(emptyList<AuthorizedFileReadRequest>(), fileReader.requests)
    }

    @Test
    fun executeLocalSearchUsesAppSearchProvider() = runTest {
        val repository = RecordingToolInvocationRepository()
        val searchClient = RecordingLocalSearchClient()
        val executor = toolExecutor(
            clock = clock,
            gatewayClientProvider = { error("GatewayClient should not run local search") },
            toolInvocationRepository = repository,
            searchConfigProvider = {
                enabledSearchConfig().copy(
                    maxResults = 5,
                    searchDepth = "basic",
                    topic = "general",
                )
            },
            searchClient = searchClient,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search"),
                "local-search",
                """{"query":"AI news","maxResults":3,"topic":"news"}""",
            ),
        )

        val result = repository.savedResults.value.single()
        val request = searchClient.requests.single()
        assertEquals("web_search_local", result.toolName)
        assertEquals(ToolPermissionLevel.Network, result.permissionLevel)
        assertEquals("AI news", request.query)
        assertEquals(3, request.config.maxResults)
        assertEquals("basic", request.config.searchDepth)
        assertEquals("news", request.config.topic)
        assertTrue(execution.messageContent.contains(""""title":"Local search title""""))
        assertTrue(execution.messageContent.contains(""""url":"https://example.com/local""""))
        assertTrue(execution.messageContent.contains(""""source":"example.com""""))
        assertTrue(execution.messageContent.contains(""""publishedAt":"2026-05-31T00:00:00Z""""))
    }

    @Test
    fun executeLocalSearchReportsDisabledConfig() = runTest {
        val repository = RecordingToolInvocationRepository()
        val searchClient = RecordingLocalSearchClient()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            searchConfigProvider = { enabledSearchConfig().copy(enabled = false) },
            searchClient = searchClient,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_disabled"),
                "web_search_local",
                """{"query":"AI news"}""",
            ),
        )

        assertEquals("tool_unavailable", execution.result.error?.code)
        assertEquals("本地搜索未启用，请在工具页配置搜索 Provider。", execution.result.error?.message)
        assertEquals(emptyList<LocalSearchRequest>(), searchClient.requests)
    }

    @Test
    fun executeLocalSearchRejectsInvalidMaxResultsBeforeRequest() = runTest {
        val repository = RecordingToolInvocationRepository()
        val searchClient = RecordingLocalSearchClient()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            searchClient = searchClient,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_invalid_max"),
                "web_search_local",
                """{"query":"AI news","maxResults":21}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("maxResults 必须在 1 到 20 之间。", execution.result.error?.message)
        assertEquals(emptyList<LocalSearchRequest>(), searchClient.requests)
    }

    @Test
    fun executeLocalSearchRejectsInvalidSearchDepthBeforeRequest() = runTest {
        val repository = RecordingToolInvocationRepository()
        val searchClient = RecordingLocalSearchClient()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            searchClient = searchClient,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_invalid_depth"),
                "web_search_local",
                """{"query":"AI news","searchDepth":"deep"}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("searchDepth 仅支持 basic 或 advanced。", execution.result.error?.message)
        assertEquals(emptyList<LocalSearchRequest>(), searchClient.requests)
    }

    @Test
    fun executeLocalSearchRejectsInvalidTopicBeforeRequest() = runTest {
        val repository = RecordingToolInvocationRepository()
        val searchClient = RecordingLocalSearchClient()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            searchClient = searchClient,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_invalid_topic"),
                "web_search_local",
                """{"query":"AI news","topic":"sports"}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("topic 仅支持 general、news 或 finance。", execution.result.error?.message)
        assertEquals(emptyList<LocalSearchRequest>(), searchClient.requests)
    }

    @Test
    fun executeLocalSearchMapsHttpExceptionCode() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            searchClient = RecordingLocalSearchClient(
                error = LocalSearchHttpException(
                    statusCode = 401,
                    code = "local_search_http_401",
                    message = "Unauthorized",
                ),
            ),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_http"),
                "web_search_local",
                """{"query":"AI news"}""",
            ),
        )

        assertEquals("local_search_http_401", execution.result.error?.code)
        assertEquals("Unauthorized", execution.result.error?.message)
        assertTrue(execution.messageContent.contains(""""statusCode":401"""))
        assertTrue(execution.messageContent.contains(""""retryable":false"""))
        assertTrue(repository.savedResults.value.single().rawOutputJson.orEmpty().contains(""""statusCode":401"""))
    }

    @Test
    fun executeLocalSearchMarksRateLimitAsRetryable() = runTest {
        val executor = toolExecutor(
            clock = clock,
            searchClient = RecordingLocalSearchClient(
                error = LocalSearchHttpException(
                    statusCode = 429,
                    code = "local_search_http_429",
                    message = "Rate limit exceeded",
                ),
            ),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_local_search_429"),
                "web_search_local",
                """{"query":"AI news"}""",
            ),
        )

        assertEquals("local_search_http_429", execution.result.error?.code)
        assertTrue(execution.messageContent.contains(""""statusCode":429"""))
        assertTrue(execution.messageContent.contains(""""retryable":true"""))
    }

    @Test
    fun executeProviderConnectionTestUsesSavedProviderAndDoesNotExposeApiKey() = runTest {
        val provider = textProviderConfig()
        val runner = RecordingProviderConnectionTestRunner(
            result = ProviderConnectionTestResult(
                ok = true,
                statusCode = 200,
                message = "连接正常。",
            ),
        )
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            providerRepository = RecordingProviderConfigRepository(
                providers = listOf(provider),
                apiKeys = mapOf(provider.id to "secret-provider-key"),
            ),
            providerConnectionRunner = runner,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_provider_test"),
                "provider-test",
                """{"providerId":" ${provider.id.value} "}""",
            ),
        )

        val result = repository.savedResults.value.single()
        assertEquals("provider_connection_test", result.toolName)
        assertEquals(ToolPermissionLevel.Network, result.permissionLevel)
        assertEquals(provider, runner.requests.single().provider)
        assertEquals("secret-provider-key", runner.requests.single().apiKey)
        assertTrue(execution.messageContent.contains(""""providerId":"${provider.id.value}""""))
        assertTrue(execution.messageContent.contains(""""providerName":"Text Provider""""))
        assertTrue(execution.messageContent.contains(""""providerType":"openai_compatible""""))
        assertTrue(execution.messageContent.contains(""""defaultModel":"gpt-4.1-mini""""))
        assertTrue(execution.messageContent.contains(""""ok":true"""))
        assertTrue(execution.messageContent.contains(""""statusCode":200"""))
        assertFalse(execution.messageContent.contains("secret-provider-key"))
    }

    @Test
    fun executeProviderConnectionTestReturnsFailedConnectionAsCompletedToolOutput() = runTest {
        val provider = textProviderConfig()
        val repository = RecordingToolInvocationRepository()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            providerRepository = RecordingProviderConfigRepository(
                providers = listOf(provider),
                apiKeys = mapOf(provider.id to "secret-provider-key"),
            ),
            providerConnectionRunner = RecordingProviderConnectionTestRunner(
                result = ProviderConnectionTestResult(
                    ok = false,
                    statusCode = 401,
                    message = "Unauthorized",
                ),
            ),
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_provider_failed"),
                "provider_connection_test",
                """{"providerId":"${provider.id.value}"}""",
            ),
        )

        val result = repository.savedResults.value.single()
        assertEquals("provider_connection_test", result.toolName)
        assertEquals(null, result.error)
        assertTrue(execution.messageContent.contains(""""ok":false"""))
        assertTrue(execution.messageContent.contains(""""statusCode":401"""))
        assertTrue(execution.messageContent.contains("Unauthorized"))
    }

    @Test
    fun executeProviderConnectionTestRejectsMissingProvider() = runTest {
        val repository = RecordingToolInvocationRepository()
        val runner = RecordingProviderConnectionTestRunner()
        val executor = toolExecutor(
            clock = clock,
            toolInvocationRepository = repository,
            providerConnectionRunner = runner,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_provider_missing"),
                "test_provider",
                """{"providerId":"missing-provider"}""",
            ),
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("Provider 不存在：missing-provider", execution.result.error?.message)
        assertEquals(emptyList<ProviderConnectionTestRequest>(), runner.requests)
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
    fun executeImageGenerationPrefersImageRoleModelOverSavedImagePreferences() = runTest {
        val provider = imageProviderConfig().copy(
            models = imageProviderConfig().models + ModelConfig(
                id = "role-image-model",
                displayName = "role-image-model",
                capability = ModelCapability(
                    model = "role-image-model",
                    text = false,
                    vision = false,
                    imageGeneration = true,
                    toolCalling = false,
                    structuredOutput = false,
                    longContext = false,
                    maxContextTokens = null,
                ),
            ),
        )
        val imageProvider = RecordingImageGenerationProvider(
            ImageGenerationProviderResponse(images = listOf(base64Image(byteArrayOf(1, 2, 3)))),
        )
        val executor = toolExecutor(
            clock = clock,
            providerRepository = RecordingProviderConfigRepository(
                providers = listOf(provider),
                apiKeys = mapOf(provider.id to "image-key"),
            ),
            preferencesRepository = RecordingImageGenerationPreferencesRepository(
                ImageGenerationPreferences(providerId = provider.id.value, model = "gpt-image-2"),
            ),
            modelRolePreferenceRepository = RecordingModelRolePreferenceRepository(
                listOf(
                    ModelRolePreference(
                        id = ModelRolePreferenceId("image-provider:Image"),
                        providerId = provider.id,
                        role = ModelRole.Image,
                        model = "role-image-model",
                        updatedAt = clock.instant(),
                    ),
                ),
            ),
            imageProvider = imageProvider,
        )

        executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                ToolCallId("call_image_role"),
                "image_generation",
                """{"prompt":"Draw a cat"}""",
            ),
        )

        assertEquals("role-image-model", imageProvider.requests.single().model)
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
            assertEquals(localToolNames + "web_search", tools.map { it.name })
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

            assertEquals(localToolNames + "web_search", tools.map { it.name })
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

            assertEquals(localToolNames + "web_search", first.map { it.name })
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

            assertEquals(localToolNames + "web_search", first.map { it.name })
            assertEquals(localToolNames + "code_sandbox", second.map { it.name })
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

            assertEquals(localToolNames + "web_search", first.map { it.name })
            assertEquals(localToolNames + "code_sandbox", second.map { it.name })
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

            assertEquals(List(5) { localToolNames + "web_search" }, results)
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

            assertEquals(localToolNames + "web_search", tools.map { it.name })
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
    modelRolePreferenceRepository: ModelRolePreferenceRepository = RecordingModelRolePreferenceRepository(),
    imageProvider: ImageGenerationProvider = RecordingImageGenerationProvider(),
    imageStorage: ImageStorage = RecordingImageStorage(),
    scriptRunner: LocalScriptRunner = RecordingScriptRunner(),
    fileReader: AuthorizedFileReader = RecordingFileReader(),
    providerConnectionRunner: ProviderConnectionTestRunner = RecordingProviderConnectionTestRunner(),
    searchConfigProvider: suspend () -> SearchConfig = { enabledSearchConfig() },
    searchClient: LocalSearchClient = RecordingLocalSearchClient(),
    toolSettingsProvider: suspend () -> Map<String, ToolRuntimeSetting> = { emptyMap() },
): ToolExecutor =
    ToolExecutor(
        gatewaySettingsProvider = gatewaySettingsProvider,
        gatewayClientProvider = gatewayClientProvider,
        toolInvocationRepository = toolInvocationRepository,
        providerRepository = providerRepository,
        preferencesRepository = preferencesRepository,
        modelRolePreferenceRepository = modelRolePreferenceRepository,
        imageGenerationRepository = imageGenerationRepository,
        imageProvider = imageProvider,
        imageStorage = imageStorage,
        clock = clock,
        localToolExecutor = LocalToolExecutor(
            defaultLocalTools(
                clock = clock,
                scriptRunner = scriptRunner,
                fileReader = fileReader,
                providerRepository = providerRepository,
                providerConnectionRunner = providerConnectionRunner,
                searchConfigProvider = searchConfigProvider,
                searchClient = searchClient,
            ),
        ),
        toolSettingsProvider = toolSettingsProvider,
    )

private fun enabledSearchConfig(): SearchConfig =
    SearchConfig(
        enabled = true,
        provider = SearchProvider.Tavily,
        baseUrl = "https://api.tavily.com",
        apiKey = "test-search-key",
        maxResults = 5,
        searchDepth = "basic",
        topic = "general",
    )

private fun textProviderConfig(): ProviderConfig =
    ProviderConfig(
        id = ProviderId("text-provider"),
        name = "Text Provider",
        type = ProviderType.OpenAICompatible,
        baseUrl = "https://example.test/v1",
        apiKeyRef = "text-key-ref",
        headers = emptyMap(),
        models = listOf(
            ModelConfig(
                id = "gpt-4.1-mini",
                displayName = "gpt-4.1-mini",
                capability = ModelCapability(
                    model = "gpt-4.1-mini",
                    text = true,
                    vision = false,
                    imageGeneration = false,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = false,
                    maxContextTokens = null,
                ),
            ),
        ),
        defaultModel = "gpt-4.1-mini",
        enabled = true,
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

private class RecordingModelRolePreferenceRepository(
    initialPreferences: List<ModelRolePreference> = emptyList(),
) : ModelRolePreferenceRepository {
    private val preferences = MutableStateFlow(initialPreferences)

    override fun observeRolePreferences(providerId: ProviderId): Flow<List<ModelRolePreference>> =
        flowOf(preferences.value.filter { it.providerId == providerId })

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> = preferences

    override suspend fun getRoleModel(providerId: ProviderId, role: ModelRole): String? =
        preferences.value.firstOrNull { it.providerId == providerId && it.role == role }?.model

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) {
        preferences.value = preferences.value.filterNot { it.providerId == providerId && it.role == role } +
            listOfNotNull(
                model?.trim()?.takeIf { it.isNotBlank() }?.let {
                    ModelRolePreference(
                        id = ModelRolePreferenceId("${providerId.value}:${role.name}"),
                        providerId = providerId,
                        role = role,
                        model = it,
                        updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
                    )
                },
            )
    }

    override suspend fun deleteForProvider(providerId: ProviderId) {
        preferences.value = preferences.value.filterNot { it.providerId == providerId }
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

private class RecordingScriptRunner(
    private val supported: Boolean = true,
    private val result: LocalScriptRunResult = LocalScriptRunResult(
        output = "null",
        durationMs = 1,
        timedOut = false,
        truncated = false,
    ),
    private val error: Throwable? = null,
) : LocalScriptRunner {
    val requests = mutableListOf<LocalScriptRunRequest>()

    override fun isSupported(): Boolean = supported

    override suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult {
        requests += request
        error?.let { throw it }
        return result
    }
}

private class RecordingFileReader(
    private val result: AuthorizedFileReadResult = AuthorizedFileReadResult(
        fileName = "file.txt",
        mimeType = "text/plain",
        sizeBytes = 0,
        content = "",
        truncated = false,
        unsupportedReason = null,
    ),
) : AuthorizedFileReader {
    val requests = mutableListOf<AuthorizedFileReadRequest>()

    override suspend fun read(request: AuthorizedFileReadRequest): AuthorizedFileReadResult {
        requests += request
        return result
    }
}

private data class LocalSearchRequest(
    val query: String,
    val config: SearchConfig,
)

private class RecordingLocalSearchClient(
    private val response: SearchResponse = SearchResponse(
        query = "AI news",
        fetchedAt = Instant.parse("2026-06-01T00:00:01Z"),
        results = listOf(
            SearchResult(
                title = "Local search title",
                summary = "Local search summary",
                url = "https://example.com/local",
                source = "example.com",
                publishedAt = Instant.parse("2026-05-31T00:00:00Z"),
            ),
        ),
    ),
    private val error: Throwable? = null,
) : LocalSearchClient {
    val requests = mutableListOf<LocalSearchRequest>()

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        requests += LocalSearchRequest(query, config)
        error?.let { throw it }
        return response.copy(query = query)
    }
}

private data class ProviderConnectionTestRequest(
    val provider: ProviderConfig,
    val apiKey: String?,
)

private class RecordingProviderConnectionTestRunner(
    private val result: ProviderConnectionTestResult = ProviderConnectionTestResult(
        ok = true,
        statusCode = 200,
        message = "OK",
    ),
) : ProviderConnectionTestRunner {
    val requests = mutableListOf<ProviderConnectionTestRequest>()

    override suspend fun test(provider: ProviderConfig, apiKey: String?): ProviderConnectionTestResult {
        requests += ProviderConnectionTestRequest(provider, apiKey)
        return result
    }
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
