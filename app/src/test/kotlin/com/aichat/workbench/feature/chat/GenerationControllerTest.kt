package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
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
import com.aichat.workbench.tool.local.defaultLocalTools
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.LocalSearchHttpException
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun startPersistsUserMessageAndCompletedAssistantMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
                register(ProviderType.OpenAICompatible.value, GenerationControllerChatProvider())
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), chatProvider.requests.single().messages)
        assertTrue(conversationRepository.allMessages().any { it.role == MessageRole.User && it.content == "Question" })
        assertTrue(
            conversationRepository.allMessages().any {
                it.role == MessageRole.Assistant &&
                    it.content == "Answer" &&
                    it.status == MessageStatus.Completed
            },
        )
    }

    @Test
    fun newApiAddsHostedWebSearchWhenGatewaySearchIsUnavailable() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("new-api", ProviderType.NewApi)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.NewApi.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "new-api-model", input = "Search"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolNames = chatProvider.requests.single().tools.map { it.name }
        assertTrue(toolNames.contains("time"))
        assertTrue(toolNames.contains("image_generation"))
        assertTrue(toolNames.contains("web_search"))
    }

    @Test
    fun openAiUsesLocalToolsWithoutHostedTools() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "openai-model", input = "Draw a cat"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Draw a cat",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolNames = chatProvider.requests.single().tools.map { it.name }
        assertTrue(toolNames.contains("time"))
        assertTrue(toolNames.contains("image_generation"))
        assertFalse(toolNames.contains("web_search"))
        assertFalse(toolNames.contains("code_interpreter"))
    }

    @Test
    fun customProviderDoesNotAddHostedWebSearchAutomatically() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("custom", ProviderType.Custom)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.Custom.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "custom-model", input = "Search"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolNames = chatProvider.requests.single().tools.map { it.name }
        assertTrue(toolNames.contains("time"))
        assertTrue(toolNames.contains("image_generation"))
        assertFalse(toolNames.contains("web_search"))
    }

    @Test
    fun toolCallExecutesToolAndContinuesGeneration() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(ToolCallId("call_1"), "time", "{}")
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock, toolRepository),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "What time is it?"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "What time is it?",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals(2, chatProvider.requests.size)
        assertTrue(chatProvider.requests.first().tools.any { it.name == "time" })
        assertEquals(
            listOf(
                ProviderChatMessage(MessageRole.User, "What time is it?"),
                ProviderChatMessage(MessageRole.Assistant, "", toolCalls = listOf(toolCall)),
                ProviderChatMessage(
                    role = MessageRole.Tool,
                    content = """{"currentTime":"2026-06-01T00:00:00Z"}""",
                    toolCallId = toolCall.id,
                ),
            ),
            chatProvider.requests[1].messages,
        )
        assertEquals(1, toolRepository.savedResults.value.size)
        assertTrue(conversationRepository.allMessages().any { it.role == MessageRole.Tool && it.toolCallId == toolCall.id })
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" && it.status == MessageStatus.Completed })
    }

    @Test
    fun networkToolWithAllowPolicyExecutesWithoutPendingApproval() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(ToolCallId("call_image"), "image_generation", """{"prompt":"Draw a cat"}""")
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                toolSettingsProvider = {
                    mapOf(
                        "image_generation" to ToolRuntimeSetting(
                            toolName = "image_generation",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Generate image"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Generate image",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals(null, state.pendingToolCall)
        assertEquals(2, chatProvider.requests.size)
        assertEquals("image_generation", toolRepository.savedResults.value.single().toolName)
    }

    @Test
    fun imageToolResultWritesInlineImagePartToChatStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("new-api", ProviderType.NewApi).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                imageCapableModel("gpt-image-1"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "image-key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(
            ToolCallId("call_image"),
            "image_generation",
            """{"prompt":"Draw a cat","count":1}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.NewApi.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                providerRepository = providerRepository,
                modelRolePreferenceRepository = GenerationControllerModelRolePreferenceRepository(
                    listOf(
                        ModelRolePreference(
                            id = ModelRolePreferenceId("new-api:Image"),
                            providerId = provider.id,
                            role = ModelRole.Image,
                            model = "gpt-image-1",
                            updatedAt = clock.instant(),
                        ),
                    ),
                ),
                imageProvider = GenerationControllerImageProvider(
                    ImageGenerationProviderResponse(
                        listOf(GeneratedImage(base64 = "aW1hZ2U=", url = null, revisedPrompt = null)),
                    ),
                ),
                toolSettingsProvider = {
                    mapOf(
                        "image_generation" to ToolRuntimeSetting(
                            toolName = "image_generation",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "Generate image"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Generate image",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        val image = toolMessage.contentParts.filterIsInstance<MessagePart.Image>().single()
        assertEquals("image/png", image.mimeType)
        assertTrue(image.uri.startsWith("original/"))
        assertTrue(toolMessage.toolResult.orEmpty().contains("Draw a cat"))
        assertEquals("image_generation", toolRepository.savedResults.value.single().toolName)
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" })
    }

    @Test
    fun failedImageToolCallUsesImageSpecificRecoverySummary() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("new-api", ProviderType.NewApi).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                imageCapableModel("gpt-image-1"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "image-key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(
            ToolCallId("call_image_failed"),
            "image_generation",
            """{"prompt":"Draw a cat","count":1}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Image failure handled"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.NewApi.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                providerRepository = providerRepository,
                modelRolePreferenceRepository = GenerationControllerModelRolePreferenceRepository(
                    listOf(
                        ModelRolePreference(
                            id = ModelRolePreferenceId("new-api:Image"),
                            providerId = provider.id,
                            role = ModelRole.Image,
                            model = "gpt-image-1",
                            updatedAt = clock.instant(),
                        ),
                    ),
                ),
                imageProvider = GenerationControllerImageProvider(
                    error = ProviderHttpException(
                        ProviderError(
                            code = "rate_limited",
                            message = "too many image requests",
                            statusCode = 429,
                            retryable = true,
                        ),
                    ),
                ),
                toolSettingsProvider = {
                    mapOf(
                        "image_generation" to ToolRuntimeSetting(
                            toolName = "image_generation",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "Generate image"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Generate image",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }

        assertEquals(MessageStatus.Failed, toolMessage.status)
        assertTrue(toolMessage.errorSummary.orEmpty().contains("HTTP：429"))
        assertTrue(toolMessage.errorSummary.orEmpty().contains("图片生成请求被限流"))
        assertTrue(toolMessage.errorSummary.orEmpty().contains("切换图片模型/Provider"))
        assertFalse(toolMessage.errorSummary.orEmpty().contains("搜索 Provider"))
        assertEquals("rate_limited", toolRepository.savedResults.value.single().error?.code)
        assertTrue(conversationRepository.allMessages().any { it.content == "Image failure handled" })
    }

    @Test
    fun highRiskToolIgnoresAllowPolicyAndWaitsForApproval() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(
            ToolCallId("call_file"),
            "file_read",
            """{"uri":"content://docs/notes.md"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                toolSettingsProvider = {
                    mapOf(
                        "file_read" to ToolRuntimeSetting(
                            toolName = "file_read",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Read file"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Read file",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertTrue(state.isGenerating)
        assertEquals(toolCall, state.pendingToolCall?.toolCall)
        assertEquals(emptyList<ToolResult>(), toolRepository.savedResults.value)

        controller.denyToolCall()
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals("tool_denied", toolRepository.savedResults.value.single().error?.code)
        assertEquals(ToolStatus.Denied, toolRepository.savedResults.value.single().status)
        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertEquals(MessageStatus.Cancelled, toolMessage.status)
        assertEquals("用户拒绝执行工具。", toolMessage.errorSummary)
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" && it.status == MessageStatus.Completed })
    }

    @Test
    fun stopPendingHighRiskToolWritesCancelledResultToChatStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(
            ToolCallId("call_file_stop"),
            "file_read",
            """{"uri":"content://docs/notes.md"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Should not continue"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Read file"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Read file",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertTrue(state.isGenerating)
        assertEquals(toolCall, state.pendingToolCall?.toolCall)

        controller.stop(this, onStateChanged = { transform -> state = transform(state) })
        advanceUntilIdle()

        val savedResult = toolRepository.savedResults.value.single()
        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertFalse(state.isGenerating)
        assertEquals(null, state.pendingToolCall)
        assertEquals(ToolStatus.Cancelled, savedResult.status)
        assertEquals("tool_cancelled", savedResult.error?.code)
        assertEquals(MessageStatus.Cancelled, toolMessage.status)
        assertEquals(toolCall.id, toolMessage.toolCallId)
        assertTrue(toolMessage.toolResult.orEmpty().contains("tool_cancelled"))
        assertFalse(conversationRepository.allMessages().any { it.content == "Should not continue" })
    }

    @Test
    fun localJsToolWaitsForApprovalThenWritesResultToChatStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val scriptRunner = GenerationControllerScriptRunner(
            LocalScriptRunResult(
                output = """{"ok":true}""",
                durationMs = 9,
                timedOut = false,
                truncated = false,
            ),
        )
        val toolCall = ToolCall(
            ToolCallId("call_js"),
            "local_js",
            """{"code":"return { ok: true };","inputJson":"{\"source\":\"chat\"}"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("JS result summarized"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(scriptRunner = scriptRunner),
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Run this JS"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Run this JS",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertTrue(state.isGenerating)
        assertEquals(toolCall, state.pendingToolCall?.toolCall)
        assertEquals(emptyList<ToolResult>(), toolRepository.savedResults.value)

        controller.confirmToolCall()
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertFalse(state.isGenerating)
        assertEquals("local_js", toolRepository.savedResults.value.single().toolName)
        assertEquals("""{"source":"chat"}""", scriptRunner.requests.single().inputJson)
        assertTrue(toolMessage.content.contains(""""output":"{\"ok\":true}""""))
        assertTrue(chatProvider.requests[1].messages.any { it.role == MessageRole.Tool && it.toolCallId == toolCall.id })
        assertTrue(conversationRepository.allMessages().any { it.content == "JS result summarized" })
    }

    @Test
    fun cancelledLocalJsToolWritesCancelledResultToChatStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(
            ToolCallId("call_js_cancelled"),
            "local_js",
            """{"code":"return 1;"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Should not continue"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(
                    scriptRunner = GenerationControllerScriptRunner(
                        error = CancellationException("user stopped"),
                    ),
                ),
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Run this JS"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Run this JS",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        controller.confirmToolCall()
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        val savedResult = toolRepository.savedResults.value.single()
        assertFalse(state.isGenerating)
        assertEquals(ToolStatus.Cancelled, savedResult.status)
        assertEquals(MessageStatus.Cancelled, toolMessage.status)
        assertEquals(toolCall.id, toolMessage.toolCallId)
        assertTrue(toolMessage.toolResult.orEmpty().contains("tool_cancelled"))
        assertTrue(toolMessage.errorSummary.orEmpty().contains("工具执行已取消"))
        assertFalse(conversationRepository.allMessages().any { it.content == "Should not continue" })
    }

    @Test
    fun pendingToolArgumentsCanBeEditedBeforeApproval() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val scriptRunner = GenerationControllerScriptRunner(
            LocalScriptRunResult(
                output = """{"ok":true}""",
                durationMs = 9,
                timedOut = false,
                truncated = false,
            ),
        )
        val toolCall = ToolCall(
            ToolCallId("call_js_edit"),
            "local_js",
            """{"code":"return input;","inputJson":"{\"source\":\"model\"}"}""",
        )
        val editedArguments = """{"code":"return input;","inputJson":"{\"source\":\"user\"}"}"""
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Edited JS result summarized"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(scriptRunner = scriptRunner),
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Run this JS"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Run this JS",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals(toolCall, state.pendingToolCall?.toolCall)

        controller.updatePendingToolArguments(editedArguments)
        state = state.copy(
            pendingToolCall = state.pendingToolCall?.let {
                it.copy(toolCall = it.toolCall.copy(arguments = editedArguments))
            },
        )
        controller.confirmToolCall()
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals("""{"source":"user"}""", scriptRunner.requests.single().inputJson)
        assertTrue(conversationRepository.allMessages().any { message ->
            message.role == MessageRole.Assistant &&
                message.toolCalls.any { it.id == toolCall.id && it.arguments == editedArguments }
        })
        assertTrue(conversationRepository.allMessages().any { it.content == "Edited JS result summarized" })
    }

    @Test
    fun fileReadToolWaitsForApprovalThenFeedsContentBackToModel() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val fileReader = GenerationControllerFileReader(
            AuthorizedFileReadResult(
                fileName = "notes.md",
                mimeType = "text/markdown",
                sizeBytes = 28,
                content = "# Notes\nImportant finding",
                truncated = false,
                unsupportedReason = null,
            ),
        )
        val toolCall = ToolCall(
            ToolCallId("call_file"),
            "file_read",
            """{"uri":"content://docs/notes.md","maxBytes":4096}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("File summarized"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(fileReader = fileReader),
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Read file"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Read file",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertTrue(state.isGenerating)
        assertEquals(toolCall, state.pendingToolCall?.toolCall)

        controller.confirmToolCall()
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertFalse(state.isGenerating)
        assertEquals("file_read", toolRepository.savedResults.value.single().toolName)
        assertEquals("content://docs/notes.md", fileReader.requests.single().uri)
        assertEquals(4096, fileReader.requests.single().maxBytes)
        assertTrue(toolMessage.content.contains("notes.md"))
        assertTrue(toolMessage.content.contains("Important finding"))
        assertTrue(chatProvider.requests[1].messages.any { it.role == MessageRole.Tool && it.content.contains("Important finding") })
        assertTrue(conversationRepository.allMessages().any { it.content == "File summarized" })
    }

    @Test
    fun localSearchToolWritesSourcesToChatStreamAndContinuesGeneration() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val searchClient = GenerationControllerSearchClient(
            SearchResponse(
                query = "AI news",
                fetchedAt = clock.instant(),
                results = listOf(
                    SearchResult(
                        title = "AI News",
                        summary = "A sourced update",
                        url = "https://example.com/ai-news",
                        source = "example.com",
                        publishedAt = clock.instant(),
                    ),
                ),
            ),
        )
        val toolCall = ToolCall(
            ToolCallId("call_search"),
            "web_search_local",
            """{"query":"AI news","maxResults":1,"topic":"news"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Search summarized"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(
                    searchConfigProvider = { enabledSearchConfig() },
                    searchClient = searchClient,
                ),
                toolSettingsProvider = {
                    mapOf(
                        "web_search_local" to ToolRuntimeSetting(
                            toolName = "web_search_local",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Search AI news"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search AI news",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertFalse(state.isGenerating)
        assertEquals(null, state.pendingToolCall)
        assertEquals("web_search_local", toolRepository.savedResults.value.single().toolName)
        assertEquals("AI news", searchClient.requests.single().query)
        assertEquals(1, searchClient.requests.single().config.maxResults)
        assertTrue(toolMessage.content.contains("https://example.com/ai-news"))
        assertTrue(toolMessage.content.contains("example.com"))
        assertTrue(chatProvider.requests[1].messages.any { it.role == MessageRole.Tool && it.content.contains("https://example.com/ai-news") })
        assertTrue(conversationRepository.allMessages().any { it.content == "Search summarized" })
    }

    @Test
    fun failedLocalSearchToolPersistsRecoverySummaryInChatStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val searchClient = GenerationControllerSearchClient(
            error = LocalSearchHttpException(
                statusCode = 429,
                code = "local_search_http_429",
                message = "Rate limit exceeded",
            ),
        )
        val toolCall = ToolCall(
            ToolCallId("call_search"),
            "web_search_local",
            """{"query":"AI news","maxResults":1,"topic":"news"}""",
        )
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Search failure handled"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(
                clock = clock,
                toolInvocationRepository = toolRepository,
                localToolExecutor = localToolExecutor(
                    searchConfigProvider = { enabledSearchConfig() },
                    searchClient = searchClient,
                ),
                toolSettingsProvider = {
                    mapOf(
                        "web_search_local" to ToolRuntimeSetting(
                            toolName = "web_search_local",
                            permissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
                        ),
                    )
                },
            ),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Search AI news"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search AI news",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }

        assertFalse(state.isGenerating)
        assertEquals(MessageStatus.Failed, toolMessage.status)
        assertTrue(toolMessage.errorSummary.orEmpty().contains("HTTP：429"))
        assertTrue(toolMessage.errorSummary.orEmpty().contains("可重试：是"))
        assertTrue(toolMessage.errorSummary.orEmpty().contains("请求被限流，稍后重试，或切换搜索 Provider。"))
        assertEquals("local_search_http_429", toolRepository.savedResults.value.single().error?.code)
        assertTrue(conversationRepository.allMessages().any { it.content == "Search failure handled" })
    }

    @Test
    fun failedToolCallPersistsReadableErrorSummary() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(ToolCallId("call_unknown"), "web_search", """{"query":"AI"}""")
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock, toolRepository),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Search the web"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search the web",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertEquals(MessageStatus.Failed, toolMessage.status)
        assertEquals("未知工具。", toolMessage.errorSummary)
        assertEquals("unknown_tool", toolRepository.savedResults.value.single().error?.code)
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" && it.status == MessageStatus.Completed })
    }

    @Test
    fun startUsesCodeRoleModelForCodeTask() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                toolCapableModel("code-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Code"),
                    providerId = provider.id,
                    role = ModelRole.Code,
                    model = "code-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "请用 Kotlin 实现一个排序函数"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "请用 Kotlin 实现一个排序函数",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("code-model", chatProvider.requests.single().model)
    }

    @Test
    fun startKeepsChatModelForNonCodeTask() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                toolCapableModel("code-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Code"),
                    providerId = provider.id,
                    role = ModelRole.Code,
                    model = "code-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "帮我总结今天的安排"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "帮我总结今天的安排",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("chat-model", chatProvider.requests.single().model)
    }

    @Test
    fun startDoesNotTreatPlainGoVerbAsCodeTask() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                toolCapableModel("code-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Code"),
                    providerId = provider.id,
                    role = ModelRole.Code,
                    model = "code-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "Where should I go for dinner?"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Where should I go for dinner?",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("chat-model", chatProvider.requests.single().model)
    }

    @Test
    fun startFallsBackToChatModelWhenCodeRoleModelCannotGenerateText() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                imageCapableModel("image-only-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Code"),
                    providerId = provider.id,
                    role = ModelRole.Code,
                    model = "image-only-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "帮我解释这段 Python 代码"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "帮我解释这段 Python 代码",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("chat-model", chatProvider.requests.single().model)
    }

    @Test
    fun startUsesToolRoleModelWhenToolsAreAvailable() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                toolCapableModel("tool-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Tool"),
                    providerId = provider.id,
                    role = ModelRole.Tool,
                    model = "tool-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "Use tools"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Use tools",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("tool-model", chatProvider.requests.single().model)
        assertTrue(chatProvider.requests.single().tools.isNotEmpty())
    }

    @Test
    fun codeTaskWithToolRoleUsesToolModelForToolPlanning() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("compatible", ProviderType.OpenAICompatible).copy(
            models = listOf(
                toolCapableModel("chat-model"),
                toolCapableModel("code-model"),
                toolCapableModel("tool-model"),
            ),
            defaultModel = "chat-model",
        )
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAICompatible.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            modelRolePreferences = listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Code"),
                    providerId = provider.id,
                    role = ModelRole.Code,
                    model = "code-model",
                    updatedAt = clock.instant(),
                ),
                ModelRolePreference(
                    id = ModelRolePreferenceId("compatible:Tool"),
                    providerId = provider.id,
                    role = ModelRole.Tool,
                    model = "tool-model",
                    updatedAt = clock.instant(),
                ),
            ),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "chat-model", input = "请用 Kotlin 实现一个排序函数"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "请用 Kotlin 实现一个排序函数",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertEquals("tool-model", chatProvider.requests.single().model)
        assertTrue(conversationRepository.allMessages().any { it.role == MessageRole.User && it.model == "code-model" })
    }

    @Test
    fun startShowsClearErrorForUnregisteredProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("anthropic", ProviderType.Anthropic)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Should not send"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "claude-test", input = "Question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals("当前 Provider 暂未接入聊天发送：anthropic。", state.error)
        assertEquals(0, chatProvider.requests.size)
        assertEquals(emptyList<Message>(), conversationRepository.allMessages())
    }

    @Test
    fun startCompressesLongHistoryBeforeProviderRequest() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI, maxContextTokens = 90)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val conversation = conversation(provider)
        conversationRepository.saveConversation(conversation)
        (1..14).forEach { index ->
            conversationRepository.saveMessage(
                historyMessage(
                    conversation = conversation,
                    id = "message-$index",
                    content = "message $index ${"x".repeat(48)}",
                    createdAt = clock.instant().plusMillis(index.toLong()),
                ),
            )
        }
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            eventTurns = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
            completionContent = "compressed summary",
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            conversations = listOf(conversation),
            selectedConversationId = conversation.id,
            messages = conversationRepository.getMessages(conversation.id),
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = provider.defaultModel.orEmpty(), input = "new question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "new question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { readyConversation ->
                state = state.copy(
                    conversations = state.conversations.filterNot { it.id == readyConversation.id } + readyConversation,
                    selectedConversationId = readyConversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val providerRequest = chatProvider.requests.last()
        assertTrue(providerRequest.systemPrompt.orEmpty().contains("compressed summary"))
        assertFalse(providerRequest.messages.any { it.content.startsWith("message 1 ") })
        assertTrue(providerRequest.messages.any { it.content.contains("message 14") })
        assertTrue(conversationRepository.allMessages().any { it.status == MessageStatus.Compressed })
    }

    @Test
    fun startReportsCompressionFailureBeforeProviderStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI, maxContextTokens = 90)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val conversation = conversation(provider)
        conversationRepository.saveConversation(conversation)
        (1..14).forEach { index ->
            conversationRepository.saveMessage(
                historyMessage(
                    conversation = conversation,
                    id = "message-$index",
                    content = "message $index ${"x".repeat(48)}",
                    createdAt = clock.instant().plusMillis(index.toLong()),
                ),
            )
        }
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            eventTurns = listOf(flowOf(ProviderStreamEvent.TextDelta("Should not send"), ProviderStreamEvent.Completed)),
            completionContent = " ",
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            conversations = listOf(conversation),
            selectedConversationId = conversation.id,
            messages = conversationRepository.getMessages(conversation.id),
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = provider.defaultModel.orEmpty(), input = "new question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "new question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { readyConversation ->
                state = state.copy(
                    conversations = state.conversations.filterNot { it.id == readyConversation.id } + readyConversation,
                    selectedConversationId = readyConversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals("长对话压缩失败：长对话压缩摘要为空。", state.error)
        assertEquals(1, chatProvider.requests.size)
        assertFalse(conversationRepository.allMessages().any { it.status == MessageStatus.Compressed })
        assertFalse(conversationRepository.allMessages().any { it.content == "Should not send" })
    }

    private fun provider(id: String, type: ProviderType, maxContextTokens: Int? = null): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = listOfNotNull(
                maxContextTokens?.let {
                    ModelConfig(
                        id = "$id-model",
                        displayName = "$id model",
                        capability = ModelCapability(
                            model = "$id-model",
                            text = true,
                            vision = false,
                            imageGeneration = false,
                            toolCalling = true,
                            structuredOutput = false,
                            longContext = true,
                            maxContextTokens = it,
                        ),
                    )
                },
            ),
            defaultModel = "$id-model",
            enabled = true,
        )

    private fun toolCapableModel(id: String): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = true,
                vision = false,
                imageGeneration = false,
                toolCalling = true,
                structuredOutput = false,
                longContext = true,
                maxContextTokens = null,
            ),
        )

    private fun imageCapableModel(id: String): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = false,
                vision = false,
                imageGeneration = true,
                toolCalling = false,
                structuredOutput = false,
                longContext = false,
                maxContextTokens = null,
            ),
        )

    private fun conversation(provider: ProviderConfig): Conversation =
        Conversation(
            id = ConversationId("conversation-1"),
            title = "Chat",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = provider.id,
            defaultModel = provider.defaultModel,
            modelParameters = ModelParameters(),
            systemPrompt = "Base system",
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
        )

    private fun historyMessage(
        conversation: Conversation,
        id: String,
        content: String,
        createdAt: Instant,
    ): Message =
        Message(
            id = MessageId(id),
            conversationId = conversation.id,
            role = MessageRole.User,
            content = content,
            contentParts = listOf(com.aichat.workbench.domain.model.MessagePart.Text(content)),
            providerId = conversation.defaultProviderId,
            model = conversation.defaultModel,
            status = MessageStatus.Completed,
            errorSummary = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            toolCallId = null,
            parentMessageId = null,
        )
}

