package com.aichat.workbench.agent.runtime

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.tool.ToolExecution
import com.aichat.workbench.domain.tool.ToolExecutionCancelledException
import com.aichat.workbench.domain.tool.ToolExecutionService
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.canonicalToolName
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class TaskOrchestrationRequest(
    val selectedConversationId: ConversationId?,
    val providers: List<ProviderConfig>,
    val selectedProviderId: String?,
    val modelDraft: String,
    val modelParameters: ModelParameters,
    val systemPromptDraft: String,
    val temporaryDraft: Boolean,
    val sensitiveDraft: Boolean,
    val modelRolePreferences: List<ModelRolePreference>,
    val messages: List<Message>,
    val imageDrafts: List<MessagePart.Image>,
    val userText: String?,
    val editedMessage: Message?,
    val retryFailedMessage: Message?,
)

interface TaskOrchestrationCallbacks {
    suspend fun onConversationReady(conversation: Conversation)

    suspend fun onInputAccepted()

    suspend fun onActiveAssistantMessageChanged(message: Message?)

    suspend fun onMessageChanged(message: Message)

    suspend fun onSummaryMessageCreated(message: Message)

    suspend fun requestToolApproval(
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
    ): TaskToolApprovalDecision

    suspend fun onError(message: String?)
}

sealed interface TaskToolApprovalDecision {
    data class Approved(val toolCall: ToolCall) : TaskToolApprovalDecision
    data object Denied : TaskToolApprovalDecision
    data object Cancelled : TaskToolApprovalDecision
}

