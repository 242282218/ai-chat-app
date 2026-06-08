package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.tool.local.LocalToolExecutor

internal class LocalToolRunner(
    private val localToolExecutor: LocalToolExecutor,
) {
    fun canRun(name: String): Boolean =
        localToolExecutor.canExecute(name)

    suspend fun run(conversationId: ConversationId, toolCall: ToolCall): ExecutedToolOutput =
        localToolExecutor
            .execute(conversationId, toolCall)
            .let { ExecutedToolOutput(it.output, it.contentParts) }
}
