package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
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
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import com.aichat.workbench.tool.gateway.GatewayClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun sendMessageUsesSelectedProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = FakeConversationRepository(clock)
        val providerRepository = FakeProviderConfigRepository(
            providers = listOf(openAi, compatible),
            apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
        )
        val openAiProvider = RecordingChatProvider()
        val compatibleProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("兼容回复"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            openAiProvider = openAiProvider,
            compatibleProvider = compatibleProvider,
        )
        advanceUntilIdle()

        viewModel.selectProvider(compatible.id.value)
        viewModel.updateModelDraft("compatible-model")
        viewModel.updateInput("Hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(0, openAiProvider.requests.size)
        assertEquals(1, compatibleProvider.requests.size)
        val request = compatibleProvider.requests.single()
        assertEquals(compatible, request.provider)
        assertEquals("compatible-key", request.apiKey)
        assertEquals("compatible-model", request.model)
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Hello")), request.messages)
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals("", viewModel.state.value.input)
        assertTrue(conversationRepository.allMessages().any { it.content == "兼容回复" && it.status == MessageStatus.Completed })
    }

    @Test
    fun observesOnlyRegisteredChatProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val anthropic = provider("anthropic", ProviderType.Anthropic)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(anthropic, openAi),
                apiKeys = mapOf(openAi.id to "openai-key", anthropic.id to "anthropic-key"),
            ),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertEquals(listOf(openAi), viewModel.state.value.providers)
        assertEquals(openAi.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("openai-model", viewModel.state.value.modelDraft)
    }

    @Test
    fun observesOnlyTextCapableChatProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val chatProvider = provider(
            id = "chat",
            type = ProviderType.OpenAI,
            defaultModel = "gpt-5.4",
            models = listOf(model("gpt-5.4", text = true)),
        )
        val imageProvider = provider(
            id = "image",
            type = ProviderType.OpenAICompatible,
            defaultModel = "gpt-image-2",
            models = listOf(model("gpt-image-2", text = false, imageGeneration = true)),
        )
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(imageProvider, chatProvider),
                apiKeys = mapOf(chatProvider.id to "chat-key", imageProvider.id to "image-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertEquals(listOf(chatProvider), viewModel.state.value.providers)
        assertEquals(chatProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-5.4", viewModel.state.value.modelDraft)
    }

    @Test
    fun selectProviderResetsModelToSelectedProviderDefault() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider("compatible", ProviderType.OpenAICompatible)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(openAi, compatible),
                apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateModelDraft("manual-openai-model")
        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("compatible-model", viewModel.state.value.modelDraft)
    }

    @Test
    fun selectProviderFallsBackToFirstDiscoveredModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider(
            id = "compatible",
            type = ProviderType.OpenAICompatible,
            defaultModel = null,
            models = listOf(ModelConfig("model-a", "Model A", capability = null)),
        )
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(openAi, compatible),
                apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateModelDraft("manual-openai-model")
        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("model-a", viewModel.state.value.modelDraft)
    }

    @Test
    fun editMessageSendsRewrittenHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id, defaultModel = "gpt-test")
        val originalUser = message(conversation.id, MessageRole.User, "Original", MessageStatus.Completed)
        conversationRepository.seed(conversation, listOf(originalUser, message(conversation.id, MessageRole.Assistant, "Old", MessageStatus.Completed)))
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("New answer"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.editMessage(originalUser.id)
        viewModel.updateInput("Revised")
        viewModel.sendMessage()
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Revised")), request.messages)
        assertNotNull(conversationRepository.allMessages().first { it.content == "Revised" }.parentMessageId)
    }

    @Test
    fun sendMessageWithImageSendsImageContentParts() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("Image answer"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        val image = MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")
        advanceUntilIdle()

        viewModel.addImageDraft(image)
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(
            listOf(
                ProviderChatMessage(
                    role = MessageRole.User,
                    content = "图片消息",
                    contentParts = listOf(MessagePart.Text("图片消息"), image),
                ),
            ),
            chatProvider.requests.single().messages,
        )
        assertEquals(emptyList<MessagePart.Image>(), viewModel.state.value.imageDrafts)
    }

    @Test
    fun sendMessageWithImageShowsErrorForKnownNonVisionModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI, vision = false)
        val conversationRepository = FakeConversationRepository(clock)
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("should not send"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        val image = MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")
        advanceUntilIdle()

        viewModel.addImageDraft(image)
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(0, chatProvider.requests.size)
        assertEquals("当前模型不支持图片输入，请切换到视觉模型。", viewModel.state.value.error)
        assertEquals(listOf(image), viewModel.state.value.imageDrafts)
    }

    @Test
    fun attachFileAppendsExplicitFileReadInstruction() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.attachFile("file:///sdcard/secret.txt")
        viewModel.updateInput("请总结这个文件")
        viewModel.attachFile("""content://docs/my "notes".md""")

        val input = viewModel.state.value.input
        assertTrue(input.startsWith("请总结这个文件"))
        assertTrue(input.contains("工具：file_read"))
        assertTrue(input.contains("""参数：{"uri":"content://docs/my \"notes\".md","maxBytes":65536}"""))
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun attachFileRejectsNonPickerUriWithoutChangingDraft() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateInput("请总结这个文件")
        viewModel.attachFile("file:///sdcard/secret.txt")

        assertEquals("请总结这个文件", viewModel.state.value.input)
        assertEquals("只能读取通过系统文件选择器授权的 content:// 文件。", viewModel.state.value.error)
    }

    @Test
    fun attachFileReplacesPreviousGeneratedFileReadInstruction() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateInput("请总结这个文件")
        viewModel.attachFile("content://docs/old.md")
        viewModel.attachFile("content://docs/new.md")

        val input = viewModel.state.value.input

        assertTrue(input.startsWith("请总结这个文件"))
        assertFalse(input.contains("content://docs/old.md"))
        assertTrue(input.contains("content://docs/new.md"))
        assertEquals(1, Regex("工具：file_read").findAll(input).count())
    }

    @Test
    fun clearAttachedFileTaskRemovesGeneratedFileReadInstructionOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateInput("请总结这个文件")
        viewModel.attachFile("content://docs/notes.md")
        assertTrue(viewModel.state.value.input.hasFileReadInstruction())

        viewModel.clearAttachedFileTask()

        assertEquals("请总结这个文件", viewModel.state.value.input)
        assertFalse(viewModel.state.value.input.hasFileReadInstruction())

        viewModel.attachFile("content://docs/notes.md")
        viewModel.updateInput(viewModel.state.value.input.removePrefix("请总结这个文件\n\n"))
        viewModel.clearAttachedFileTask()

        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun clearAttachedFileTaskIgnoresUserWrittenFileReadText() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        val userText = """
            请解释这段文字，不要执行工具。
            工具：file_read
            参数：我只是讨论这个格式
        """.trimIndent()

        viewModel.updateInput(userText)
        assertFalse(viewModel.state.value.input.hasFileReadInstruction())

        viewModel.clearAttachedFileTask()

        assertEquals(userText, viewModel.state.value.input)
    }

    @Test
    fun fileReadInstructionDetectionRequiresGeneratedJsonArguments() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        val userText = """
            请读取我刚通过系统文件选择器授权的文件，并基于文件内容继续处理。
            工具：file_read
            参数：请先解释这个格式，不要执行
        """.trimIndent()

        viewModel.updateInput(userText)
        assertFalse(viewModel.state.value.input.hasFileReadInstruction())

        viewModel.clearAttachedFileTask()

        assertEquals(userText, viewModel.state.value.input)
    }

    @Test
    fun clearAttachedFileTaskPreservesUserTextAfterGeneratedInstruction() =
        runTest(mainDispatcherRule.testDispatcher) {
            val openAi = provider("openai", ProviderType.OpenAI)
            val viewModel = startViewModel(
                conversationRepository = FakeConversationRepository(clock),
                providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
                openAiProvider = RecordingChatProvider(),
            )
            advanceUntilIdle()

            viewModel.updateInput("请总结这个文件")
            viewModel.attachFile("content://docs/notes.md")
            viewModel.updateInput("${viewModel.state.value.input}\n\n重点检查风险项")

            viewModel.clearAttachedFileTask()

            assertEquals("请总结这个文件\n\n重点检查风险项", viewModel.state.value.input)
            assertFalse(viewModel.state.value.input.hasFileReadInstruction())
        }

    @Test
    fun imagePromptActionsReuseAndRegenerateDraft() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.reuseImagePrompt("  Draw a city  ")

        assertEquals("Draw a city", viewModel.state.value.input)

        viewModel.regenerateImagePrompt("""Draw "city" again""")

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：image_generation"))
        assertTrue(input.contains("联网且可能产生费用"))
        assertTrue(input.contains("不要自动上传本地图片"))
        assertTrue(input.contains("""参数：{"prompt":"Draw \"city\" again","count":1}"""))
    }

    @Test
    fun starterToolActionsPrepareExplicitSearchAndLocalJsInstructions() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareSearchTask("AI \"news\"")

        val searchInput = viewModel.state.value.input
        assertTrue(searchInput.contains("工具：web_search_local"))
        assertTrue(searchInput.contains("关键结论必须标注对应来源 URL"))
        assertTrue(searchInput.contains("没有可引用来源"))
        assertTrue(searchInput.contains("""参数：{"query":"AI \"news\""}"""))

        viewModel.prepareLocalJsTask("""return JSON.stringify({ "ok": true })""")

        val jsInput = viewModel.state.value.input
        assertTrue(jsInput.contains("工具：local_js"))
        assertTrue(jsInput.contains("只允许纯计算或文本处理"))
        assertTrue(jsInput.contains("不要请求网络、文件系统、系统命令或 Android Context"))
        assertTrue(jsInput.contains("\"language\":\"javascript\""))
        assertTrue(jsInput.contains("""return JSON.stringify({ \"ok\": true })"""))
    }

    @Test
    fun starterToolActionsPrepareTextTransformAndDiffPreviewInstructions() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareTextTransformTask("""{"name":"A"}""")

        val textInput = viewModel.state.value.input
        assertTrue(textInput.contains("工具：text_transform"))
        assertTrue(textInput.contains(""""operation":"json_format""""))
        assertTrue(textInput.contains("""\"name\":\"A\""""))

        viewModel.prepareCodeDiffPreviewTask("""fun answer() = "old"""")

        val diffInput = viewModel.state.value.input
        assertTrue(diffInput.contains("工具：code_diff_preview"))
        assertTrue(diffInput.contains(""""fileName":"snippet""""))
        assertTrue(diffInput.contains(""""original":"fun answer() = \"old\"""""))
        assertTrue(diffInput.contains(""""modified":"fun answer() = \"old\"""""))
    }

    @Test
    fun continueWithToolResultPreparesFollowUpDraft() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.continueWithToolResult("local_js", """{"output":"42"}""")

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：local_js"))
        assertTrue(input.contains("工具结果："))
        assertTrue(input.contains("""{"output":"42"}"""))
    }

    @Test
    fun continueWithFileReadResultKeepsPreviewOnlyBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.continueWithToolResult(
            "read_file",
            """{"fileName":"notes.md","preview":"# Notes","sentToModel":false}""",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：file_read"))
        assertTrue(input.contains("只包含文件元数据和文本预览"))
        assertTrue(input.contains("不代表完整文件内容已发送给模型"))
        assertTrue(input.contains("不要编造未出现在预览中的内容"))
        assertTrue(input.contains(""""sentToModel":false"""))
    }

    @Test
    fun prepareLocalJsRecoveryTaskKeepsResultAndRequestsAdjustedRerunPlan() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareLocalJsRecoveryTask("""{"output":"partial","timedOut":true,"truncated":false}""")

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：local_js"))
        assertTrue(input.contains("结果不完整"))
        assertTrue(input.contains("新的 local_js 参数"))
        assertTrue(input.contains("不要请求网络、文件系统、系统命令或 Android Context"))
        assertTrue(input.contains(""""timedOut":true"""))
    }

    @Test
    fun prepareToolRecoveryTaskKeepsToolNameReasonAndResult() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "text_transform",
            toolResult = """{"operation":"regex_preview","truncated":true}""",
            reason = "文本转换结果已截断",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：text_transform"))
        assertTrue(input.contains("原因：文本转换结果已截断"))
        assertTrue(input.contains("新的 text_transform 参数"))
        assertTrue(input.contains(""""truncated":true"""))
    }

    @Test
    fun prepareFileReadRecoveryTaskKeepsSystemPickerBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "file_read",
            toolResult = """{"uri":"content://docs/a.pdf","status":"unsupported","unsupportedReason":"PDF 暂不支持"}""",
            reason = "文件读取结果不完整，需要重新选择文件或改用受支持的文本格式。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：file_read"))
        assertTrue(input.contains("重新选择文件"))
        assertTrue(input.contains("新的 file_read 参数"))
        assertTrue(input.contains("content://docs/a.pdf"))
    }

    @Test
    fun prepareLocalJsRecoveryTaskFromGenericToolPathKeepsSandboxBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "local_js",
            toolResult = """{"timedOut":true,"output":"partial"}""",
            reason = "执行超时，需要减少循环或调高 timeoutMillis。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：local_js"))
        assertTrue(input.contains("新的 local_js 参数"))
        assertTrue(input.contains("不要请求网络、文件系统、系统命令或 Android Context"))
        assertTrue(input.contains("执行前确认超时和输出截断设置"))
        assertTrue(input.contains(""""timedOut":true"""))
    }

    @Test
    fun prepareWebSearchRecoveryTaskKeepsCitationRequirement() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "web_search_local",
            toolResult = """{"results":[]}""",
            reason = "没有搜索结果，需要换关键词。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：web_search_local"))
        assertTrue(input.contains("新的 web_search_local 参数"))
        assertTrue(input.contains("关键结论必须标注对应来源 URL"))
        assertTrue(input.contains("没有可引用来源"))
        assertTrue(input.contains(""""results":[]"""))
    }

    @Test
    fun prepareToolRecoveryTaskCanonicalizesToolAliasInDraft() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "web-search",
            toolResult = """{"results":[]}""",
            reason = "需要换关键词。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：web_search"))
        assertTrue(input.contains("新的 web_search 参数"))
        assertTrue(input.contains("关键结论必须标注对应来源 URL"))
    }

    @Test
    fun prepareProviderConnectionRecoveryTaskDoesNotRequestApiKeyPlaintext() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "provider_connection_test",
            toolResult = """{"ok":false,"statusCode":401}""",
            reason = "Provider 鉴权失败。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：provider_connection_test"))
        assertTrue(input.contains("新的 provider_connection_test 参数"))
        assertTrue(input.contains("已保存的 Provider 配置"))
        assertTrue(input.contains("不要输出或索要 API Key 明文"))
        assertTrue(input.contains(""""statusCode":401"""))
    }

    @Test
    fun prepareImageGenerationRecoveryTaskKeepsPaidNetworkBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.prepareToolRecoveryTask(
            toolName = "image_generation",
            toolResult = """{"code":"rate_limited","statusCode":429,"retryable":true}""",
            reason = "请求被限流，请稍后重试或切换模型/Provider。",
        )

        val input = viewModel.state.value.input
        assertTrue(input.contains("工具：image_generation"))
        assertTrue(input.contains("新的 image_generation 参数"))
        assertTrue(input.contains("联网且可能产生费用"))
        assertTrue(input.contains("执行前必须确认 Provider、模型、数量、尺寸和质量"))
        assertTrue(input.contains("不要自动上传本地图片"))
        assertTrue(input.contains(""""statusCode":429"""))
    }

    @Test
    fun prepareImageGenerationRecoveryTaskRecognizesCanonicalAliases() =
        runTest(mainDispatcherRule.testDispatcher) {
            val openAi = provider("openai", ProviderType.OpenAI)
            val viewModel = startViewModel(
                conversationRepository = FakeConversationRepository(clock),
                providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
                openAiProvider = RecordingChatProvider(),
            )
            advanceUntilIdle()

            viewModel.prepareToolRecoveryTask(
                toolName = "generate_image",
                toolResult = """{"code":"provider_error"}""",
                reason = "Provider 返回错误。",
            )

            val input = viewModel.state.value.input
            assertTrue(input.contains("工具：image_generation"))
            assertTrue(input.contains("联网且可能产生费用"))
            assertTrue(input.contains("不要自动上传本地图片"))
        }

    @Test
    fun retryMessageSendsHistoryBeforeFailure() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id, defaultModel = "gpt-test")
        val user = message(conversation.id, MessageRole.User, "Question", MessageStatus.Completed)
        val failed = message(
            conversationId = conversation.id,
            role = MessageRole.Assistant,
            content = "",
            status = MessageStatus.Failed,
            providerId = openAi.id,
            model = "failed-model",
            errorSummary = "network error",
        )
        conversationRepository.seed(conversation, listOf(user, failed))
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("Retried"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.retryMessage(failed.id)
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), request.messages)
        assertEquals("failed-model", request.model)
        assertTrue(conversationRepository.allMessages().any { it.parentMessageId == failed.id && it.content == "Retried" })
    }

    @Test
    fun generationStateStaysActiveUntilStreamCompletes() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val events = Channel<ProviderStreamEvent>(Channel.UNLIMITED)
        val chatProvider = RecordingChatProvider(events.receiveAsFlow())
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.updateInput("Stream")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isGenerating)

        events.send(ProviderStreamEvent.TextDelta("partial"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isGenerating)
        assertTrue(viewModel.state.value.messages.any { it.content == "partial" && it.status == MessageStatus.Streaming })

        events.send(ProviderStreamEvent.Completed)
        events.close()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isGenerating)
        assertTrue(conversationRepository.allMessages().any { it.content == "partial" && it.status == MessageStatus.Completed })
    }

    @Test
    fun stopGenerationMarksActiveAssistantMessageCancelled() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val events = Channel<ProviderStreamEvent>(Channel.UNLIMITED)
        val chatProvider = RecordingChatProvider(events.receiveAsFlow())
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.updateInput("Stream")
        viewModel.sendMessage()
        advanceUntilIdle()
        events.send(ProviderStreamEvent.TextDelta("partial"))
        advanceUntilIdle()

        viewModel.stopGeneration()
        advanceUntilIdle()

        val assistant = conversationRepository.allMessages().single { it.role == MessageRole.Assistant }
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals(MessageStatus.Cancelled, assistant.status)
        assertEquals("partial", assistant.content)
        assertEquals("已停止，已保留当前回复内容。", assistant.errorSummary)
    }

    private fun startViewModel(
        conversationRepository: ConversationRepository,
        providerRepository: ProviderConfigRepository,
        openAiProvider: ChatProvider,
        compatibleProvider: ChatProvider = RecordingChatProvider(),
    ): ChatViewModel {
        runCatching { stopKoin() }
        startKoin {
            modules(
                module {
                    single { clock }
                    single<ConversationRepository> { conversationRepository }
                    single<ProviderConfigRepository> { providerRepository }
                    single<PromptPresetRepository> { FakePromptPresetRepository() }
                    single<ToolInvocationRepository> { FakeToolInvocationRepository() }
                    single<ImageGenerationPreferencesRepository> { FakeImageGenerationPreferencesRepository() }
                    single<ImageGenerationRepository> { FakeImageGenerationRepository() }
                    single<ImageGenerationProvider> { FakeImageGenerationProvider() }
                    single<ImageStorage> { FakeImageStorage() }
                    single<ChatProvider>(named("openai")) { openAiProvider }
                    single<ChatProvider>(named("compatible")) { compatibleProvider }
                    single {
                        ProviderRegistry().apply {
                            register(ProviderType.OpenAI.value, get(named("openai")))
                            register(ProviderType.OpenAICompatible.value, get(named("compatible")))
                        }
                    }
                    factory { SavedStateHandle() }
                    factory { ConversationManager(conversationRepository = get(), clock = get()) }
                    factory {
                        ToolExecutor(
                            gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
                            gatewayClientProvider = { GatewayClient() },
                            toolInvocationRepository = get(),
                            providerRepository = get(),
                            preferencesRepository = get(),
                            imageGenerationRepository = get(),
                            imageProvider = get(),
                            imageStorage = get(),
                            clock = get(),
                        )
                    }
                    factory {
                        GenerationController(
                            conversationRepository = get(),
                            providerRepository = get(),
                            conversationManager = get(),
                            conversationCompactor = ConversationCompactor(get(), get()),
                            providerRegistry = get(),
                            toolExecutor = get(),
                            clock = get(),
                        )
                    }
                    factory {
                        ChatViewModel(
                            savedStateHandle = get(),
                            conversationRepository = get(),
                            providerRepository = get(),
                            promptPresetRepository = get(),
                            conversationManager = get(),
                            generationController = get(),
                            providerRegistry = get(),
                        )
                    }
                },
            )
        }
        return get()
    }

    private fun provider(
        id: String,
        type: ProviderType,
        vision: Boolean? = null,
        defaultModel: String? = "$id-model",
        models: List<ModelConfig> = listOfNotNull(
            vision?.let {
                ModelConfig(
                    id = "$id-model",
                    displayName = "$id model",
                    capability = ModelCapability(
                        model = "$id-model",
                        text = true,
                        vision = it,
                        imageGeneration = false,
                        toolCalling = true,
                        structuredOutput = false,
                        longContext = false,
                        maxContextTokens = 32_000,
                    ),
                )
            },
        ),
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = models,
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun model(
        id: String,
        text: Boolean,
        imageGeneration: Boolean = false,
    ): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = text,
                vision = text,
                imageGeneration = imageGeneration,
                toolCalling = text,
                structuredOutput = text,
                longContext = text,
                maxContextTokens = null,
            ),
        )

    private fun conversation(
        defaultProviderId: ProviderId?,
        defaultModel: String?,
    ): Conversation =
        Conversation(
            id = ConversationId("conversation-${defaultProviderId?.value ?: "none"}"),
            title = "Existing",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = defaultProviderId,
            defaultModel = defaultModel,
            modelParameters = ModelParameters(),
            systemPrompt = null,
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
        )

    private fun message(
        conversationId: ConversationId,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        providerId: ProviderId? = null,
        model: String? = null,
        errorSummary: String? = null,
    ): Message =
        Message(
            id = MessageId("message-${messageCounter++}"),
            conversationId = conversationId,
            role = role,
            content = content,
            contentParts = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
            providerId = providerId,
            model = model,
            status = status,
            errorSummary = errorSummary,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = null,
        )

    private var messageCounter = 0
}

