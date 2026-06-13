package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>

    suspend fun getConversation(id: ConversationId): Conversation?

    suspend fun saveConversation(conversation: Conversation)

    suspend fun renameConversation(id: ConversationId, title: String)

    suspend fun deleteConversation(id: ConversationId)

    fun observeMessages(conversationId: ConversationId): Flow<List<Message>>

    fun observeRecentMessages(conversationId: ConversationId, limit: Int): Flow<List<Message>> =
        observeMessages(conversationId).map { messages -> messages.takeLast(limit) }

    fun observeMessageCount(conversationId: ConversationId): Flow<Int> =
        observeMessages(conversationId).map { it.size }

    suspend fun getMessages(conversationId: ConversationId): List<Message>

    suspend fun saveMessage(message: Message)

    suspend fun deleteMessages(conversationId: ConversationId)

    suspend fun deleteMessage(messageId: MessageId)

    fun observeConversationsWithPreview(): Flow<List<ConversationPreview>>
}