private class GenerationControllerChatProvider(
    private val eventTurns: List<Flow<ProviderStreamEvent>> = listOf(flowOf(ProviderStreamEvent.Completed)),
    private val completionContent: String = "",
) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()
    private var streamCount = 0

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        requests += request
        return ProviderTextResponse(completionContent)
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> {
        requests += request
        val index = streamCount++
        return eventTurns.getOrElse(index) { flowOf(ProviderStreamEvent.Completed) }
    }
}

private fun toolExecutor(
    clock: Clock,
    toolInvocationRepository: ToolInvocationRepository = GenerationControllerToolInvocationRepository(),
    providerRepository: ProviderConfigRepository = GenerationControllerProviderRepository(emptyList(), emptyMap()),
    modelRolePreferenceRepository: ModelRolePreferenceRepository = GenerationControllerModelRolePreferenceRepository(),
    imageProvider: ImageGenerationProvider = GenerationControllerImageProvider(),
    localToolExecutor: LocalToolExecutor = localToolExecutor(clock = clock),
    toolSettingsProvider: suspend () -> Map<String, ToolRuntimeSetting> = { emptyMap() },
): ToolExecutor =
    ToolExecutor(
        gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
        gatewayClientProvider = { GatewayClient() },
        toolInvocationRepository = toolInvocationRepository,
        providerRepository = providerRepository,
        preferencesRepository = GenerationControllerImagePreferencesRepository(),
        modelRolePreferenceRepository = modelRolePreferenceRepository,
        imageGenerationRepository = GenerationControllerImageGenerationRepository(),
        imageProvider = imageProvider,
        imageStorage = GenerationControllerImageStorage(),
        clock = clock,
        localToolExecutor = localToolExecutor,
        toolSettingsProvider = toolSettingsProvider,
    )

