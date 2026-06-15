package com.aichat.workbench.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.recreateConversationsWithSchema(
    conversationColumns: String,
    conversationSchema: String,
    conversationIndices: List<String> = emptyList(),
) {
    execSQL(
        """
        CREATE TEMP TABLE conversations_backup AS
        SELECT $conversationColumns
        FROM conversations
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TEMP TABLE messages_backup AS
        SELECT
            id, conversation_id, role, content, content_parts_json, provider_id, model,
            status, error_summary, created_at, updated_at, parent_message_id
        FROM messages
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TEMP TABLE image_generations_backup AS
        SELECT
            id, conversation_id, prompt, provider_id, model, size, quality, count,
            original_path, thumbnail_path, status, error_summary, created_at
        FROM image_generations
        """.trimIndent(),
    )
    execSQL("DELETE FROM messages")
    execSQL("DELETE FROM image_generations")
    execSQL("DROP TABLE conversations")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS conversations (
            $conversationSchema
        )
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO conversations ($conversationColumns)
        SELECT $conversationColumns
        FROM conversations_backup
        """.trimIndent(),
    )
    conversationIndices.forEach { index ->
        execSQL(index)
    }
    execSQL(
        """
        INSERT INTO image_generations (
            id, conversation_id, prompt, provider_id, model, size, quality, count,
            original_path, thumbnail_path, status, error_summary, created_at
        )
        SELECT
            id, conversation_id, prompt, provider_id, model, size, quality, count,
            original_path, thumbnail_path, status, error_summary, created_at
        FROM image_generations_backup
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO messages (
            id, conversation_id, role, content, content_parts_json, provider_id, model,
            status, error_summary, created_at, updated_at, parent_message_id
        )
        SELECT
            id, conversation_id, role, content, content_parts_json, provider_id, model,
            status, error_summary, created_at, updated_at, parent_message_id
        FROM messages_backup
        """.trimIndent(),
    )
}
