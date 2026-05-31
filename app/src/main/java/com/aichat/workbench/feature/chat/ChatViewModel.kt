package com.aichat.workbench.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.app.AppGraph
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val messages: List<Message> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val promptPresets: List<PromptPreset> = emptyList(),
    val selectedProviderId: String? = null,
    val input: String = "",
    val titleDraft: String = "",
    val systemPromptDraft: String = "",
    val modelDraft: String = "",
    val temperatureDraft: String = "",
    val topPDraft: String = "",
    val maxTokensDraft: String = "",
    val temporaryDraft: Boolean = false,
    val sensitiveDraft: Boolean = false,
    val editingMessageId: MessageId? = null,
    val settingsExpanded: Boolean = false,
    val promptsExpanded: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val promptPresetRepository: PromptPresetRepository,
    private val openAiProvider: ChatProvider,
    private val compatibleProvider: ChatProvider,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var messagesJob: Job? = null
    private var observedConversationId: ConversationId? = null
    private var generationJob: Job? = null
    private var activeAssistantMessage: Message? = null

    init {
        viewModelScope.launch {
            conversationRepository.observeConversations().collect { conversations ->
                val current = _state.value
                val selected = conversations.firstOrNull { it.id == current.selectedConversationId }
                    ?: conversations.firstOrNull()

                _state.update { state ->
                    if (selected == null || selected.id == state.selectedConversationId) {
                        state.copy(
                            conversations = conversations,
                            selectedConversationId = selected?.id,
                        )
                    } else {
                        state.withSelectedConversation(conversations, selected)
                    }
                }
                selected?.id?.let(::observeMessages)
            }
        }
        viewModelScope.launch {
            providerRepository.observeProviders().collect { providers ->
                _state.update { current ->
                    val selectedProvider = current.selectedProviderId
                        ?.let { id -> providers.firstOrNull { it.id.value == id && it.enabled } }
                    val fallbackProvider = selectedProvider ?: providers.firstOrNull { it.enabled }

                    current.copy(
                        providers = providers,
                        selectedProviderId = fallbackProvider?.id?.value,
                        modelDraft = current.modelDraft.ifBlank { fallbackProvider?.defaultModel.orEmpty() },
                    )
                }
            }
        }
        viewModelScope.launch {
            promptPresetRepository.observePromptPresets().collect { presets ->
                _state.update { it.copy(promptPresets = presets) }
            }
        }
    }

    fun selectConversation(id: ConversationId) {
        val conversation = _state.value.conversations.firstOrNull { it.id == id } ?: return
        _state.update { state ->
            state.withSelectedConversation(state.conversations, conversation)
        }
        observeMessages(id)
    }

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun updateTitleDraft(value: String) {
        _state.update { it.copy(titleDraft = value) }
    }

    fun updateSystemPromptDraft(value: String) {
        _state.update { it.copy(systemPromptDraft = value) }
    }

    fun updateModelDraft(value: String) {
        _state.update { it.copy(modelDraft = value) }
    }

    fun updateTemperatureDraft(value: String) {
        _state.update { it.copy(temperatureDraft = value) }
    }

    fun updateTopPDraft(value: String) {
        _state.update { it.copy(topPDraft = value) }
    }

    fun updateMaxTokensDraft(value: String) {
        _state.update { it.copy(maxTokensDraft = value) }
    }

    fun updateTemporaryDraft(value: Boolean) {
        _state.update { it.copy(temporaryDraft = value) }
    }

    fun updateSensitiveDraft(value: Boolean) {
        _state.update { it.copy(sensitiveDraft = value) }
    }

    fun toggleSettingsExpanded() {
        _state.update { it.copy(settingsExpanded = !it.settingsExpanded) }
    }

    fun togglePromptsExpanded() {
        _state.update { it.copy(promptsExpanded = !it.promptsExpanded) }
    }

    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id }
        _state.update {
            it.copy(
                selectedProviderId = id,
                modelDraft = it.modelDraft.ifBlank { provider?.defaultModel.orEmpty() },
            )
        }
    }

    fun createConversation() {
        viewModelScope.launch {
            val providerId = _state.value.selectedProviderId?.let(::ProviderId)
            val conversation = CreateConversationUseCase(conversationRepository, clock)(
                title = "New chat",
                defaultProviderId = providerId,
                defaultModel = _state.value.modelDraft.trim().ifBlank { null },
                isSensitive = _state.value.sensitiveDraft,
            )
            selectConversation(conversation.id)
        }
    }

    fun createTemporaryConversation() {
        viewModelScope.launch {
            val providerId = _state.value.selectedProviderId?.let(::ProviderId)
            val conversation = CreateConversationUseCase(conversationRepository, clock)(
                title = "Temporary chat",
                defaultProviderId = providerId,
                defaultModel = _state.value.modelDraft.trim().ifBlank { null },
                isTemporary = true,
                isSensitive = _state.value.sensitiveDraft,
            )
            selectConversation(conversation.id)
        }
    }

    fun renameSelectedConversation() {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val title = current.titleDraft.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            conversationRepository.renameConversation(id, title)
        }
    }

    fun saveConversationSettings() {
        val current = _state.value
        val conversation = current.selectedConversation() ?: return
        val parameters = runCatching { current.modelParameters() }
            .onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Invalid model parameters.") }
            }
            .getOrNull() ?: return

        viewModelScope.launch {
            conversationRepository.saveConversation(
                conversation.copy(
                    defaultProviderId = current.selectedProviderId?.let(::ProviderId),
                    defaultModel = current.modelDraft.trim().ifBlank { null },
                    modelParameters = parameters,
                    systemPrompt = current.systemPromptDraft.trim().ifBlank { null },
                    isTemporary = current.temporaryDraft,
                    isSensitive = current.sensitiveDraft,
                    updatedAt = clock.instant(),
                ),
            )
            _state.update { it.copy(error = null) }
        }
    }

    fun applyPromptPreset(id: PromptPresetId) {
        val preset = _state.value.promptPresets.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val conversation = _state.value.selectedConversation()
            if (conversation == null) {
                val created = CreateConversationUseCase(conversationRepository, clock)(
                    title = preset.name,
                    defaultProviderId = _state.value.selectedProviderId?.let(::ProviderId),
                    defaultModel = preset.defaultModel,
                    systemPrompt = preset.systemPrompt,
                )
                selectConversation(created.id)
                return@launch
            }

            val updated = conversation.copy(
                systemPrompt = preset.systemPrompt,
                defaultModel = preset.defaultModel ?: conversation.defaultModel,
                updatedAt = clock.instant(),
            )
            conversationRepository.saveConversation(updated)
            _state.update { state -> state.withSelectedConversation(state.conversations, updated) }
        }
    }

    fun archiveSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        viewModelScope.launch {
            conversationRepository.archiveConversation(id)
            clearSelection()
        }
    }

    fun deleteSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            clearSelection()
        }
    }

    fun deleteTemporaryConversationOnExit() {
        val conversation = _state.value.selectedConversation()
        if (conversation?.isTemporary != true) return
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversation.id)
            clearSelection()
        }
    }

    fun clearContext() {
        val id = _state.value.selectedConversationId ?: return
        if (generationJob?.isActive == true) return
        viewModelScope.launch {
            conversationRepository.deleteMessages(id)
            _state.update { it.copy(error = null, editingMessageId = null, input = "") }
        }
    }

    fun editMessage(id: MessageId) {
        val message = _state.value.messages.firstOrNull { it.id == id && it.role == MessageRole.User } ?: return
        _state.update {
            it.copy(
                input = message.content,
                editingMessageId = id,
                error = null,
            )
        }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingMessageId = null, input = "") }
    }

    fun sendMessage() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isBlank() || generationJob?.isActive == true) return
        val editedMessage = current.editingMessageId?.let { id ->
            current.messages.firstOrNull { it.id == id && it.role == MessageRole.User }
        }

        generationJob = viewModelScope.launch {
            runGeneration(
                userText = text,
                editedMessage = editedMessage,
                retryFailedMessage = null,
            )
        }
    }

    fun retryMessage(id: MessageId) {
        val failed = _state.value.messages.firstOrNull {
            it.id == id && it.role == MessageRole.Assistant && it.status == MessageStatus.Failed
        } ?: return
        if (generationJob?.isActive == true) return

        generationJob = viewModelScope.launch {
            runGeneration(
                userText = null,
                editedMessage = null,
                retryFailedMessage = failed,
            )
        }
    }

    fun stopGeneration() {
        val active = activeAssistantMessage
        generationJob?.cancel()
        generationJob = null
        if (active != null) {
            viewModelScope.launch {
                conversationRepository.saveMessage(
                    active.copy(
                        status = MessageStatus.Cancelled,
                        updatedAt = clock.instant(),
                    ),
                )
            }
        }
        activeAssistantMessage = null
        _state.update { it.copy(isGenerating = false) }
    }

    private suspend fun runGeneration(
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
    ) {
        try {
            val conversation = selectedOrNewConversation(userText ?: "Retry")
            val provider = providerFor(conversation, retryFailedMessage)
            val apiKey = providerRepository.getApiKey(provider.id)
            require(!apiKey.isNullOrBlank()) { "API key is missing." }

            val model = modelFor(provider, conversation, retryFailedMessage)
            val historySeed = _state.value.messages
            val userMessage = userText?.let {
                message(
                    conversation = conversation,
                    role = MessageRole.User,
                    content = it,
                    status = MessageStatus.Completed,
                    provider = provider,
                    model = model,
                    parentMessageId = editedMessage?.id,
                )
            }

            _state.update { it.copy(input = "", editingMessageId = null, isGenerating = true, error = null) }
            if (userMessage != null) {
                conversationRepository.saveMessage(userMessage)
                maybeRenameConversation(conversation, userMessage.content)
            }

            val history = when {
                editedMessage != null && userMessage != null -> editHistory(historySeed, editedMessage, userMessage)
                retryFailedMessage != null -> retryHistory(historySeed, retryFailedMessage)
                else -> conversationRepository.getMessages(conversation.id)
            }.toProviderHistory()

            val assistant = message(
                conversation = conversation,
                role = MessageRole.Assistant,
                content = "",
                status = MessageStatus.Pending,
                provider = provider,
                model = model,
                parentMessageId = retryFailedMessage?.id ?: userMessage?.id,
            )
            activeAssistantMessage = assistant

            val request = ChatProviderRequest(
                provider = provider,
                apiKey = apiKey,
                model = model,
                systemPrompt = conversation.systemPrompt,
                messages = history,
                parameters = conversation.modelParameters,
            )
            SendMessageUseCase(conversationRepository, providerClient(provider), clock)(assistant, request)
                .collect { activeAssistantMessage = it }

            activeAssistantMessage?.takeIf { it.status == MessageStatus.Failed }?.let { failed ->
                _state.update { it.copy(error = failed.errorSummary) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.update { it.copy(error = error.message ?: "Send failed.") }
        } finally {
            activeAssistantMessage = null
            _state.update { it.copy(isGenerating = false) }
        }
    }

    private fun observeMessages(id: ConversationId) {
        if (observedConversationId == id) return
        observedConversationId = id
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            conversationRepository.observeMessages(id).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    private suspend fun selectedOrNewConversation(firstMessage: String): Conversation {
        val selected = _state.value.selectedConversationId
        val existing = selected?.let { conversationRepository.getConversation(it) }
        if (existing != null) return existing

        val conversation = CreateConversationUseCase(conversationRepository, clock)(
            title = firstMessage.take(40),
            defaultProviderId = _state.value.selectedProviderId?.let(::ProviderId),
            defaultModel = _state.value.modelDraft.trim().ifBlank { null },
            modelParameters = _state.value.modelParameters(),
            systemPrompt = _state.value.systemPromptDraft.trim().ifBlank { null },
            isTemporary = _state.value.temporaryDraft,
            isSensitive = _state.value.sensitiveDraft,
        )
        _state.update { it.withSelectedConversation(it.conversations, conversation) }
        observeMessages(conversation.id)
        return conversation
    }

    private suspend fun maybeRenameConversation(conversation: Conversation, firstMessage: String) {
        if (conversation.title != "New chat" && conversation.title != "Temporary chat") return
        conversationRepository.renameConversation(conversation.id, firstMessage.take(40))
    }

    private fun providerFor(
        conversation: Conversation,
        retryFailedMessage: Message?,
    ): ProviderConfig {
        val current = _state.value
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
            ?: error("Provider is not configured.")
    }

    private fun modelFor(
        provider: ProviderConfig,
        conversation: Conversation,
        retryFailedMessage: Message?,
    ): String =
        retryFailedMessage?.model
            ?: conversation.defaultModel
            ?: _state.value.modelDraft.trim().ifBlank { null }
            ?: provider.defaultModel
            ?: provider.models.firstOrNull()?.id
            ?: error("Default model is missing.")

    private fun providerClient(provider: ProviderConfig): ChatProvider =
        when (provider.type) {
            ProviderType.OpenAI -> openAiProvider
            ProviderType.OpenAICompatible -> compatibleProvider
        }

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

    private fun List<Message>.toProviderHistory(): List<ProviderChatMessage> =
        filter { message ->
            when (message.role) {
                MessageRole.User -> message.status == MessageStatus.Completed
                MessageRole.Assistant -> message.status == MessageStatus.Completed
                MessageRole.System,
                MessageRole.Tool -> false
            }
        }.map { ProviderChatMessage(it.role, it.content) }

    private fun message(
        conversation: Conversation,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        provider: ProviderConfig,
        model: String,
        parentMessageId: MessageId?,
    ): Message =
        Message(
            id = MessageId(UUID.randomUUID().toString()),
            conversationId = conversation.id,
            role = role,
            content = content,
            contentParts = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
            providerId = provider.id,
            model = model,
            status = status,
            errorSummary = null,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = parentMessageId,
        )

    private fun clearSelection() {
        observedConversationId = null
        messagesJob?.cancel()
        messagesJob = null
        _state.update {
            it.copy(
                selectedConversationId = null,
                messages = emptyList(),
                titleDraft = "",
                systemPromptDraft = "",
                modelDraft = "",
                temperatureDraft = "",
                topPDraft = "",
                maxTokensDraft = "",
                temporaryDraft = false,
                sensitiveDraft = false,
                editingMessageId = null,
            )
        }
    }

    private fun ChatUiState.selectedConversation(): Conversation? =
        selectedConversationId?.let { id -> conversations.firstOrNull { it.id == id } }

    private fun ChatUiState.modelParameters(): ModelParameters {
        val temperature = temperatureDraft.toNullableDouble("temperature")
        val topP = topPDraft.toNullableDouble("top_p")
        val maxTokens = maxTokensDraft.toNullableInt("max_tokens")

        require(temperature == null || temperature in 0.0..2.0) {
            "temperature must be between 0 and 2."
        }
        require(topP == null || topP in 0.0..1.0) {
            "top_p must be between 0 and 1."
        }
        require(maxTokens == null || maxTokens > 0) {
            "max_tokens must be greater than 0."
        }
        return ModelParameters(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
    }

    private fun String.toNullableDouble(name: String): Double? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        return trimmed.toDoubleOrNull() ?: error("$name must be a number.")
    }

    private fun String.toNullableInt(name: String): Int? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        return trimmed.toIntOrNull() ?: error("$name must be an integer.")
    }

    private fun ChatUiState.withSelectedConversation(
        conversations: List<Conversation>,
        conversation: Conversation,
    ): ChatUiState =
        copy(
            conversations = conversations,
            selectedConversationId = conversation.id,
            titleDraft = conversation.title,
            systemPromptDraft = conversation.systemPrompt.orEmpty(),
            modelDraft = conversation.defaultModel
                ?: selectedProviderId?.let { id ->
                    providers.firstOrNull { it.id.value == id }?.defaultModel
                }.orEmpty(),
            temperatureDraft = conversation.modelParameters.temperature?.toString().orEmpty(),
            topPDraft = conversation.modelParameters.topP?.toString().orEmpty(),
            maxTokensDraft = conversation.modelParameters.maxTokens?.toString().orEmpty(),
            temporaryDraft = conversation.isTemporary,
            sensitiveDraft = conversation.isSensitive,
            editingMessageId = null,
            error = null,
        )

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    conversationRepository = AppGraph.conversationRepository,
                    providerRepository = AppGraph.providerConfigRepository,
                    promptPresetRepository = AppGraph.promptPresetRepository,
                    openAiProvider = AppGraph.openAiChatProvider,
                    compatibleProvider = AppGraph.compatibleChatProvider,
                    clock = AppGraph.clock,
                ) as T
        }
    }
}
