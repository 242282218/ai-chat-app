package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolResult
import kotlinx.coroutines.flow.Flow

interface ToolInvocationRepository {
    fun observeToolInvocations(): Flow<List<ToolResult>>

    suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult)
}