class TaskOrchestrator(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val contextProvider: TaskContextProvider,
    private val providerRegistry: ProviderRegistry,
    private val toolExecutor: ToolExecutionService,
    private val clock: Clock,
) {
    suspend fun run(
        request: TaskOrchestrationRequest,
        callbacks: TaskOrchestrationCallbacks,
    ) {
        val conversation = selectedOrNewConversation(request)
        callbacks.onConversationReady(conversation)
        val provider = providerFor(request, conversation)
        val apiKey = providerRepository.getApiKey(provider.id)
        val descriptor = providerRegistry.descriptor(provider.type)
        require(providerRegistry.isRegistered(provider.type)) {
            "当前 Provider 暂未接入聊天发送：${provider.type.value}。"
        }
        require(!descriptor.requiresApiKey || !apiKey.isNullOrBlank()) { "API Key 缺失。" }

        val chatModel = modelFor(request, provider, conversation)
        val model = provider.codeTaskModel(
            rolePreferences = request.modelRolePreferences,
            chatModel = chatModel,
            taskText = routingTaskText(request),
            providerSupportsText = descriptor.capabilities.text,
            providerSupportsVision = descriptor.capabilities.vision,
            hasImageInput = request.imageDrafts.isNotEmpty(),
        )
        requireImageSupported(provider, model, request.imageDrafts, descriptor.capabilities.vision)
        val toolModel = provider.toolPlanningModel(
            rolePreferences = request.modelRolePreferences,
            chatModel = model,
            providerSupportsTools = descriptor.capabilities.toolCalling,
        )
        val tools = provider.chatToolsFor(
            model = toolModel,
            providerSupportsTools = descriptor.capabilities.toolCalling,
            hasImageInput = request.imageDrafts.isNotEmpty(),
        )
        val chatProvider = providerRegistry.get(provider.type.value)
        val userMessage = createUserMessage(request, conversation, provider, model)

        callbacks.onInputAccepted()
        if (userMessage != null) {
            conversationRepository.saveMessage(userMessage)
            maybeRenameConversation(conversation, userMessage.content)
        }

        var context = initialMessages(request, conversation, userMessage)
            .buildContext(conversation, provider, apiKey, model, chatProvider, callbacks)
        var systemPrompt = context.systemPrompt
        var providerHistory = context.history
        var parentMessageId = request.retryFailedMessage?.id ?: userMessage?.id
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
                callbacks = callbacks,
            )
            if (assistant.status == MessageStatus.Failed) {
                callbacks.onError(assistant.errorSummary)
                return
            }
            val toolCalls = assistant.toolCalls
            if (toolCalls.isEmpty()) return
            if (depth >= MAX_TOOL_DEPTH) {
                callbacks.onError("工具调用超过最大递归深度。")
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
                    callbacks = callbacks,
                )
            }
            context = conversationRepository.getMessages(conversation.id)
                .buildContext(conversation, provider, apiKey, toolModel, chatProvider, callbacks)
            systemPrompt = context.systemPrompt
            providerHistory = context.history
            parentMessageId = assistant.id
            depth += 1
        }
    }

    private suspend fun selectedOrNewConversation(request: TaskOrchestrationRequest): Conversation {
        val existing = request.selectedConversationId?.let { conversationRepository.getConversation(it) }
        if (existing != null) return existing

        return CreateConversationUseCase(conversationRepository, clock)(
            title = conversationTitlePreview(request.userText ?: "重试"),
            defaultProviderId = request.selectedProviderId?.let(::ProviderId),
            defaultModel = request.modelDraft.trim().ifBlank { null },
            modelParameters = request.modelParameters,
            systemPrompt = request.systemPromptDraft.trim().ifBlank { null },
            isTemporary = request.temporaryDraft,
            isSensitive = request.sensitiveDraft,
        )
    }

    private suspend fun maybeRenameConversation(conversation: Conversation, firstMessage: String) {
        if (conversation.title != "新对话" && conversation.title != "临时对话") return
        conversationRepository.renameConversation(conversation.id, conversationTitlePreview(firstMessage))
    }

    private fun providerFor(
        request: TaskOrchestrationRequest,
        conversation: Conversation,
    ): ProviderConfig {
        val retryProvider = request.retryFailedMessage?.providerId
            ?.let { providerId -> request.providers.firstOrNull { it.id == providerId && it.enabled } }
        val conversationProvider = conversation.defaultProviderId
            ?.let { providerId -> request.providers.firstOrNull { it.id == providerId && it.enabled } }
        val selectedProvider = request.selectedProviderId
            ?.let { id -> request.providers.firstOrNull { it.id.value == id && it.enabled } }

        return retryProvider
            ?: selectedProvider
            ?: conversationProvider
            ?: request.providers.firstOrNull { it.enabled }
            ?: error("模型连接未配置。")
    }

    private fun modelFor(
        request: TaskOrchestrationRequest,
        provider: ProviderConfig,
        conversation: Conversation,
    ): String =
        request.retryFailedMessage?.model?.takeIf { request.retryFailedMessage.providerId == provider.id }
            ?: request.modelDraft.trim().ifBlank { null }
            ?: conversation.defaultModel?.takeIf { conversation.defaultProviderId == provider.id }
            ?: provider.defaultModel
            ?: provider.models.firstOrNull()?.id
            ?: error("默认 Model 缺失。")

    private fun createUserMessage(
        request: TaskOrchestrationRequest,
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
    ): Message? {
        val text = request.userText ?: return null
        val contentParts = buildList {
            text.takeIf { it.isNotBlank() }?.let { add(MessagePart.Text(it)) }
            addAll(request.imageDrafts)
        }
        return createMessage(
            conversation = conversation,
            role = MessageRole.User,
            content = text,
            status = MessageStatus.Completed,
            provider = provider,
            model = model,
            parentMessageId = request.editedMessage?.id,
            contentParts = contentParts,
        )
    }

    private suspend fun initialMessages(
        request: TaskOrchestrationRequest,
        conversation: Conversation,
        userMessage: Message?,
    ): List<Message> {
        return when {
            request.editedMessage != null && userMessage != null ->
                request.messages.takeWhile { it.id != request.editedMessage.id } + userMessage
            request.retryFailedMessage != null ->
                request.messages.takeWhile { it.id != request.retryFailedMessage.id }
            else -> conversationRepository.getMessages(conversation.id)
        }
    }

    private suspend fun List<Message>.buildContext(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        chatProvider: ChatProvider,
        callbacks: TaskOrchestrationCallbacks,
    ): TaskConversationContext =
        try {
            contextProvider.build(
                conversation = conversation,
                provider = provider,
                apiKey = apiKey,
                model = model,
                messages = this,
                chatProvider = chatProvider,
            ).also { context ->
                context.summaryMessage?.let { summary -> callbacks.onSummaryMessageCreated(summary) }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw IllegalStateException(
                "长对话压缩失败：${error.message ?: "摘要生成失败。"}",
                error,
            )
        }

    private suspend fun runModelTurn(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        systemPrompt: String?,
        history: List<ProviderChatMessage>,
        parentMessageId: MessageId?,
        tools: List<ToolDescriptor>,
        chatProvider: ChatProvider,
        callbacks: TaskOrchestrationCallbacks,
    ): Message {
        val assistant = createMessage(
            conversation = conversation,
            role = MessageRole.Assistant,
            content = "",
            status = MessageStatus.Pending,
            provider = provider,
            model = model,
            parentMessageId = parentMessageId,
        )
        callbacks.onActiveAssistantMessageChanged(assistant)
        var current = assistant
        val providerRequest = ChatProviderRequest(
            provider = provider,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messages = history,
            parameters = conversation.modelParameters,
            tools = tools,
        )
        SendMessageUseCase(conversationRepository, chatProvider, clock)(assistant, providerRequest)
            .collect { message ->
                current = message
                callbacks.onActiveAssistantMessageChanged(message)
                callbacks.onMessageChanged(message)
            }
        return current
    }

    private suspend fun executeToolCall(
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
        assistant: Message,
        toolCall: ToolCall,
        tools: List<ToolDescriptor>,
        callbacks: TaskOrchestrationCallbacks,
    ) {
        val descriptor = tools.firstOrNull { it.name == toolCall.name.canonicalToolName() }
        val decision = if (descriptor != null && toolExecutor.requiresConfirmation(descriptor)) {
            callbacks.requestToolApproval(toolCall, descriptor)
        } else {
            TaskToolApprovalDecision.Approved(toolCall)
        }
        val execution = try {
            when (decision) {
                is TaskToolApprovalDecision.Approved -> {
                    if (decision.toolCall != toolCall) {
                        val updatedAssistant = assistant.copy(
                            toolCalls = assistant.toolCalls.map {
                                if (it.id == decision.toolCall.id) decision.toolCall else it
                            },
                            updatedAt = clock.instant(),
                        )
                        conversationRepository.saveMessage(updatedAssistant)
                        callbacks.onMessageChanged(updatedAssistant)
                    }
                    toolExecutor.execute(conversation.id, decision.toolCall, descriptor)
                }
                TaskToolApprovalDecision.Denied -> toolExecutor.deny(conversation.id, toolCall, descriptor)
                TaskToolApprovalDecision.Cancelled -> toolExecutor.cancel(conversation.id, toolCall, descriptor)
            }
        } catch (error: ToolExecutionCancelledException) {
            withContext(NonCancellable) {
                saveToolExecutionMessage(
                    conversation = conversation,
                    provider = provider,
                    model = model,
                    assistant = assistant,
                    toolCall = toolCall,
                    toolCallId = (decision as? TaskToolApprovalDecision.Approved)?.toolCall?.id ?: toolCall.id,
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
            toolCallId = (decision as? TaskToolApprovalDecision.Approved)?.toolCall?.id ?: toolCall.id,
            execution = execution,
        )
        if (decision == TaskToolApprovalDecision.Cancelled) {
            throw CancellationException("工具执行已取消。")
        }
    }

    private suspend fun saveToolExecutionMessage(
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
        assistant: Message,
        toolCall: ToolCall,
        toolCallId: ToolCallId,
        execution: ToolExecution,
    ) {
        val toolMessage = createMessage(
            conversation = conversation,
            role = MessageRole.Tool,
            content = execution.messageContent,
            status = execution.result.status.toMessageStatus(),
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

    private fun createMessage(
        conversation: Conversation,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        provider: ProviderConfig,
        model: String,
        parentMessageId: MessageId?,
        toolCallId: ToolCallId? = null,
        toolCalls: List<ToolCall> = emptyList(),
        toolResult: String? = null,
        errorSummary: String? = null,
        contentParts: List<MessagePart> = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
    ): Message =
        Message(
            id = MessageId(UUID.randomUUID().toString()),
            conversationId = conversation.id,
            role = role,
            content = content,
            contentParts = contentParts,
            providerId = provider.id,
            model = model,
            status = status,
            errorSummary = errorSummary,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = toolCallId,
            parentMessageId = parentMessageId,
            toolCalls = toolCalls,
            toolResult = toolResult,
        )

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
        rolePreferences: List<ModelRolePreference>,
        chatModel: String,
        taskText: String,
        providerSupportsText: Boolean,
        providerSupportsVision: Boolean,
        hasImageInput: Boolean,
    ): String {
        if (!taskText.isLikelyCodeTask()) return chatModel
        val roleModel = rolePreferenceModel(rolePreferences, ModelRole.Code)
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
        rolePreferences: List<ModelRolePreference>,
        chatModel: String,
        providerSupportsTools: Boolean,
    ): String {
        val roleModel = rolePreferenceModel(rolePreferences, ModelRole.Tool)
            ?: return chatModel
        return roleModel.takeIf { supportsToolCalling(it, providerSupportsTools) } ?: chatModel
    }

    private fun ProviderType.supportsChatCompletionsHostedWebSearch(): Boolean =
        this == ProviderType.NewApi ||
            this == ProviderType.Sub2Api

    private fun routingTaskText(request: TaskOrchestrationRequest): String =
        request.userText?.takeIf { it.isNotBlank() }
            ?: request.retryFailedMessage?.parentMessageId
                ?.let { parentId -> request.messages.lastOrNull { it.id == parentId }?.content }
                ?.takeIf { it.isNotBlank() }
            ?: request.retryFailedMessage?.content.orEmpty()

    private fun String.isLikelyCodeTask(): Boolean {
        val normalized = lowercase()
        return CODE_TASK_PHRASES.any { marker -> normalized.contains(marker) } ||
            CODE_TASK_WORD_PATTERN.containsMatchIn(normalized) ||
            CODE_BLOCK_PATTERN.containsMatchIn(this)
    }

    private fun ToolStatus.toMessageStatus(): MessageStatus =
        when (this) {
            ToolStatus.Completed -> MessageStatus.Completed
            ToolStatus.Denied,
            ToolStatus.Cancelled,
            -> MessageStatus.Cancelled
            else -> MessageStatus.Failed
        }

    private fun conversationTitlePreview(message: String): String {
        val normalized = message.replace(Regex("\\s+"), " ").trim()
        return when {
            normalized.isBlank() -> "新对话"
            normalized.length <= CONVERSATION_TITLE_MAX_LENGTH -> normalized
            else -> "${normalized.take(CONVERSATION_TITLE_MAX_LENGTH - 3)}..."
        }
    }

    private companion object {
        const val MAX_TOOL_DEPTH = 5
        const val CONVERSATION_TITLE_MAX_LENGTH = 40
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