private fun localToolExecutor(
    clock: Clock = Clock.systemUTC(),
    scriptRunner: LocalScriptRunner = GenerationControllerScriptRunner(),
    fileReader: AuthorizedFileReader = GenerationControllerFileReader(),
    searchConfigProvider: (suspend () -> SearchConfig)? = null,
    searchClient: LocalSearchClient? = null,
): LocalToolExecutor =
    LocalToolExecutor(
        defaultLocalTools(
            clock = clock,
            scriptRunner = scriptRunner,
            fileReader = fileReader,
            searchConfigProvider = searchConfigProvider,
            searchClient = searchClient,
        ),
    )

private fun enabledSearchConfig(): SearchConfig =
    SearchConfig(
        enabled = true,
        provider = SearchProvider.Tavily,
        baseUrl = "https://api.tavily.com",
        apiKey = "search-key",
        maxResults = 5,
        searchDepth = "basic",
        topic = "general",
    )

private class GenerationControllerToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}

private class GenerationControllerModelRolePreferenceRepository(
    initialPreferences: List<ModelRolePreference> = emptyList(),
) : ModelRolePreferenceRepository {
    private val preferences = MutableStateFlow(initialPreferences)

    override fun observeRolePreferences(providerId: ProviderId): Flow<List<ModelRolePreference>> =
        flowOf(preferences.value.filter { it.providerId == providerId })

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> = preferences

    override suspend fun getRoleModel(providerId: ProviderId, role: ModelRole): String? =
        preferences.value.firstOrNull { it.providerId == providerId && it.role == role }?.model

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) = Unit

    override suspend fun deleteForProvider(providerId: ProviderId) = Unit
}

