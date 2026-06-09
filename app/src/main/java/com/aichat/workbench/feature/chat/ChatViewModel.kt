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
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val modelRolePreferenceRepository: ModelRolePreferenceRepository,
    private val conversationManager: ConversationManager,
    private val generationController: GenerationController,
    private val providerRegistry: ProviderRegistry,
    private val applicationScope: com.aichat.workbench.app.ApplicationScope,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(draft = DraftState.fromSavedState(savedStateHandle)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var messagesJob: Job? = null
    private var observedConversationId: ConversationId? = null
    init {
        viewModelScope.observeChatStateSources(
            conversationRepository,
            providerRepository,
            modelRolePreferenceRepository,
            conversationManager,
            providerRegistry,
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
    fun applyInitialDraft(input: String) {
        val draftInput = input.trim()
        if (draftInput.isBlank()) return
        updateDraft {
            it.copy(input = draftInput.ifBlank { it.input })
        }
    }
    fun addImageDraft(image: MessagePart.Image) = updateState { it.copy(imageDrafts = it.imageDrafts + image, error = null) }
    fun removeImageDraft(index: Int) = updateState {
        it.copy(imageDrafts = it.imageDrafts.filterIndexed { itemIndex, _ -> itemIndex != index })
    }
    fun reportImageInputError(message: String) = updateState { it.copy(error = message) }
    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id && it.enabled } ?: return
        updateState { it.copy(selectedProviderId = provider.id.value) }
    }
    fun createConversation() = createConversation(title = "新对话")
    fun renameSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        val title = _state.value.titleDraft.trim()
        if (title.isBlank()) return
        viewModelScope.launch { conversationRepository.renameConversation(id, title) }
    }

    fun deleteSelectedConversation() = runForSelected { conversationRepository.deleteConversation(it) }

    override fun onCleared() {
        super.onCleared()
        cleanupOnExit()
    }

    internal fun cleanupOnExit() {
        applicationScope.launch {
            generationController.cancelActiveGenerationAndPersist(::updateState)
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

    private fun createConversation(title: String) {
        viewModelScope.launch {
            selectConversation(conversationManager.createConversation(_state.value, title))
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
            combine(
                conversationRepository.observeRecentMessages(id, CHAT_MESSAGE_WINDOW_SIZE),
                conversationRepository.observeMessageCount(id),
            ) { messages, count ->
                messages to count
            }.collect { (messages, count) ->
                updateState {
                    it.copy(
                        messages = messages,
                        selectedConversationMessageCount = count,
                    )
                }
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

private const val CHAT_MESSAGE_WINDOW_SIZE = 200
