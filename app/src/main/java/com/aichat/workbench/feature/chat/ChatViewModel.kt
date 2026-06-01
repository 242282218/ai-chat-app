package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val promptPresetRepository: PromptPresetRepository,
    private val conversationManager: ConversationManager,
    private val generationController: GenerationController,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(draft = DraftState.fromSavedState(savedStateHandle)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var messagesJob: Job? = null
    private var observedConversationId: ConversationId? = null
    init {
        viewModelScope.observeChatStateSources(
            conversationRepository,
            providerRepository,
            promptPresetRepository,
            conversationManager,
            currentState = { _state.value },
            updateState = ::updateState,
            observeMessages = ::observeMessages,
        )
    }
    fun selectConversation(id: ConversationId) {
        val conversation = _state.value.conversations.firstOrNull { it.id == id } ?: return
        selectConversation(conversation)
    }
    fun updateInput(value: String) = updateDraft { it.copy(input = value) }
    fun updateTitleDraft(value: String) = updateDraft { it.copy(title = value) }
    fun updateSystemPromptDraft(value: String) = updateDraft { it.copy(systemPrompt = value) }
    fun updateModelDraft(value: String) = updateDraft { it.copy(model = value) }
    fun updateTemperatureDraft(value: String) = updateDraft { it.copy(temperature = value) }
    fun updateTopPDraft(value: String) = updateDraft { it.copy(topP = value) }
    fun updateMaxTokensDraft(value: String) = updateDraft { it.copy(maxTokens = value) }
    fun updateTemporaryDraft(value: Boolean) = updateDraft { it.copy(temporary = value) }
    fun updateSensitiveDraft(value: Boolean) = updateDraft { it.copy(sensitive = value) }
    fun applyInitialDraft(input: String, temporary: Boolean) {
        val draftInput = input.trim()
        if (draftInput.isBlank() && !temporary) return
        updateDraft {
            it.copy(
                input = draftInput.ifBlank { it.input },
                temporary = temporary || it.temporary,
            )
        }
    }
    fun addImageDraft(image: MessagePart.Image) = updateState { it.copy(imageDrafts = it.imageDrafts + image, error = null) }
    fun removeImageDraft(index: Int) = updateState {
        it.copy(imageDrafts = it.imageDrafts.filterIndexed { itemIndex, _ -> itemIndex != index })
    }
    fun reportImageInputError(message: String) = updateState { it.copy(error = message) }
    fun toggleSettingsExpanded() = updateState { it.copy(settingsExpanded = !it.settingsExpanded) }
    fun togglePromptsExpanded() = updateState { it.copy(promptsExpanded = !it.promptsExpanded) }
    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id }
        updateState {
            it.copy(
                selectedProviderId = id,
                draft = it.draft.copy(model = it.modelDraft.ifBlank { provider?.defaultModel.orEmpty() }),
            )
        }
    }
    fun createConversation() = createConversation(title = "新对话", temporary = false)
    fun createTemporaryConversation() = createConversation(title = "临时对话", temporary = true)
    fun renameSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        val title = _state.value.titleDraft.trim()
        if (title.isBlank()) return
        viewModelScope.launch { conversationRepository.renameConversation(id, title) }
    }

    fun saveConversationSettings() {
        val current = _state.value
        if (conversationManager.selectedConversation(current) == null) return
        viewModelScope.launch {
            runCatching { conversationManager.saveSelectedSettings(current) }
                .onSuccess { updateState { it.copy(error = null) } }
                .onFailure { error -> updateState { it.copy(error = error.message ?: "模型参数无效。") } }
        }
    }
    fun applyPromptPreset(id: PromptPresetId) {
        val preset = _state.value.promptPresets.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            selectConversation(conversationManager.applyPromptPreset(_state.value, preset))
        }
    }
    fun archiveSelectedConversation() = runForSelected { conversationRepository.archiveConversation(it) }
    fun deleteSelectedConversation() = runForSelected { conversationRepository.deleteConversation(it) }

    fun deleteTemporaryConversationOnExit() {
        val conversation = conversationManager.selectedConversation(_state.value)
        if (conversation?.isTemporary != true) return
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversation.id)
            clearSelection()
        }
    }

    fun clearContext() {
        val id = _state.value.selectedConversationId ?: return
        if (generationController.isActive) return
        viewModelScope.launch {
            conversationRepository.deleteMessages(id)
            updateState { it.copy(error = null, draft = it.draft.copy(input = "", editingMessageId = null)) }
        }
    }

    fun editMessage(id: MessageId) {
        val message = _state.value.messages.firstOrNull { it.id == id && it.role == MessageRole.User } ?: return
        updateState { it.copy(draft = it.draft.copy(input = message.content, editingMessageId = id), error = null) }
    }

    fun cancelEdit() = updateDraft { it.copy(editingMessageId = null, input = "") }

    fun sendMessage() {
        val current = _state.value
        val text = current.input.trim()
        if ((text.isBlank() && current.imageDrafts.isEmpty()) || generationController.isActive) return
        val edited = current.editingMessageId?.let { id ->
            current.messages.firstOrNull { it.id == id && it.role == MessageRole.User }
        }
        val userText = text.ifBlank { "图片消息" }
        generationController.start(viewModelScope, current, userText, edited, null, ::selectGeneratedConversation, ::updateState)
    }

    fun retryMessage(id: MessageId) {
        val failed = _state.value.messages.firstOrNull {
            it.id == id && it.role == MessageRole.Assistant && it.status == MessageStatus.Failed
        } ?: return
        if (generationController.isActive) return
        generationController.start(viewModelScope, _state.value, null, null, failed, ::selectGeneratedConversation, ::updateState)
    }

    fun stopGeneration() = generationController.stop(viewModelScope, ::updateState)
    fun confirmToolCall() = generationController.confirmToolCall()
    fun denyToolCall() = generationController.denyToolCall()

    private fun createConversation(title: String, temporary: Boolean) {
        viewModelScope.launch {
            selectConversation(conversationManager.createConversation(_state.value, title, temporary))
        }
    }

    private fun runForSelected(block: suspend (ConversationId) -> Unit) {
        val id = _state.value.selectedConversationId ?: return
        viewModelScope.launch {
            block(id)
            clearSelection()
        }
    }

    private fun selectGeneratedConversation(conversation: Conversation) {
        if (_state.value.selectedConversationId != conversation.id) selectConversation(conversation)
    }

    private fun selectConversation(conversation: Conversation) {
        updateState { conversationManager.withSelectedConversation(it, it.conversations, conversation) }
        observeMessages(conversation.id)
    }

    private fun observeMessages(id: ConversationId) {
        if (observedConversationId == id) return
        observedConversationId = id
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            conversationRepository.observeMessages(id).collect { messages ->
                updateState { it.copy(messages = messages) }
            }
        }
    }

    private fun clearSelection() {
        observedConversationId = null
        messagesJob?.cancel()
        messagesJob = null
        updateState { conversationManager.clearSelection(it) }
    }

    private fun updateDraft(transform: (DraftState) -> DraftState) =
        updateState { it.copy(draft = transform(it.draft)) }

    private fun updateState(transform: (ChatUiState) -> ChatUiState) {
        var nextState: ChatUiState? = null
        _state.update { current -> transform(current).also { nextState = it } }
        nextState?.draft?.toSavedState(savedStateHandle)
    }
}