private class GenerationControllerImagePreferencesRepository : ImageGenerationPreferencesRepository {
    private val preferences = MutableStateFlow(ImageGenerationPreferences())

    override fun observePreferences(): MutableStateFlow<ImageGenerationPreferences> = preferences

    override suspend fun savePreferences(providerId: String?, model: String?) {
        preferences.value = ImageGenerationPreferences(providerId = providerId, model = model)
    }
}

private class GenerationControllerImageGenerationRepository : ImageGenerationRepository {
    private val generations = MutableStateFlow<List<ImageGeneration>>(emptyList())

    override fun observeImageGenerations(): Flow<List<ImageGeneration>> = generations

    override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
        generations.value.firstOrNull { it.id == id }

    override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
        generations.value = generations.value.filterNot { it.id == imageGeneration.id } + imageGeneration
    }

    override suspend fun deleteImageGeneration(id: ImageGenerationId) {
        generations.value = generations.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllImageGenerations() {
        generations.value = emptyList()
    }
}

private class GenerationControllerImageProvider(
    private val response: ImageGenerationProviderResponse = ImageGenerationProviderResponse(emptyList()),
    private val error: RuntimeException? = null,
) : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse {
        error?.let { throw it }
        return response
    }
}

private class GenerationControllerImageStorage : ImageStorage {
    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
        StoredImagePaths(
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumb/${id.value}.png",
        )

