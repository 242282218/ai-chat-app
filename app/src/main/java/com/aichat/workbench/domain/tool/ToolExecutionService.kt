package com.aichat.workbench.domain.tool

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.tool.model.ToolDescriptor
import kotlinx.coroutines.CancellationException

data class ToolExecution(
    val result: ToolResult,
    val messageContent: String,
    val contentParts: List<MessagePart> = emptyList(),
)

class ToolExecutionCancelledException(
    val execution: ToolExecution,
    cause: CancellationException,
) : CancellationException(cause.message) {
    init {
        initCause(cause)
    }
}

interface ToolExecutionService {
    suspend fun availableTools(): List<ToolDescriptor>

    suspend fun descriptorFor(name: String): ToolDescriptor?

    suspend fun requiresConfirmation(descriptor: ToolDescriptor): Boolean

    suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): ToolExecution

    suspend fun execute(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution

    suspend fun deny(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution

    suspend fun cancel(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution
}
