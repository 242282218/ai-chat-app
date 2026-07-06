package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ChatConfig
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.providerFailureSummary
import com.aichat.workbench.provider.api.recoverySummary
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val chatProvider: ChatProvider,
    private val clock: Clock,
    private val chatConfig: ChatConfig = ChatConfig(),
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
            var terminalSeen = false

            suspend fun flush(force: Boolean = false) {
                if (!dirty) return
                val now = clock.millis()
                if (force || deltaCount >= chatConfig.flushDeltaCount || now - lastFlushAt >= chatConfig.flushIntervalMillis) {
                    conversationRepository.saveMessage(current)
                    dirty = false
                    deltaCount = 0
                    lastFlushAt = now
                }
            }

            try {
                chatProvider.stream(request).collect { event ->
                    if (terminalSeen) return@collect
                    val isDelta = event is ProviderStreamEvent.TextDelta ||
                        event is ProviderStreamEvent.ImageDelta
                    current = when (event) {
                        is ProviderStreamEvent.TextDelta -> current.withContent(
                            content = current.content + event.text,
                            status = MessageStatus.Streaming,
                            errorSummary = null,
                        )
                        is ProviderStreamEvent.ImageDelta -> current.withImage(
                            image = event.image,
                            status = MessageStatus.Streaming,
                            errorSummary = null,
                        )
                        ProviderStreamEvent.Completed -> current.withContent(
                            content = current.content,
                            status = MessageStatus.Completed,
                            errorSummary = null,
                        )
                        is ProviderStreamEvent.Failed -> current.withContent(
                            content = current.content,
                            status = MessageStatus.Failed,
                            errorSummary = event.error.recoverySummary(),
                        )
                    }
                    dirty = true
                    if (isDelta) deltaCount += 1
                    terminalSeen = current.status.isTerminal()
                    flush(force = terminalSeen)
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
            contentParts = content.toTextParts() + contentParts.filterIsInstance<MessagePart.Image>(),
            status = status,
            errorSummary = errorSummary,
            updatedAt = clock.instant(),
        )

    private fun Message.withImage(
        image: MessagePart.Image,
        status: MessageStatus,
        errorSummary: String? = this.errorSummary,
    ): Message {
        val nextContent = content.ifBlank { GENERATED_IMAGE_PLACEHOLDER }
        return copy(
            content = nextContent,
            contentParts = nextContent.toTextParts() + contentParts.filterIsInstance<MessagePart.Image>() + image,
            status = status,
            errorSummary = errorSummary,
            updatedAt = clock.instant(),
        )
    }

    private fun String.toTextParts(): List<MessagePart.Text> =
        if (isBlank()) emptyList() else listOf(MessagePart.Text(this))

    private fun Throwable.summary(): String =
        providerFailureSummary("Provider 请求失败。")

    private fun MessageStatus.isTerminal(): Boolean =
        this == MessageStatus.Completed || this == MessageStatus.Failed || this == MessageStatus.Cancelled

    private companion object {
        const val GENERATED_IMAGE_PLACEHOLDER = "已生成图片。"
    }
}
