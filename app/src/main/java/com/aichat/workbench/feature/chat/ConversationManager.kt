package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.provider.preferredModel
import java.time.Clock
import java.util.UUID

class ConversationManager(
    private val conversationRepository: ConversationRepository,
    private val clock: Clock,
) {
    suspend fun selectedOrNewConversation(current: ChatUiState, firstMessage: String): Conversation {
        val existing = current.selectedConversationId?.let { conversationRepository.getConversation(it) }
        if (existing != null) return existing

        return CreateConversationUseCase(conversationRepository, clock)(
            title = conversationTitlePreview(firstMessage),
            defaultProviderId = current.selectedProviderId?.let(::ProviderId),
            defaultModel = current.modelDraft.trim().ifBlank { null },
            modelParameters = current.validatedModelParameters(),
            systemPrompt = current.systemPromptDraft.trim().ifBlank { null },
            isTemporary = current.temporaryDraft,
            isSensitive = current.sensitiveDraft,
        )
    }

    suspend fun maybeRenameConversation(conversation: Conversation, firstMessage: String) {
        if (conversation.title != "新对话" && conversation.title != "临时对话") return
        conversationRepository.renameConversation(conversation.id, conversationTitlePreview(firstMessage))
    }

    suspend fun createConversation(current: ChatUiState, title: String, temporary: Boolean): Conversation =
        CreateConversationUseCase(conversationRepository, clock)(
            title = title,
            defaultProviderId = current.selectedProviderId?.let(::ProviderId),
            defaultModel = current.modelDraft.trim().ifBlank { null },
            isTemporary = temporary,
            isSensitive = current.sensitiveDraft,
        )

    suspend fun saveSelectedSettings(current: ChatUiState): Conversation {
        val conversation = selectedConversation(current) ?: error("未选择对话。")
        val updated = conversation.copy(
            defaultProviderId = current.selectedProviderId?.let(::ProviderId),
            defaultModel = current.modelDraft.trim().ifBlank { null },
            modelParameters = current.validatedModelParameters(),
            systemPrompt = current.systemPromptDraft.trim().ifBlank { null },
            isTemporary = current.temporaryDraft,
            isSensitive = current.sensitiveDraft,
            updatedAt = clock.instant(),
        )
        conversationRepository.saveConversation(updated)
        return updated
    }

    suspend fun applyPromptPreset(current: ChatUiState, preset: PromptPreset): Conversation {
        val conversation = selectedConversation(current)
        if (conversation == null) {
            return CreateConversationUseCase(conversationRepository, clock)(
                title = preset.name,
                defaultProviderId = current.selectedProviderId?.let(::ProviderId),
                defaultModel = preset.defaultModel,
                systemPrompt = preset.systemPrompt,
            )
        }
        val updated = conversation.copy(
            systemPrompt = preset.systemPrompt,
            defaultModel = preset.defaultModel ?: conversation.defaultModel,
            updatedAt = clock.instant(),
        )
        conversationRepository.saveConversation(updated)
        return updated
    }

    fun providerFor(
        current: ChatUiState,
        conversation: Conversation,
        retryFailedMessage: Message?,
    ): ProviderConfig {
        val retryProvider = retryFailedMessage?.providerId
            ?.let { providerId -> current.providers.firstOrNull { it.id == providerId && it.enabled } }
        val conversationProvider = conversation.defaultProviderId
            ?.let { providerId -> current.providers.firstOrNull { it.id == providerId && it.enabled } }
        val selectedProvider = current.selectedProviderId
            ?.let { id -> current.providers.firstOrNull { it.id.value == id && it.enabled } }

        return retryProvider
            ?: conversationProvider
            ?: selectedProvider
            ?: current.providers.firstOrNull { it.enabled }
            ?: error("模型连接未配置。")
    }

    fun modelFor(
        current: ChatUiState,
        provider: ProviderConfig,
        conversation: Conversation,
        retryFailedMessage: Message?,
    ): String =
        retryFailedMessage?.model?.takeIf { retryFailedMessage.providerId == provider.id }
            ?: conversation.defaultModel?.takeIf { conversation.defaultProviderId == provider.id }
            ?: current.modelDraft.trim().ifBlank { null }
            ?: provider.defaultModel
            ?: provider.models.firstOrNull()?.id
            ?: error("默认 Model 缺失。")

    fun withSelectedConversation(
        state: ChatUiState,
        conversations: List<Conversation>,
        conversation: Conversation,
    ): ChatUiState {
        val selectionChanged = state.selectedConversationId != conversation.id
        val selectedProviderId = conversation.defaultProviderId
            ?.takeIf { providerId -> state.providers.any { it.id == providerId && it.enabled } }
            ?.value
            ?: state.selectedProviderId
        return state.copy(
            conversations = conversations,
            selectedConversationId = conversation.id,
            selectedProviderId = selectedProviderId,
            messages = if (selectionChanged) emptyList() else state.messages,
            selectedConversationMessageCount = if (selectionChanged) 0 else state.selectedConversationMessageCount,
            draft = draftFor(state, conversation, selectedProviderId),
            error = null,
        )
    }

    fun clearSelection(state: ChatUiState): ChatUiState =
        state.copy(
            selectedConversationId = null,
            messages = emptyList(),
            selectedConversationMessageCount = 0,
            draft = DraftState(),
        )

    fun selectedConversation(state: ChatUiState): Conversation? =
        state.selectedConversationId?.let { id -> state.conversations.firstOrNull { it.id == id } }

    fun createMessage(
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

    private fun draftFor(
        state: ChatUiState,
        conversation: Conversation,
        selectedProviderId: String?,
    ): DraftState =
        DraftState(
            title = conversation.title,
            systemPrompt = conversation.systemPrompt.orEmpty(),
            model = conversation.defaultModel
                ?: selectedProviderId?.let { id ->
                    state.providers.firstOrNull { it.id.value == id }?.preferredModel()
                }.orEmpty(),
            temperature = conversation.modelParameters.temperature?.toString().orEmpty(),
            topP = conversation.modelParameters.topP?.toString().orEmpty(),
            maxTokens = conversation.modelParameters.maxTokens?.toString().orEmpty(),
            temporary = conversation.isTemporary,
            sensitive = conversation.isSensitive,
        )
}