class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class RecordingChatProvider(
    private val events: Flow<ProviderStreamEvent> = flowOf(ProviderStreamEvent.Completed),
) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        requests += request
        return ProviderTextResponse("")
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> {
        requests += request
        return events
    }
}

private class FakeProviderConfigRepository(
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

private class FakeConversationRepository(
    private val clock: Clock,
) : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<ConversationId, MutableStateFlow<List<Message>>>()

    fun seed(conversation: Conversation, seedMessages: List<Message>) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
        messages.getOrPut(conversation.id) { MutableStateFlow(emptyList()) }.value = seedMessages
    }

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

    override suspend fun archiveConversation(id: ConversationId) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(archivedAt = clock.instant(), updatedAt = clock.instant()) else it
        }
    }

    override suspend fun deleteConversation(id: ConversationId) {
        conversations.value = conversations.value.filterNot { it.id == id }
        messages.remove(id)
    }

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

private class FakePromptPresetRepository : PromptPresetRepository {
    private val presets = MutableStateFlow<List<PromptPreset>>(emptyList())

    override fun observePromptPresets(): Flow<List<PromptPreset>> = presets

    override suspend fun getPromptPreset(id: PromptPresetId): PromptPreset? =
        presets.value.firstOrNull { it.id == id }

    override suspend fun savePromptPreset(promptPreset: PromptPreset) {
        presets.value = presets.value.filterNot { it.id == promptPreset.id } + promptPreset
    }

    override suspend fun deletePromptPreset(id: PromptPresetId) {
        presets.value = presets.value.filterNot { it.id == id }
    }
}

private class FakeImageGenerationPreferencesRepository : ImageGenerationPreferencesRepository {
    private val preferences = MutableStateFlow(ImageGenerationPreferences())

    override fun observePreferences(): MutableStateFlow<ImageGenerationPreferences> = preferences

    override suspend fun savePreferences(providerId: String?, model: String?) {
        preferences.value = ImageGenerationPreferences(providerId = providerId, model = model)
    }
}

private class FakeImageGenerationRepository : ImageGenerationRepository {
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

private class FakeImageGenerationProvider : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        ImageGenerationProviderResponse(emptyList())
}

private class FakeImageStorage : ImageStorage {
    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
        StoredImagePaths(
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumb/${id.value}.png",
        )

    override suspend fun deleteAllImages() = Unit
}

private class FakeToolInvocationRepository : ToolInvocationRepository {
    private val results = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = results

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        results.value = results.value + toolResult
    }
}
