package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.tool.ToolExecutor
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.provider.rolePreferenceModel
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GenerationController(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val conversationManager: ConversationManager,
    private val conversationCompactor: ConversationCompactor,
    private val providerRegistry: ProviderRegistry,
    private val toolExecutor: ToolExecutor,
    private val clock: Clock,
) {
    // Mutex to protect concurrent access to generation state
    private val stateMutex = Mutex()
    private var generationJob: Job? = null
    private var activeAssistantMessage: Message? = null
    private var pendingToolApproval: CompletableDeferred<ToolApprovalDecision>? = null
    private var activePendingToolCall: ToolCall? = null

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
        scope.launch {
            stateMutex.withLock {
                // Check if tool approval is pending and try to complete it
                pendingToolApproval?.let { approval ->
                    if (approval.complete(ToolApprovalDecision.Cancelled)) {
                        onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
                        return@launch
                    }
                }

                // Capture active message before cancellation
                val active = activeAssistantMessage
                val job = generationJob

                // Cancel the job
                job?.cancel()
                generationJob = null

                // Save message state even if cancellation occurs
                if (active != null) {
                    withContext(NonCancellable) {
                        conversationRepository.saveMessage(
                            active.copy(
                                status = MessageStatus.Cancelled,
                                errorSummary = "已停止，已保留当前回复内容。",
                                updatedAt = clock.instant(),
                            ),
                        )
                    }
                }

                // Clean up state
                activeAssistantMessage = null
                pendingToolApproval?.complete(ToolApprovalDecision.Denied)
                pendingToolApproval = null
                onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
            }
        }
    }

    fun confirmToolCall() {
        val toolCall = activePendingToolCall ?: return
        val completed = pendingToolApproval?.complete(ToolApprovalDecision.Approved(toolCall)) ?: false
        if (!completed) {
            android.util.Log.w("GenerationController", "confirmToolCall ignored: approval already resolved.")
        }
    }

    fun updatePendingToolArguments(arguments: String) {
        activePendingToolCall = activePendingToolCall?.copy(arguments = arguments)
    }

    fun denyToolCall() {
        val completed = pendingToolApproval?.complete(ToolApprovalDecision.Denied) ?: false
        if (!completed) {
            android.util.Log.w("GenerationController", "denyToolCall ignored: approval already resolved.")
        }
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

            val chatModel = conversationManager.modelFor(current, provider, conversation, retryFailedMessage)
            val model = provider.codeTaskModel(
                current = current,
                chatModel = chatModel,
                taskText = routingTaskText(current, userText, retryFailedMessage),
                providerSupportsText = descriptor.capabilities.text,
                providerSupportsVision = descriptor.capabilities.vision,
                hasImageInput = current.imageDrafts.isNotEmpty(),
            )
            requireImageSupported(provider, model, current.imageDrafts, descriptor.capabilities.vision)
            val toolModel = provider.toolPlanningModel(
                current = current,
                chatModel = model,
                providerSupportsTools = descriptor.capabilities.toolCalling,
            )
            val tools = provider.chatToolsFor(
                model = toolModel,
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
                    model = toolModel,
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
                        model = toolModel,
                        assistant = assistant,
                        toolCall = toolCall,
                        tools = tools,
                        onStateChanged = onStateChanged,
                    )
                }
                history = conversationRepository.getMessages(conversation.id)
                    .compactForProvider(conversation, provider, apiKey, toolModel, chatProvider, onStateChanged)
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
        val decision = if (descriptor != null && toolExecutor.requiresConfirmation(descriptor)) {
            awaitToolApproval(toolCall, descriptor, onStateChanged)
        } else {
            ToolApprovalDecision.Approved(toolCall)
        }
        val execution = try {
            when (decision) {
                is ToolApprovalDecision.Approved -> {
                    if (decision.toolCall != toolCall) {
                        conversationRepository.saveMessage(
                            assistant.copy(
                                toolCalls = assistant.toolCalls.map {
                                    if (it.id == decision.toolCall.id) decision.toolCall else it
                                },
                                updatedAt = clock.instant(),
                            ),
                        )
                    }
                    toolExecutor.execute(conversation.id, decision.toolCall, descriptor)
                }
                ToolApprovalDecision.Denied -> toolExecutor.deny(conversation.id, toolCall, descriptor)
                ToolApprovalDecision.Cancelled -> toolExecutor.cancel(conversation.id, toolCall, descriptor)
            }
        } catch (error: ToolExecutionCancelledException) {
            withContext(NonCancellable) {
                saveToolExecutionMessage(
                    conversation = conversation,
                    provider = provider,
                    model = model,
                    assistant = assistant,
                    toolCall = toolCall,
                    toolCallId = (decision as? ToolApprovalDecision.Approved)?.toolCall?.id ?: toolCall.id,
                    execution = error.execution,
                )
            }
            throw error
        }
        saveToolExecutionMessage(
            conversation = conversation,
            provider = provider,
            model = model,
            assistant = assistant,
            toolCall = toolCall,
            toolCallId = (decision as? ToolApprovalDecision.Approved)?.toolCall?.id ?: toolCall.id,
            execution = execution,
        )
        if (decision == ToolApprovalDecision.Cancelled) {
            throw CancellationException("工具执行已取消。")
        }
    }

    private suspend fun saveToolExecutionMessage(
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
        assistant: Message,
        toolCall: ToolCall,
        toolCallId: com.aichat.workbench.domain.model.ToolCallId,
        execution: ToolExecution,
    ) {
        val status = execution.result.status.toMessageStatus()
        val toolMessage = conversationManager.createMessage(
            conversation = conversation,
            role = MessageRole.Tool,
            content = execution.messageContent,
            status = status,
            provider = provider,
            model = model,
            parentMessageId = assistant.id,
            toolCallId = toolCallId,
            toolResult = execution.messageContent,
            errorSummary = execution.result.toolMessageErrorSummary(
                toolName = toolCall.name,
                toolResult = execution.messageContent,
            ),
            contentParts = execution.contentParts.ifEmpty { listOf(MessagePart.Text(execution.messageContent)) },
        )
        conversationRepository.saveMessage(toolMessage)
    }

    private suspend fun awaitToolApproval(
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ): ToolApprovalDecision {
        val approval = CompletableDeferred<ToolApprovalDecision>()
        pendingToolApproval = approval
        activePendingToolCall = toolCall
        onStateChanged {
            it.copy(
                pendingToolCall = PendingToolCall(
                    toolCall = activePendingToolCall ?: toolCall,
                    displayName = descriptor.displayName,
                    permissionLevel = descriptor.permissionLevel,
                ),
            )
        }
        return try {
            approval.await()
        } finally {
            pendingToolApproval = null
            activePendingToolCall = null
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

    private fun ProviderConfig.codeTaskModel(
        current: ChatUiState,
        chatModel: String,
        taskText: String,
        providerSupportsText: Boolean,
        providerSupportsVision: Boolean,
        hasImageInput: Boolean,
    ): String {
        if (!taskText.isLikelyCodeTask()) return chatModel
        val roleModel = rolePreferenceModel(current.modelRolePreferences, ModelRole.Code)
            ?: return chatModel
        return roleModel.takeIf {
            supportsTextGeneration(it, providerSupportsText) &&
                (!hasImageInput || supportsVisionInput(it, providerSupportsVision))
        } ?: chatModel
    }

    private fun ProviderConfig.supportsTextGeneration(model: String, providerSupportsText: Boolean): Boolean {
        val capability = models.firstOrNull { it.id == model }?.capability
        return capability?.text ?: providerSupportsText
    }

    private fun ProviderConfig.supportsVisionInput(model: String, providerSupportsVision: Boolean): Boolean {
        val capability = models.firstOrNull { it.id == model }?.capability
        return capability?.vision ?: providerSupportsVision
    }

    private fun ProviderConfig.toolPlanningModel(
        current: ChatUiState,
        chatModel: String,
        providerSupportsTools: Boolean,
    ): String {
        val roleModel = rolePreferenceModel(current.modelRolePreferences, ModelRole.Tool)
            ?: return chatModel
        return roleModel.takeIf { supportsToolCalling(it, providerSupportsTools) } ?: chatModel
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

    private fun routingTaskText(
        current: ChatUiState,
        userText: String?,
        retryFailedMessage: Message?,
    ): String =
        userText?.takeIf { it.isNotBlank() }
            ?: retryFailedMessage?.parentMessageId
                ?.let { parentId -> current.messages.lastOrNull { it.id == parentId }?.content }
                ?.takeIf { it.isNotBlank() }
            ?: retryFailedMessage?.content.orEmpty()

    private fun String.isLikelyCodeTask(): Boolean {
        val normalized = lowercase()
        return CODE_TASK_PHRASES.any { marker -> normalized.contains(marker) } ||
            CODE_TASK_WORD_PATTERN.containsMatchIn(normalized) ||
            CODE_BLOCK_PATTERN.containsMatchIn(this)
    }

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

    private sealed interface ToolApprovalDecision {
        data class Approved(val toolCall: ToolCall) : ToolApprovalDecision
        data object Denied : ToolApprovalDecision
        data object Cancelled : ToolApprovalDecision
    }

    private companion object {
        const val MAX_TOOL_DEPTH = 5
        val CODE_TASK_PHRASES = listOf(
            "代码",
            "函数",
            "报错",
            "异常",
            "堆栈",
            "编译",
            "构建失败",
            "测试失败",
            "重构",
            "解释代码",
            "生成代码",
            "写一段代码",
            "写段代码",
            "实现一个",
            "修复 bug",
            "补丁",
            "正则",
            "go 语言",
            "go语言",
            "jetpack compose",
            "react component",
            "react 组件",
            "diff",
            "patch",
            "code",
            "function",
            "error",
            "exception",
            "stack trace",
            "compile",
            "build failed",
            "refactor",
            "debug",
            "unit test",
        )
        val CODE_TASK_WORD_PATTERN = Regex(
            """\b(kotlin|java|golang|rust|python|typescript|javascript|sql|json|yaml|gradle|android|vue|regex)\b""",
        )
        val CODE_BLOCK_PATTERN = Regex("""```|\b(class|fun|func|def|fn|const|let|var|package|import|interface|struct|enum)\b""")
    }
}

private fun ToolStatus.toMessageStatus(): MessageStatus =
    when (this) {
        ToolStatus.Completed -> MessageStatus.Completed
        ToolStatus.Denied,
        ToolStatus.Cancelled,
        -> MessageStatus.Cancelled
        else -> MessageStatus.Failed
    }

private fun ToolResult.toolMessageErrorSummary(
    toolName: String,
    toolResult: String,
): String? {
    val error = error ?: return null
    val structuredError = extractToolErrorResult(toolResult) ?: return error.message
    return buildString {
        append(structuredError.message)
        structuredError.statusCode?.let { append("\nHTTP：$it") }
        structuredError.retryable?.let { append("\n可重试：${if (it) "是" else "否"}") }
        append("\n建议：${structuredError.recoveryHint(toolName)}")
    }
}
