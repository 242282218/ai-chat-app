package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class MessageSearchResult(
    val conversation: Conversation,
    val message: Message,
)

interface ConversationRepository {
    fun observeConversations(includeArchived: Boolean = false): Flow<List<Conversation>>

    suspend fun getConversation(id: ConversationId): Conversation?

    suspend fun saveConversation(conversation: Conversation)

    suspend fun renameConversation(id: ConversationId, title: String)

    suspend fun archiveConversation(id: ConversationId)

    suspend fun deleteConversation(id: ConversationId)

    fun observeMessages(conversationId: ConversationId): Flow<List<Message>>

    suspend fun getMessages(conversationId: ConversationId): List<Message>

    suspend fun saveMessage(message: Message)

    suspend fun deleteMessages(conversationId: ConversationId)

    fun searchMessages(query: String, limit: Int = 50): Flow<List<MessageSearchResult>> =
        flowOf(emptyList())
}
