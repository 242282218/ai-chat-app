package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toPreview

import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationRepository(
    private val dao: ConversationDao,
    private val clock: Clock,
) : ConversationRepository {
    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getConversation(id: ConversationId): Conversation? =
        dao.getConversation(id.value)?.toDomain()

    override suspend fun saveConversation(conversation: Conversation) {
        dao.upsertConversation(conversation.toEntity())
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        dao.renameConversation(id.value, title.trim(), clock.millis())
    }

    override suspend fun deleteConversation(id: ConversationId) {
        dao.deleteConversation(id.value)
    }

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        dao.observeMessages(conversationId.value).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeRecentMessages(conversationId: ConversationId, limit: Int): Flow<List<Message>> =
        dao.observeRecentMessages(conversationId.value, limit).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeMessageCount(conversationId: ConversationId): Flow<Int> =
        dao.observeMessageCount(conversationId.value)

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        dao.getMessages(conversationId.value).map { it.toDomain() }

    override suspend fun saveMessage(message: Message) {
        dao.saveMessageAndTouch(message.toEntity(), message.updatedAt.toEpochMilli())
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        dao.deleteMessages(conversationId.value)
        dao.touchConversation(conversationId.value, clock.millis())
    }

    override suspend fun deleteMessage(messageId: MessageId) {
        dao.deleteMessage(messageId.value)
    }

    override fun observeConversationsWithPreview(): Flow<List<ConversationPreview>> =
        dao.observeConversationsWithPreview().map { entities ->
            entities.map { it.toPreview() }
        }

}
