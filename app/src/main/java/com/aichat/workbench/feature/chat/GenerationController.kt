package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Presentation adapter for chat generation.
 *
 * ChatTurnOrchestrator owns provider turns and context building. This class keeps
 * UI lifecycle and stop/cancel semantics.
 */
class GenerationController(
    private val conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    conversationCompactor: ConversationCompactor,
    providerRegistry: ProviderRegistry,
    private val clock: Clock,
) {
    private val chatTurnOrchestrator = ChatTurnOrchestrator(
        conversationRepository = conversationRepository,
        providerRepository = providerRepository,
        contextProvider = conversationCompactor,
        providerRegistry = providerRegistry,
        clock = clock,
    )
    private val stateMutex = Mutex()
    private var generationJob: Job? = null
    private var activeAssistantMessage: Message? = null

    val isActive: Boolean
        get() = generationJob?.isActive == true

    fun start(
        scope: CoroutineScope,
        current: ChatUiState,
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
        onConversationReady: (Conversation) -> Unit,
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
            cancelActiveGeneration(onStateChanged)
        }
    }

    suspend fun cancelActiveGenerationAndPersist(
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        cancelActiveGeneration(onStateChanged)
    }

    private suspend fun cancelActiveGeneration(
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        stateMutex.withLock {
            val active = activeAssistantMessage
            val job = generationJob

            job?.cancel()
            generationJob = null

            active?.let { message ->
                withContext(NonCancellable) {
                    conversationRepository.saveMessage(message.cancelledCopy())
                }
            }

            activeAssistantMessage = null
            onStateChanged { it.copy(isGenerating = false) }
        }
    }

    private fun Message.cancelledCopy(): Message =
        copy(
            status = MessageStatus.Cancelled,
            errorSummary = "已停止，已保留当前回复内容。",
            updatedAt = clock.instant(),
        )

    private suspend fun runGeneration(
        current: ChatUiState,
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
        onConversationReady: (Conversation) -> Unit,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        try {
            chatTurnOrchestrator.run(
                request = current.toChatTurnRequest(
                    userText = userText,
                    editedMessage = editedMessage,
                    retryFailedMessage = retryFailedMessage,
                ),
                callbacks = object : ChatTurnCallbacks {
                    override suspend fun onConversationReady(conversation: Conversation) {
                        onConversationReady(conversation)
                    }

                    override suspend fun onInputAccepted() {
                        onStateChanged {
                            it.copy(
                                draft = it.draft.copy(input = "", editingMessageId = null),
                                imageDrafts = emptyList(),
                                isGenerating = true,
                                error = null,
                            )
                        }
                    }

                    override suspend fun onActiveAssistantMessageChanged(message: Message?) {
                        activeAssistantMessage = message
                    }

                    override suspend fun onMessageChanged(message: Message) {
                        onStateChanged { it.copy(messages = it.messages.upsert(message)) }
                    }

                    override suspend fun onSummaryMessageCreated(message: Message) {
                        onStateChanged { it.copy(messages = it.messages.upsertSorted(message)) }
                    }

                    override suspend fun onError(message: String?) {
                        onStateChanged { it.copy(error = message) }
                    }
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onStateChanged { it.copy(error = error.message ?: "发送失败。") }
        } finally {
            activeAssistantMessage = null
            onStateChanged { it.copy(isGenerating = false) }
        }
    }

    private fun ChatUiState.toChatTurnRequest(
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
    ): ChatTurnRequest =
        ChatTurnRequest(
            selectedConversationId = selectedConversationId,
            providers = providers,
            modelRolePreferences = modelRolePreferences,
            selectedProviderId = selectedProviderId,
            messages = messages,
            imageDrafts = imageDrafts,
            userText = userText,
            editedMessage = editedMessage,
            retryFailedMessage = retryFailedMessage,
        )

    private fun List<Message>.upsert(message: Message): List<Message> =
        if (any { it.id == message.id }) {
            map { if (it.id == message.id) message else it }
        } else {
            this + message
        }

    private fun List<Message>.upsertSorted(message: Message): List<Message> =
        upsert(message).sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id.value })
}
