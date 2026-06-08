package com.aichat.workbench.agent.runtime

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.EmptyMemoryRepository
import com.aichat.workbench.domain.repository.MemoryRepository
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ToolChoice
import java.time.Clock
import java.util.UUID

data class TaskConversationContext(
    val systemPrompt: String?,
    val history: List<ProviderChatMessage>,
    val summaryMessage: Message?,
)

interface TaskContextProvider {
    suspend fun build(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        messages: List<Message>,
        chatProvider: ChatProvider,
    ): TaskConversationContext
}

class TaskContextBuilder(
    private val conversationRepository: ConversationRepository,
    private val clock: Clock,
    private val memoryRepository: MemoryRepository = EmptyMemoryRepository,
    private val tokenEstimator: ContextTokenEstimator = ContextTokenEstimator(),
) : TaskContextProvider {
    override suspend fun build(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        messages: List<Message>,
        chatProvider: ChatProvider,
    ): TaskConversationContext {
        val boundary = messages.indexOfLast { it.isCompressedSummary() }
        val previousSummary = messages.getOrNull(boundary)?.content
        val trailingMessages = if (boundary >= 0) messages.drop(boundary + 1) else messages
        val memories = memoryRepository.findRelevantMemories(
            query = conversation.memoryQuery(trailingMessages),
            limit = MEMORY_CONTEXT_LIMIT,
        )
        val history = trailingMessages.toProviderHistory()
        val systemPrompt = conversation.systemPrompt
            .withSummary(previousSummary)
            .withMemories(memories)
        if (!shouldCompress(systemPrompt, history, provider, model)) {
            return TaskConversationContext(systemPrompt, history, summaryMessage = null)
        }

        val contextMessages = trailingMessages.contextMessages()
        val recentMessages = contextMessages.takeLast(RECENT_MESSAGE_COUNT)
        val oldMessages = contextMessages.dropLast(RECENT_MESSAGE_COUNT)
        if (previousSummary == null && oldMessages.isEmpty()) {
            return TaskConversationContext(systemPrompt, history, summaryMessage = null)
        }

        val summary = summarize(
            provider = provider,
            apiKey = apiKey,
            model = model,
            previousSummary = previousSummary,
            messages = oldMessages,
            chatProvider = chatProvider,
        )
        val summaryMessage = createSummaryMessage(
            conversation = conversation,
            provider = provider,
            model = model,
            summary = summary,
            compressedMessageCount = oldMessages.size + if (previousSummary == null) 0 else 1,
            firstRetainedMessage = recentMessages.firstOrNull(),
        )
        conversationRepository.saveMessage(summaryMessage)
        return TaskConversationContext(
            systemPrompt = conversation.systemPrompt
                .withSummary(summaryMessage.content)
                .withMemories(memories),
            history = recentMessages.toProviderHistory(),
            summaryMessage = summaryMessage,
        )
    }

    private suspend fun summarize(
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        previousSummary: String?,
        messages: List<Message>,
        chatProvider: ChatProvider,
    ): String {
        val request = ChatProviderRequest(
            provider = provider,
            apiKey = apiKey,
            model = model,
            systemPrompt = SUMMARY_SYSTEM_PROMPT,
            messages = listOf(
                ProviderChatMessage(
                    role = MessageRole.User,
                    content = summaryPrompt(previousSummary, messages),
                ),
            ),
            parameters = ModelParameters(temperature = 0.2),
            toolChoice = ToolChoice.None,
        )
        val summary = chatProvider.complete(request).content.trim()
        require(summary.isNotBlank()) { "长对话压缩摘要为空。" }
        return summary
    }

    private fun shouldCompress(
        systemPrompt: String?,
        history: List<ProviderChatMessage>,
        provider: ProviderConfig,
        model: String,
    ): Boolean {
        val tokens = tokenEstimator.estimateText(systemPrompt.orEmpty()) + tokenEstimator.estimateMessages(history)
        val limit = provider.maxContextTokens(model)
        return tokens > (limit * COMPRESSION_THRESHOLD).toInt()
    }

    private fun createSummaryMessage(
        conversation: Conversation,
        provider: ProviderConfig,
        model: String,
        summary: String,
        compressedMessageCount: Int,
        firstRetainedMessage: Message?,
    ): Message {
        val createdAt = firstRetainedMessage?.createdAt?.minusMillis(1) ?: clock.instant()
        val content = "早期对话摘要（${compressedMessageCount} 条消息）：\n\n$summary"
        return Message(
            id = MessageId("summary-${UUID.randomUUID()}"),
            conversationId = conversation.id,
            role = MessageRole.System,
            content = content,
            contentParts = listOf(MessagePart.Text(content)),
            providerId = provider.id,
            model = model,
            status = MessageStatus.Compressed,
            errorSummary = null,
            createdAt = createdAt,
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = firstRetainedMessage?.id,
        )
    }

    private fun summaryPrompt(previousSummary: String?, messages: List<Message>): String =
        buildString {
            appendLine("请压缩以下早期对话上下文，输出一段简洁中文摘要。")
            appendLine("必须保留用户目标、关键事实、约束、已确认结论、代码/API 名称、待办和未解决问题。")
            appendLine("不要添加原文没有的信息。")
            previousSummary?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("已有摘要：")
                appendLine(it)
            }
            if (messages.isNotEmpty()) {
                appendLine()
                appendLine("新增早期消息：")
                messages.forEachIndexed { index, message ->
                    appendLine("${index + 1}. ${message.role.name}: ${message.summaryText()}")
                }
            }
        }

    private fun Message.summaryText(): String =
        buildString {
            content.takeIf { it.isNotBlank() }?.let { append(it) }
            val images = contentParts.filterIsInstance<MessagePart.Image>()
            if (images.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("[${images.size} image(s)]")
            }
            if (toolCalls.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("Tool calls: ")
                append(toolCalls.joinToString { "${it.name}(${it.arguments})" })
            }
        }.ifBlank { "(empty)" }.take(SUMMARY_MESSAGE_LIMIT)

    private fun String?.withSummary(summary: String?): String? =
        listOfNotNull(
            this?.takeIf { it.isNotBlank() },
            summary?.takeIf { it.isNotBlank() }?.let {
                "以下是已压缩的早期对话摘要，后续回答必须把它视为历史上下文：\n$it"
            },
        ).joinToString("\n\n").takeIf { it.isNotBlank() }

    private fun String?.withMemories(memories: List<MemoryItem>): String? =
        listOfNotNull(
            this?.takeIf { it.isNotBlank() },
            memories.takeIf { it.isNotEmpty() }?.toMemoryPrompt(),
        ).joinToString("\n\n").takeIf { it.isNotBlank() }

    private fun List<MemoryItem>.toMemoryPrompt(): String =
        buildString {
            appendLine("以下是用户手动保存的长期记忆。它们可能与当前任务相关；使用时不要泄露不存在的信息：")
            this@toMemoryPrompt.take(MEMORY_CONTEXT_LIMIT).forEachIndexed { index, memory ->
                appendLine("${index + 1}. [${memory.kind.name}] ${memory.content.trim().take(MEMORY_ITEM_CONTEXT_LIMIT)}")
            }
        }.trim()

    private fun Conversation.memoryQuery(messages: List<Message>): String =
        buildString {
            appendLine(title)
            systemPrompt?.takeIf { it.isNotBlank() }?.let(::appendLine)
            messages
                .filter { it.role == MessageRole.User || it.role == MessageRole.Assistant }
                .takeLast(MEMORY_QUERY_MESSAGE_COUNT)
                .forEach { appendLine(it.content) }
        }

    private fun List<Message>.contextMessages(): List<Message> =
        filter { message ->
            when (message.role) {
                MessageRole.User -> message.status == MessageStatus.Completed
                MessageRole.Assistant -> message.status == MessageStatus.Completed
                MessageRole.Tool -> message.status == MessageStatus.Completed || message.status == MessageStatus.Failed
                MessageRole.System -> false
            }
        }

    private fun List<Message>.toProviderHistory(): List<ProviderChatMessage> =
        contextMessages().map { message ->
            val providerContent = message.providerContextContent()
            ProviderChatMessage(
                role = message.role,
                content = providerContent,
                contentParts = if (message.role == MessageRole.Tool) {
                    listOf(MessagePart.Text(providerContent))
                } else {
                    message.contentParts
                },
                toolCalls = message.toolCalls,
                toolCallId = message.toolCallId,
            )
        }

    private fun Message.providerContextContent(): String =
        if (role != MessageRole.Tool) {
            content
        } else {
            toolContextSummary()
        }

    private fun Message.toolContextSummary(): String =
        buildString {
            appendLine("工具结果摘要：")
            toolCallId?.value?.let { appendLine("toolCallId: $it") }
            appendLine("status: ${status.name}")
            errorSummary?.takeIf { it.isNotBlank() }?.let { appendLine("error: ${it.take(TOOL_ERROR_CONTEXT_LIMIT)}") }
            val normalized = content.replace(Regex("\\s+"), " ").trim()
            if (normalized.isNotBlank()) {
                val truncated = normalized.length > TOOL_RESULT_CONTEXT_LIMIT
                append("content: ")
                append(normalized.take(TOOL_RESULT_CONTEXT_LIMIT))
                if (truncated) append(" ...[truncated]")
            }
        }.trim()

    private fun Message.isCompressedSummary(): Boolean =
        role == MessageRole.System && status == MessageStatus.Compressed

    private fun ProviderConfig.maxContextTokens(model: String): Int =
        models.firstOrNull { it.id == model }
            ?.capability
            ?.maxContextTokens
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_CONTEXT_TOKENS

    private companion object {
        const val COMPRESSION_THRESHOLD = 0.8
        const val DEFAULT_MAX_CONTEXT_TOKENS = 32_000
        const val RECENT_MESSAGE_COUNT = 12
        const val MEMORY_CONTEXT_LIMIT = 6
        const val MEMORY_ITEM_CONTEXT_LIMIT = 700
        const val MEMORY_QUERY_MESSAGE_COUNT = 8
        const val SUMMARY_MESSAGE_LIMIT = 4_000
        const val TOOL_RESULT_CONTEXT_LIMIT = 1_200
        const val TOOL_ERROR_CONTEXT_LIMIT = 400
        const val SUMMARY_SYSTEM_PROMPT = "你是长对话上下文压缩器，只输出忠实摘要。"
    }
}
