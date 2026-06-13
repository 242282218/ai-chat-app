package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ConversationWithPreview
import com.aichat.workbench.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("UPDATE conversations SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        )
        ORDER BY created_at ASC, id ASC
        """,
    )
    fun observeRecentMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    fun observeMessageCount(conversationId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    /**
     * Atomically upserts a message and updates the conversation's timestamp.
     * Ensures conversation ordering stays consistent even if one step fails.
     */
    @Transaction
    suspend fun saveMessageAndTouch(message: MessageEntity, updatedAt: Long) {
        upsertMessage(message)
        touchConversation(message.conversationId, updatedAt)
    }

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query(
        """
        SELECT c.id, c.title, c.created_at, c.updated_at, c.default_provider_id,
               m.content AS last_message_content, m.role AS last_message_role
        FROM conversations c
        LEFT JOIN (
            SELECT conversation_id, content, role
            FROM messages
            WHERE id IN (
                SELECT id FROM messages AS m2
                WHERE m2.conversation_id = messages.conversation_id
                ORDER BY m2.created_at DESC, m2.id DESC
                LIMIT 1
            )
        ) m ON m.conversation_id = c.id
        ORDER BY c.updated_at DESC
        """,
    )
    fun observeConversationsWithPreview(): Flow<List<ConversationWithPreview>>

}
