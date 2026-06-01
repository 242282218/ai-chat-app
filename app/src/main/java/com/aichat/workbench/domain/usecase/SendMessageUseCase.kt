package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val chatProvider: ChatProvider,
    private val clock: Clock,
) {
    operator fun invoke(
        assistantMessage: Message,
        request: ChatProviderRequest,
    ): Flow<Message> =
        flow {
            var current = assistantMessage.withContent(
                content = assistantMessage.content,
                status = MessageStatus.Streaming,
            )
            conversationRepository.saveMessage(current)
            emit(current)
            var dirty = false
            var deltaCount = 0
            var lastFlushAt = clock.millis()

            suspend fun flush(force: Boolean = false) {
                if (!dirty) return
                val now = clock.millis()
                if (force || deltaCount >= FLUSH_DELTA_COUNT || now - lastFlushAt >= FLUSH_INTERVAL_MILLIS) {
                    conversationRepository.saveMessage(current)
                    dirty = false
                    deltaCount = 0
                    lastFlushAt = now
                }
            }

            try {
                chatProvider.stream(request).collect { event ->
                    val isDelta = event is ProviderStreamEvent.TextDelta || event is ProviderStreamEvent.ToolCallDelta
                    current = when (event) {
                        is ProviderStreamEvent.TextDelta -> current.withContent(
                            content = current.content + event.text,
                            status = MessageStatus.Streaming,
                            errorSummary = null,
                        )
                        is ProviderStreamEvent.ToolCallDelta -> current.copy(
                            toolCalls = current.toolCalls.upsert(event.toolCall),
                            status = MessageStatus.Streaming,
                            errorSummary = null,
                            updatedAt = clock.instant(),
                        )
                        ProviderStreamEvent.Completed -> current.withContent(
                            content = current.content,
                            status = MessageStatus.Completed,
                            errorSummary = null,
                        )
                        is ProviderStreamEvent.Failed -> current.withContent(
                            content = current.content,
                            status = MessageStatus.Failed,
                            errorSummary = event.error.message,
                        )
                    }
                    dirty = true
                    if (isDelta) deltaCount += 1
                    flush(force = current.status.isTerminal())
                    emit(current)
                }
                flush(force = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                current = current.withContent(
                    content = current.content,
                    status = MessageStatus.Failed,
                    errorSummary = error.summary(),
                )
                conversationRepository.saveMessage(current)
                emit(current)
            }
        }

    private fun Message.withContent(
        content: String,
        status: MessageStatus,
        errorSummary: String? = this.errorSummary,
    ): Message =
        copy(
            content = content,
            contentParts = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
            status = status,
            errorSummary = errorSummary,
            updatedAt = clock.instant(),
        )

    private fun Throwable.summary(): String =
        when (this) {
            is ProviderHttpException -> error.message
            else -> message ?: "Provider 请求失败。"
        }

    private fun MessageStatus.isTerminal(): Boolean =
        this == MessageStatus.Completed || this == MessageStatus.Failed || this == MessageStatus.Cancelled

    private fun List<ToolCall>.upsert(toolCall: ToolCall): List<ToolCall> =
        filterNot { it.id == toolCall.id } + toolCall

    private companion object {
        const val FLUSH_DELTA_COUNT = 10
        const val FLUSH_INTERVAL_MILLIS = 500L
    }
}
