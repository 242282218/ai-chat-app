package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.preferredChatModel
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class ChatTurnRequest(
    val selectedConversationId: ConversationId?,
    val providers: List<ProviderConfig>,
    val modelRolePreferences: List<ModelRolePreference>,
    val selectedProviderId: String?,
    val messages: List<Message>,
    val imageDrafts: List<MessagePart.Image>,
    val userText: String?,
    val editedMessage: Message?,
    val retryFailedMessage: Message?,
)

interface ChatTurnCallbacks {
    suspend fun onConversationReady(conversation: Conversation)

    suspend fun onInputAccepted()

    suspend fun onActiveAssistantMessageChanged(message: Message?)

    suspend fun onMessageChanged(message: Message)

    suspend fun onSummaryMessageCreated(message: Message)

    suspend fun onError(message: String?)
}

class ChatTurnOrchestrator(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val contextProvider: ConversationContextProvider,
    private val providerRegistry: ProviderRegistry,
    private val clock: Clock,
) {
    suspend fun run(
        request: ChatTurnRequest,
        callbacks: ChatTurnCallbacks,
    ) {
        val existingConversation = request.selectedConversationId?.let {
            conversationRepository.getConversation(it)
        }
        val provider = providerFor(request, existingConversation)
        val apiKey = providerRepository.getApiKey(provider.id)
        val descriptor = providerRegistry.descriptor(provider.type)
        require(providerRegistry.isRegistered(provider.type)) {
            "当前 Provider 暂未接入聊天发送：${provider.type.value}。"
        }
        require(!descriptor.requiresApiKey || !apiKey.isNullOrBlank()) { "API Key 缺失。" }

        val chatModel = modelFor(request, provider)
        val model = chatModel
        requireImageSupported(provider, model, request.imageDrafts, descriptor.capabilities.vision)
        val chatProvider = providerRegistry.get(provider.type.value)
        val conversation = existingConversation ?: createConversation(request)
        callbacks.onConversationReady(conversation)
        val userMessage = createUserMessage(request, conversation, provider, model)

        callbacks.onInputAccepted()
        if (userMessage != null) {
            conversationRepository.saveMessage(userMessage)
            maybeRenameConversation(conversation, userMessage.content)
        }

        val context = initialMessages(request, conversation, userMessage)
            .buildContext(conversation, provider, apiKey, model, chatProvider, callbacks)
        val assistant = runModelTurn(
            conversation = conversation,
            provider = provider,
            apiKey = apiKey,
            model = model,
            systemPrompt = context.systemPrompt,
            history = context.history,
            parentMessageId = request.retryFailedMessage?.id ?: userMessage?.id,
            chatProvider = chatProvider,
            callbacks = callbacks,
        )
        if (assistant.status == MessageStatus.Failed) {
            callbacks.onError(assistant.errorSummary)
        }
    }

    private suspend fun createConversation(request: ChatTurnRequest): Conversation {
        return CreateConversationUseCase(conversationRepository, clock)(
            title = conversationTitlePreview(request.userText ?: "重试"),
            defaultProviderId = request.selectedProviderId?.let(::ProviderId),
        )
    }

    private suspend fun maybeRenameConversation(conversation: Conversation, firstMessage: String) {
        if (conversation.title != "新对话") return
        conversationRepository.renameConversation(conversation.id, conversationTitlePreview(firstMessage))
    }

    private fun providerFor(
        request: ChatTurnRequest,
        conversation: Conversation?,
    ): ProviderConfig {
        val retryProvider = request.retryFailedMessage?.providerId
            ?.let { providerId -> request.providers.firstOrNull { it.id == providerId && it.enabled } }
        val conversationProvider = conversation?.defaultProviderId
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
        request: ChatTurnRequest,
        provider: ProviderConfig,
    ): String =
        request.retryFailedMessage?.model?.takeIf { request.retryFailedMessage.providerId == provider.id }
            ?: provider.preferredChatModel(request.modelRolePreferences).takeIf { it.isNotBlank() }
            ?: error("默认 Model 缺失。")

    private fun createUserMessage(
        request: ChatTurnRequest,
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
        request: ChatTurnRequest,
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
        callbacks: ChatTurnCallbacks,
    ): ConversationContext =
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
        chatProvider: ChatProvider,
        callbacks: ChatTurnCallbacks,
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
            parameters = ModelParameters(),
        )
        SendMessageUseCase(conversationRepository, chatProvider, clock)(assistant, providerRequest)
            .collect { message ->
                current = message
                callbacks.onActiveAssistantMessageChanged(message)
                callbacks.onMessageChanged(message)
            }
        return current
    }

    private fun createMessage(
        conversation: Conversation,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        provider: ProviderConfig,
        model: String,
        parentMessageId: MessageId?,
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
            parentMessageId = parentMessageId,
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

}
