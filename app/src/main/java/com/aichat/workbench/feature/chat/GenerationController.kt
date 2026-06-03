package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.requiresConfirmation
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GenerationController(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val conversationManager: ConversationManager,
    private val conversationCompactor: ConversationCompactor,
    private val providerRegistry: ProviderRegistry,
    private val toolExecutor: ToolExecutor,
    private val clock: Clock,
) {
    private var generationJob: Job? = null
    private var activeAssistantMessage: Message? = null
    private var pendingToolApproval: CompletableDeferred<Boolean>? = null

    val isActive: Boolean
        get() = generationJob?.isActive == true

    fun start(
        scope: CoroutineScope,
        current: ChatUiState,
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
        onConversationReady: (com.aichat.workbench.domain.model.Conversation) -> Unit,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        if (isActive) return
        generationJob = scope.launch {
            runGeneration(
                current = current,
                userText = userText,
                editedMessage = editedMessage,
                retryFailedMessage = retryFailedMessage,
                onConversationReady = onConversationReady,
                onStateChanged = onStateChanged,
            )
        }
    }

    fun stop(
        scope: CoroutineScope,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        val active = activeAssistantMessage
        generationJob?.cancel()
        generationJob = null
        if (active != null) {
            scope.launch {
                conversationRepository.saveMessage(
                    active.copy(
                        status = MessageStatus.Cancelled,
                        errorSummary = "已停止，已保留当前回复内容。",
                        updatedAt = clock.instant(),
                    ),
                )
            }
        }
        activeAssistantMessage = null
        pendingToolApproval?.complete(false)
        pendingToolApproval = null
        onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
    }

    fun confirmToolCall() {
        pendingToolApproval?.complete(true)
    }

    fun denyToolCall() {
        pendingToolApproval?.complete(false)
    }

    private suspend fun runGeneration(
        current: ChatUiState,
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
        onConversationReady: (com.aichat.workbench.domain.model.Conversation) -> Unit,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        try {
            val conversation = conversationManager.selectedOrNewConversation(current, userText ?: "重试")
            onConversationReady(conversation)
            val provider = conversationManager.providerFor(current, conversation, retryFailedMessage)
            val apiKey = providerRepository.getApiKey(provider.id)
            val descriptor = providerRegistry.descriptor(provider.type)
            require(providerRegistry.isRegistered(provider.type)) {
                "当前 Provider 暂未接入聊天发送：${provider.type.value}。"
            }
            require(!descriptor.requiresApiKey || !apiKey.isNullOrBlank()) { "API Key 缺失。" }

            val model = conversationManager.modelFor(current, provider, conversation, retryFailedMessage)
            requireImageSupported(provider, model, current.imageDrafts, descriptor.capabilities.vision)
            val tools = provider.chatToolsFor(
                model = model,
                providerSupportsTools = descriptor.capabilities.toolCalling,
                hasImageInput = current.imageDrafts.isNotEmpty(),
            )
            val chatProvider = providerClient(provider)
            val userContentParts = userText?.let { text ->
                buildList {
                    text.takeIf { it.isNotBlank() }?.let { add(MessagePart.Text(it)) }
                    addAll(current.imageDrafts)
                }
            }
            val userMessage = userText?.let {
                conversationManager.createMessage(
                    conversation = conversation,
                    role = MessageRole.User,
                    content = it,
                    status = MessageStatus.Completed,
                    provider = provider,
                    model = model,
                    parentMessageId = editedMessage?.id,
                    contentParts = userContentParts.orEmpty(),
                )
            }

            onStateChanged {
                it.copy(
                    draft = it.draft.copy(input = "", editingMessageId = null),
                    imageDrafts = emptyList(),
                    isGenerating = true,
                    error = null,
                )
            }
            if (userMessage != null) {
                conversationRepository.saveMessage(userMessage)
                conversationManager.maybeRenameConversation(conversation, userMessage.content)
            }

            var history = when {
                editedMessage != null && userMessage != null -> editHistory(current.messages, editedMessage, userMessage)
                retryFailedMessage != null -> retryHistory(current.messages, retryFailedMessage)
                else -> conversationRepository.getMessages(conversation.id)
            }.compactForProvider(conversation, provider, apiKey, model, chatProvider, onStateChanged)
            var systemPrompt = history.systemPrompt
            var providerHistory = history.history
            var parentMessageId = retryFailedMessage?.id ?: userMessage?.id
            var depth = 0

            while (true) {
                val assistant = runModelTurn(
                    conversation = conversation,
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    history = providerHistory,
                    parentMessageId = parentMessageId,
                    tools = tools,
                    chatProvider = chatProvider,
                    onStateChanged = onStateChanged,
                )
                if (assistant.status == MessageStatus.Failed) {
                    onStateChanged { it.copy(error = assistant.errorSummary) }
                    return
                }
                val toolCalls = assistant.toolCalls
                if (toolCalls.isEmpty()) return
                if (depth >= MAX_TOOL_DEPTH) {
                    onStateChanged { it.copy(error = "工具调用超过最大递归深度。") }
                    return
                }
                for (toolCall in toolCalls) {
                    executeToolCall(
                        conversation = conversation,
                        provider = provider,
                        model = model,
                        assistant = assistant,
                        toolCall = toolCall,
                        tools = tools,
                        onStateChanged = onStateChanged,
                    )
                }
                history = conversationRepository.getMessages(conversation.id)
                    .compactForProvider(conversation, provider, apiKey, model, chatProvider, onStateChanged)
                systemPrompt = history.systemPrompt
                providerHistory = history.history
                parentMessageId = assistant.id
                depth += 1
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onStateChanged { it.copy(error = error.message ?: "发送失败。") }
        } finally {
            activeAssistantMessage = null
            pendingToolApproval = null
            onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
        }
    }

    private suspend fun runModelTurn(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        systemPrompt: String?,
        history: List<ProviderChatMessage>,
        parentMessageId: com.aichat.workbench.domain.model.MessageId?,
        tools: List<ToolDescriptor>,
        chatProvider: ChatProvider,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ): Message {
        val assistant = conversationManager.createMessage(
            conversation = conversation,
            role = MessageRole.Assistant,
            content = "",
            status = MessageStatus.Pending,
            provider = provider,
            model = model,
            parentMessageId = parentMessageId,
        )
        activeAssistantMessage = assistant
        val request = ChatProviderRequest(
            provider = provider,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messages = history,
            parameters = conversation.modelParameters,
            tools = tools,
        )
        SendMessageUseCase(conversationRepository, chatProvider, clock)(assistant, request)
            .collect { message ->
                activeAssistantMessage = message
                onStateChanged { it.copy(messages = it.messages.upsert(message)) }
            }
        return requireNotNull(activeAssistantMessage) { "Assistant 消息缺失。" }
    }

    private suspend fun executeToolCall(
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
        assistant: Message,
        toolCall: ToolCall,
        tools: List<ToolDescriptor>,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        val descriptor = tools.firstOrNull { it.name == toolCall.name.canonicalToolName() }
        val approved = if (descriptor?.permissionLevel?.requiresConfirmation() == true) {
            awaitToolApproval(toolCall, descriptor, onStateChanged)
        } else {
            true
        }
        val execution = if (approved) {
            toolExecutor.execute(conversation.id, toolCall, descriptor)
        } else {
            toolExecutor.deny(conversation.id, toolCall, descriptor)
        }
        val status = if (execution.result.status == com.aichat.workbench.domain.model.ToolStatus.Completed) {
            MessageStatus.Completed
        } else {
            MessageStatus.Failed
        }
        val toolMessage = conversationManager.createMessage(
            conversation = conversation,
            role = MessageRole.Tool,
            content = execution.messageContent,
            status = status,
            provider = provider,
            model = model,
            parentMessageId = assistant.id,
            toolCallId = toolCall.id,
            toolResult = execution.messageContent,
            errorSummary = execution.result.error?.message,
            contentParts = execution.contentParts.ifEmpty { listOf(MessagePart.Text(execution.messageContent)) },
        )
        conversationRepository.saveMessage(toolMessage)
    }

    private suspend fun awaitToolApproval(
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ): Boolean {
        val approval = CompletableDeferred<Boolean>()
        pendingToolApproval = approval
        onStateChanged {
            it.copy(
                pendingToolCall = PendingToolCall(
                    toolCall = toolCall,
                    displayName = descriptor.displayName,
                    permissionLevel = descriptor.permissionLevel,
                ),
            )
        }
        return try {
            approval.await()
        } finally {
            pendingToolApproval = null
            onStateChanged { it.copy(pendingToolCall = null) }
        }
    }

    private fun providerClient(provider: ProviderConfig): ChatProvider =
        providerRegistry.get(provider.type.value)

    private fun requireImageSupported(
        provider: ProviderConfig,
        model: String,
        imageDrafts: List<MessagePart.Image>,
        providerSupportsVision: Boolean,
    ) {
        if (imageDrafts.isEmpty()) return
        val capability = provider.models.firstOrNull { it.id == model }?.capability
        val supportsVision = capability?.vision ?: providerSupportsVision
        require(supportsVision) { "当前模型不支持图片输入，请切换到视觉模型。" }
    }

    private suspend fun ProviderConfig.chatToolsFor(
        model: String,
        providerSupportsTools: Boolean,
        hasImageInput: Boolean,
    ): List<ToolDescriptor> {
        if (!supportsToolCalling(model, providerSupportsTools)) return emptyList()
        if (type == ProviderType.OpenAI && !hasImageInput) return officialHostedTools()
        val executableTools = toolExecutor.availableTools()
        if (hasImageInput || executableTools.any { it.name == "web_search" }) {
            return executableTools
        }
        return if (type.supportsChatCompletionsHostedWebSearch()) {
            executableTools + officialHostedWebSearchTool()
        } else {
            executableTools
        }
    }

    private fun officialHostedTools(): List<ToolDescriptor> =
        listOf(
            officialHostedWebSearchTool(),
            ToolDescriptor(
                name = "code_interpreter",
                displayName = "Code Interpreter",
                description = "Run small code tasks using the provider's official hosted code interpreter.",
                permissionLevel = ToolPermissionLevel.ReadOnly,
                inputSchemaJson = "{}",
                outputSchemaJson = null,
                timeoutSeconds = null,
                source = ToolSource.Official,
            ),
            ToolDescriptor(
                name = "image_generation",
                displayName = "Image Generation",
                description = "Generate images using the provider's official hosted image generation tool.",
                permissionLevel = ToolPermissionLevel.ReadOnly,
                inputSchemaJson = "{}",
                outputSchemaJson = null,
                timeoutSeconds = null,
                source = ToolSource.Official,
            ),
        )

    private fun officialHostedWebSearchTool(): ToolDescriptor =
        ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Search the web using the provider's official hosted search tool.",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Official,
        )

    private fun ProviderConfig.supportsToolCalling(model: String, providerSupportsTools: Boolean): Boolean {
        val capability = models.firstOrNull { it.id == model }?.capability
        return capability?.toolCalling ?: providerSupportsTools
    }

    private fun ProviderType.supportsChatCompletionsHostedWebSearch(): Boolean =
        this == ProviderType.NewApi ||
            this == ProviderType.Sub2Api

    private fun editHistory(
        messages: List<Message>,
        editedMessage: Message,
        newMessage: Message,
    ): List<Message> =
        messages.takeWhile { it.id != editedMessage.id } + newMessage

    private fun retryHistory(
        messages: List<Message>,
        failedMessage: Message,
    ): List<Message> =
        messages.takeWhile { it.id != failedMessage.id }

    private suspend fun List<Message>.compactForProvider(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        chatProvider: ChatProvider,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ): ConversationContext =
        try {
            conversationCompactor.compactIfNeeded(
                conversation = conversation,
                provider = provider,
                apiKey = apiKey,
                model = model,
                messages = this,
                chatProvider = chatProvider,
            ).also { context ->
                context.summaryMessage?.let { summary ->
                    onStateChanged { it.copy(messages = it.messages.upsertSorted(summary)) }
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw IllegalStateException(
                "长对话压缩失败：${error.message ?: "摘要生成失败。"}",
                error,
            )
        }

    private fun List<Message>.upsert(message: Message): List<Message> =
        if (any { it.id == message.id }) {
            map { if (it.id == message.id) message else it }
        } else {
            this + message
        }

    private fun List<Message>.upsertSorted(message: Message): List<Message> =
        upsert(message).sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id.value })

    private companion object {
        const val MAX_TOOL_DEPTH = 5
    }
}
