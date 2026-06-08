package com.aichat.workbench.feature.chat

import com.aichat.workbench.agent.runtime.TaskOrchestrationCallbacks
import com.aichat.workbench.agent.runtime.TaskOrchestrationRequest
import com.aichat.workbench.agent.runtime.TaskOrchestrator
import com.aichat.workbench.agent.runtime.TaskToolApprovalDecision
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.tool.ToolExecutionService
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.tool.model.ToolDescriptor
import java.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Presentation adapter for task execution.
 *
 * TaskOrchestrator owns provider turns, context building, and tool loops. This
 * class keeps UI lifecycle, stop/cancel semantics, and tool approval state.
 */
class GenerationController(
    private val conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    private val conversationManager: ConversationManager,
    conversationCompactor: ConversationCompactor,
    providerRegistry: ProviderRegistry,
    toolExecutor: ToolExecutionService,
    private val clock: Clock,
) {
    private val taskOrchestrator = TaskOrchestrator(
        conversationRepository = conversationRepository,
        providerRepository = providerRepository,
        contextProvider = conversationCompactor,
        providerRegistry = providerRegistry,
        toolExecutor = toolExecutor,
        clock = clock,
    )
    private val stateMutex = Mutex()
    private var generationJob: Job? = null
    private var activeAssistantMessage: Message? = null
    private var pendingToolApproval: CompletableDeferred<TaskToolApprovalDecision>? = null
    private var activePendingToolCall: ToolCall? = null

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
            cancelActiveGeneration(onStateChanged, forceCancelJob = false)
        }
    }

    suspend fun cancelActiveGenerationAndPersist(
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ) {
        cancelActiveGeneration(onStateChanged, forceCancelJob = true)
    }

    fun confirmToolCall() {
        val toolCall = activePendingToolCall ?: return
        val completed = pendingToolApproval
            ?.complete(TaskToolApprovalDecision.Approved(toolCall))
            ?: false
        if (!completed) {
            android.util.Log.w("GenerationController", "confirmToolCall ignored: approval already resolved.")
        }
    }

    fun updatePendingToolArguments(arguments: String) {
        activePendingToolCall = activePendingToolCall?.copy(arguments = arguments)
    }

    fun denyToolCall() {
        val completed = pendingToolApproval
            ?.complete(TaskToolApprovalDecision.Denied)
            ?: false
        if (!completed) {
            android.util.Log.w("GenerationController", "denyToolCall ignored: approval already resolved.")
        }
    }

    private suspend fun cancelActiveGeneration(
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
        forceCancelJob: Boolean,
    ) {
        stateMutex.withLock {
            pendingToolApproval?.let { approval ->
                if (approval.complete(TaskToolApprovalDecision.Cancelled) && !forceCancelJob) {
                    onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
                    return
                }
            }

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
            pendingToolApproval?.complete(TaskToolApprovalDecision.Denied)
            pendingToolApproval = null
            activePendingToolCall = null
            onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
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
            taskOrchestrator.run(
                request = current.toTaskRequest(
                    userText = userText,
                    editedMessage = editedMessage,
                    retryFailedMessage = retryFailedMessage,
                ),
                callbacks = object : TaskOrchestrationCallbacks {
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

                    override suspend fun requestToolApproval(
                        toolCall: ToolCall,
                        descriptor: ToolDescriptor,
                    ): TaskToolApprovalDecision =
                        awaitToolApproval(toolCall, descriptor, onStateChanged)

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
            pendingToolApproval = null
            activePendingToolCall = null
            onStateChanged { it.copy(isGenerating = false, pendingToolCall = null) }
        }
    }

    private fun ChatUiState.toTaskRequest(
        userText: String?,
        editedMessage: Message?,
        retryFailedMessage: Message?,
    ): TaskOrchestrationRequest =
        TaskOrchestrationRequest(
            selectedConversationId = selectedConversationId,
            providers = providers,
            selectedProviderId = selectedProviderId,
            modelDraft = modelDraft,
            modelParameters = validatedModelParameters(),
            systemPromptDraft = systemPromptDraft,
            temporaryDraft = temporaryDraft,
            sensitiveDraft = sensitiveDraft,
            modelRolePreferences = modelRolePreferences,
            messages = messages,
            imageDrafts = imageDrafts,
            userText = userText,
            editedMessage = editedMessage,
            retryFailedMessage = retryFailedMessage,
        )

    private suspend fun awaitToolApproval(
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
        onStateChanged: ((ChatUiState) -> ChatUiState) -> Unit,
    ): TaskToolApprovalDecision {
        val approval = CompletableDeferred<TaskToolApprovalDecision>()
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

    private fun List<Message>.upsert(message: Message): List<Message> =
        if (any { it.id == message.id }) {
            map { if (it.id == message.id) message else it }
        } else {
            this + message
        }

    private fun List<Message>.upsertSorted(message: Message): List<Message> =
        upsert(message).sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id.value })
}