    override suspend fun deleteAllImages() = Unit
}

private class GenerationControllerScriptRunner(
    private val result: LocalScriptRunResult = LocalScriptRunResult(
        output = "null",
        durationMs = 1,
        timedOut = false,
        truncated = false,
    ),
    private val error: Throwable? = null,
) : LocalScriptRunner {
    val requests = mutableListOf<LocalScriptRunRequest>()

    override fun isSupported(): Boolean = true

    override suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult {
        requests += request
        error?.let { throw it }
        return result
    }
}

private class GenerationControllerFileReader(
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

private data class GenerationControllerSearchRequest(
    val query: String,
    val config: SearchConfig,
)

private class GenerationControllerSearchClient(
    private val response: SearchResponse = SearchResponse(
        query = "query",
        fetchedAt = Instant.parse("2026-06-01T00:00:00Z"),
        results = emptyList(),
    ),
    private val error: Throwable? = null,
) : LocalSearchClient {
    val requests = mutableListOf<GenerationControllerSearchRequest>()

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        requests += GenerationControllerSearchRequest(query, config)
        error?.let { throw it }
        return response.copy(query = query)
    }
}

private class GenerationControllerProviderRepository(
    providers: List<ProviderConfig>,
    private val apiKeys: Map<ProviderId, String>,
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

private class GenerationControllerConversationRepository(
    private val clock: Clock,
) : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<ConversationId, MutableStateFlow<List<Message>>>()

    fun allMessages(): List<Message> =
        messages.values.flatMap { it.value }

    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
        messages.getOrPut(conversation.id) { MutableStateFlow(emptyList()) }
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = clock.instant()) else it
        }
    }

    override suspend fun archiveConversation(id: ConversationId) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value

    override suspend fun saveMessage(message: Message) {
        val flow = messages.getOrPut(message.conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
}
