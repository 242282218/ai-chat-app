package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.MessageSearchResult
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomConversationRepository(
    private val dao: ConversationDao,
    private val clock: Clock,
) : ConversationRepository {
    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> =
        dao.observeConversations(includeArchived).map { entities ->
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

    override suspend fun archiveConversation(id: ConversationId) {
        dao.archiveConversation(id.value, clock.millis())
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
        dao.upsertMessage(message.toEntity())
        dao.touchConversation(message.conversationId.value, message.updatedAt.toEpochMilli())
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        dao.deleteMessages(conversationId.value)
        dao.touchConversation(conversationId.value, clock.millis())
    }

    override fun searchMessages(query: String, limit: Int): Flow<List<MessageSearchResult>> {
        val normalized = query.toFtsQuery() ?: return flowOf(emptyList())
        return dao.searchMessages(normalized, limit).map { entities ->
            entities.map { entity ->
                MessageSearchResult(
                    conversation = entity.conversation.toDomain(),
                    message = entity.message.toDomain(),
                )
            }
        }
    }

    private fun String.toFtsQuery(): String? {
        val tokens = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " ") { token ->
            "\"${token.replace("\"", "\"\"")}\""
        }
    }
}
