package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toPreview
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.exception.DatabaseException
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomConversationRepository(
    private val dao: ConversationDao,
    private val clock: Clock,
) : ConversationRepository {
    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { error -> throw DatabaseException("获取对话列表失败", error) }

    override suspend fun getConversation(id: ConversationId): Conversation? =
        try {
            dao.getConversation(id.value)?.toDomain()
        } catch (error: Exception) {
            throw DatabaseException("获取对话失败: ${id.value}", error)
        }

    override suspend fun saveConversation(conversation: Conversation) {
        try {
            dao.upsertConversation(conversation.toEntity())
        } catch (error: Exception) {
            throw DatabaseException("保存对话失败", error)
        }
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        try {
            dao.renameConversation(id.value, title.trim(), clock.millis())
        } catch (error: Exception) {
            throw DatabaseException("重命名对话失败", error)
        }
    }

    override suspend fun deleteConversation(id: ConversationId) {
        try {
            dao.deleteConversation(id.value)
        } catch (error: Exception) {
            throw DatabaseException("删除对话失败", error)
        }
    }

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        dao.observeMessages(conversationId.value)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { error -> throw DatabaseException("获取消息列表失败", error) }

    override fun observeRecentMessages(conversationId: ConversationId, limit: Int): Flow<List<Message>> =
        dao.observeRecentMessages(conversationId.value, limit)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { error -> throw DatabaseException("获取最近消息失败", error) }

    override fun observeMessageCount(conversationId: ConversationId): Flow<Int> =
        dao.observeMessageCount(conversationId.value)
            .catch { error -> throw DatabaseException("获取消息数量失败", error) }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        try {
            dao.getMessages(conversationId.value).map { it.toDomain() }
        } catch (error: Exception) {
            throw DatabaseException("获取对话消息失败", error)
        }

    override suspend fun saveMessage(message: Message) {
        try {
            dao.saveMessageAndTouch(message.toEntity(), message.updatedAt.toEpochMilli())
        } catch (error: Exception) {
            throw DatabaseException("保存消息失败", error)
        }
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        try {
            dao.deleteMessages(conversationId.value)
            dao.touchConversation(conversationId.value, clock.millis())
        } catch (error: Exception) {
            throw DatabaseException("删除消息失败", error)
        }
    }

    override suspend fun deleteMessage(messageId: MessageId) {
        try {
            dao.deleteMessage(messageId.value)
        } catch (error: Exception) {
            throw DatabaseException("删除单条消息失败", error)
        }
    }

    override suspend fun deleteMessageAndFollowing(message: Message) {
        try {
            dao.deleteMessageAndFollowing(
                conversationId = message.conversationId.value,
                createdAt = message.createdAt.toEpochMilli(),
                id = message.id.value,
            )
            dao.touchConversation(message.conversationId.value, clock.millis())
        } catch (error: Exception) {
            throw DatabaseException("删除消息分支失败", error)
        }
    }

    override fun observeConversationsWithPreview(): Flow<List<ConversationPreview>> =
        dao.observeConversationsWithPreview()
            .map { entities -> entities.map { it.toPreview() } }
            .catch { error -> throw DatabaseException("获取对话预览失败", error) }

}
