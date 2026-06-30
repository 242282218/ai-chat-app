package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AiChatDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration4To5_convertsProviderTypeNamesToStableValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("provider-type-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO provider_configs (
                    id, name, type, base_url, api_key_ref, headers_json, models_json,
                    default_model, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "openai",
                    "OpenAI",
                    "OpenAI",
                    "https://api.openai.com/v1",
                    null,
                    "{}",
                    "[]",
                    null,
                    1,
                    1L,
                    1L,
                ),
            )
            execSQL(
                """
                INSERT INTO provider_configs (
                    id, name, type, base_url, api_key_ref, headers_json, models_json,
                    default_model, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "compatible",
                    "Compatible",
                    "OpenAICompatible",
                    "https://example.test/v1",
                    null,
                    "{}",
                    "[]",
                    null,
                    1,
                    1L,
                    1L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AiChatDatabase.MIGRATION_4_5,
        )
        val values = mutableMapOf<String, String>()
        val cursor = migrated.query("SELECT id, type FROM provider_configs")
        cursor.use {
            while (it.moveToNext()) {
                values[it.getString(0)] = it.getString(1)
            }
        }
        migrated.close()

        assertEquals("openai", values["openai"])
        assertEquals("openai_compatible", values["compatible"])
    }

    @Test
    fun migration5To6_addsToolCallColumnsWithDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("message-tool-call-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    1L,
                    null,
                    null,
                    "{}",
                    null,
                    0,
                    0,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, tool_call_id, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "Hello",
                    """[{"type":"text","text":"Hello"}]""",
                    null,
                    null,
                    "Completed",
                    null,
                    1L,
                    1L,
                    null,
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AiChatDatabase.MIGRATION_5_6,
        )
        val cursor = migrated.query("SELECT tool_calls, tool_result FROM messages WHERE id = 'message-1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("[]", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }

    @Test
    fun migration6To7_createsMessageFtsIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("message-fts-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    1L,
                    null,
                    null,
                    "{}",
                    null,
                    0,
                    0,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, tool_call_id, parent_message_id,
                    tool_calls, tool_result
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "needle content",
                    """[{"type":"text","text":"needle content"}]""",
                    null,
                    null,
                    "Completed",
                    null,
                    1L,
                    1L,
                    null,
                    null,
                    "[]",
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            AiChatDatabase.MIGRATION_6_7,
        )
        val cursor = migrated.query(
            """
            SELECT m.content FROM messages m
            JOIN messages_fts fts ON fts.rowid = m.rowid
            WHERE messages_fts MATCH 'needle'
            """.trimIndent(),
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("needle content", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration7To8_createsModelRolePreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("model-role-preferences-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 7).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            AiChatDatabase.MIGRATION_7_8,
            AiChatDatabase.MIGRATION_8_9,
            AiChatDatabase.MIGRATION_9_10,
        )
        migrated.execSQL(
            """
            INSERT INTO model_role_preferences (
                id, provider_id, role, model, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("provider-1:Image", "provider-1", "Image", "gpt-image", 1L, 1L),
        )
        val cursor = migrated.query("SELECT model FROM model_role_preferences WHERE provider_id = 'provider-1' AND role = 'Image'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("gpt-image", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration8To9_addsToolInvocationRawPayloadAndTimingColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("tool-invocation-metadata-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 8).apply {
            execSQL(
                """
                INSERT INTO tool_invocations (
                    id, conversation_id, tool_name, permission_level, input_summary,
                    output_json, status, started_at, finished_at, error_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "tool-call-1",
                    null,
                    "web_search",
                    "Network",
                    "query: AI news",
                    """{"type":"json","value":"{\"query\":\"AI news\"}"}""",
                    "Completed",
                    1L,
                    2L,
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            AiChatDatabase.MIGRATION_8_9,
            AiChatDatabase.MIGRATION_9_10,
        )
        val cursor = migrated.query(
            """
            SELECT raw_input_json, raw_output_json, duration_ms, canceled_at
            FROM tool_invocations
            WHERE id = 'tool-call-1'
            """.trimIndent(),
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
        }
        migrated.close()
    }

    @Test
    fun migration10To11_createsMemoryItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("memory-items-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 10).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            11,
            true,
            AiChatDatabase.MIGRATION_10_11,
        )
        migrated.execSQL(
            """
            INSERT INTO memory_items (
                id, kind, content, source_conversation_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("memory-1", "UserFact", "用户偏好 Kotlin。", null, 1L, 1L),
        )
        val cursor = migrated.query("SELECT kind, content FROM memory_items WHERE id = 'memory-1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("UserFact", it.getString(0))
            assertEquals("用户偏好 Kotlin。", it.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migration11To12_removesRemovedFeatureStorageAndKeepsChatHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("chat-image-only-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 11).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    1L,
                    null,
                    null,
                    "{}",
                    null,
                    0,
                    0,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, tool_call_id, parent_message_id,
                    tool_calls, tool_result
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "Tool",
                    "legacy image result",
                    """[{"type":"image","uri":"data:image/png;base64,AAAA","mimeType":"image/png"}]""",
                    null,
                    null,
                    "Completed",
                    null,
                    1L,
                    1L,
                    "call-1",
                    null,
                    """[{"id":"call-1","name":"legacy","arguments":"{}"}]""",
                    """{"prompt":"legacy"}""",
                ),
            )
            execSQL(
                """
                INSERT INTO prompt_presets (
                    id, name, description, system_prompt, default_model, default_tool_names_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("prompt-1", "Old", null, "sys", null, "[]", 1L, 1L),
            )
            execSQL(
                """
                INSERT INTO tool_invocations (
                    id, conversation_id, tool_name, permission_level, input_summary,
                    output_json, status, started_at, finished_at, error_json,
                    raw_input_json, raw_output_json, duration_ms, canceled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "tool-call-1",
                    null,
                    "legacy_tool",
                    "Network",
                    "input",
                    "{}",
                    "Completed",
                    1L,
                    2L,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO memory_items (
                    id, kind, content, source_conversation_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("memory-1", "UserFact", "legacy memory", null, 1L, 1L),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            AiChatDatabase.MIGRATION_11_12,
        )

        val roleCursor = migrated.query("SELECT role, content_parts_json FROM messages WHERE id = 'message-1'")
        roleCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Assistant", it.getString(0))
            assertTrue(it.getString(1).contains("data:image/png;base64,AAAA"))
        }
        assertFalse(migrated.tableExists("prompt_presets"))
        assertFalse(migrated.tableExists("tool_invocations"))
        assertFalse(migrated.tableExists("memory_items"))
        assertFalse(migrated.columnExists("messages", "tool_call_id"))
        assertFalse(migrated.columnExists("messages", "tool_calls"))
        assertFalse(migrated.columnExists("messages", "tool_result"))

        val searchCursor = migrated.query(
            """
            SELECT m.content FROM messages m
            JOIN messages_fts fts ON fts.rowid = m.rowid
            WHERE messages_fts MATCH 'legacy'
            """.trimIndent(),
        )
        searchCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("legacy image result", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration12To13_removesSensitiveFlagAndKeepsConversationData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-sensitive-flag-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 12).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Temporary chat",
                    1L,
                    2L,
                    null,
                    "gpt-test",
                    """{"temperature":0.4}""",
                    "Be direct",
                    1,
                    1,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "needle text",
                    """[{"type":"text","text":"needle text"}]""",
                    null,
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    null,
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            13,
            true,
            AiChatDatabase.MIGRATION_12_13,
        )

        assertFalse(migrated.columnExists("conversations", "is_sensitive"))
        val conversationCursor = migrated.query(
            "SELECT title, default_model, model_parameters_json, system_prompt, is_temporary FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Temporary chat", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
            assertEquals("""{"temperature":0.4}""", it.getString(2))
            assertEquals("Be direct", it.getString(3))
            assertEquals(1, it.getInt(4))
        }
        val messageCursor = migrated.query("SELECT content FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("needle text", it.getString(0))
        }
        val imageCursor = migrated.query("SELECT prompt, original_path FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("/tmp/original.png", it.getString(1))
        }
        val searchCursor = migrated.query(
            """
            SELECT m.content FROM messages m
            JOIN messages_fts fts ON fts.rowid = m.rowid
            WHERE messages_fts MATCH 'needle'
            """.trimIndent(),
        )
        searchCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("needle text", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration13To14_removesArchiveStorageAndKeepsConversationData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-archive-storage-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 13).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    2L,
                    null,
                    "gpt-test",
                    """{"topP":0.8}""",
                    "Be direct",
                    0,
                    9L,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "archive needle",
                    """[{"type":"text","text":"archive needle"}]""",
                    null,
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    null,
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            14,
            true,
            AiChatDatabase.MIGRATION_13_14,
        )

        assertFalse(migrated.columnExists("conversations", "archived_at"))
        val conversationCursor = migrated.query(
            "SELECT title, default_model, model_parameters_json, system_prompt, is_temporary FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Chat", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
            assertEquals("""{"topP":0.8}""", it.getString(2))
            assertEquals("Be direct", it.getString(3))
            assertEquals(0, it.getInt(4))
        }
        val messageCursor = migrated.query("SELECT content FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("archive needle", it.getString(0))
        }
        val imageCursor = migrated.query("SELECT prompt, thumbnail_path FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("/tmp/thumb.png", it.getString(1))
        }
        val searchCursor = migrated.query(
            """
            SELECT m.content FROM messages m
            JOIN messages_fts fts ON fts.rowid = m.rowid
            WHERE messages_fts MATCH 'archive'
            """.trimIndent(),
        )
        searchCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("archive needle", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration14To15_removesMessageFtsAndKeepsChatHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("message-fts-removal-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 14).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    2L,
                    null,
                    "gpt-test",
                    "{}",
                    null,
                    0,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "kept message",
                    """[{"type":"text","text":"kept message"}]""",
                    null,
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            15,
            true,
            AiChatDatabase.MIGRATION_14_15,
        )

        assertFalse(migrated.tableExists("messages_fts"))
        val messageCursor = migrated.query("SELECT content FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("kept message", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration15To16_removesTemporaryFlagAndKeepsConversationData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-temporary-flag-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 15).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Temporary",
                    1L,
                    2L,
                    null,
                    "gpt-test",
                    """{"maxTokens":256}""",
                    "Be direct",
                    1,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "kept message",
                    """[{"type":"text","text":"kept message"}]""",
                    null,
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    null,
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            16,
            true,
            AiChatDatabase.MIGRATION_15_16,
        )

        assertFalse(migrated.columnExists("conversations", "is_temporary"))
        val conversationCursor = migrated.query(
            "SELECT title, default_model, model_parameters_json, system_prompt FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Temporary", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
            assertEquals("""{"maxTokens":256}""", it.getString(2))
            assertEquals("Be direct", it.getString(3))
        }
        val messageCursor = migrated.query("SELECT content FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("kept message", it.getString(0))
        }
        val imageCursor = migrated.query("SELECT prompt, original_path FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("/tmp/original.png", it.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migration16To17_removesUnusedModelPreferenceStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("model-preference-removal-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 16).apply {
            execSQL(
                """
                INSERT INTO provider_configs (
                    id, name, type, base_url, api_key_ref, headers_json, models_json,
                    default_model, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "provider-1",
                    "OpenAI",
                    "openai",
                    "https://api.openai.com/v1",
                    null,
                    "{}",
                    "[]",
                    "gpt-test",
                    1,
                    1L,
                    2L,
                ),
            )
            execSQL(
                """
                INSERT INTO model_preferences (
                    id, provider_id, model, is_favorite, is_default, capability_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "provider-1:gpt-test",
                    "provider-1",
                    "gpt-test",
                    1,
                    1,
                    null,
                    1L,
                    2L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            17,
            true,
            AiChatDatabase.MIGRATION_16_17,
        )

        assertFalse(migrated.tableExists("model_preferences"))
        val providerCursor = migrated.query("SELECT default_model FROM provider_configs WHERE id = 'provider-1'")
        providerCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("gpt-test", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration17To18_removesConversationModelControlsAndKeepsChatHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-model-controls-removal-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 17).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    2L,
                    "provider-1",
                    "gpt-test",
                    """{"temperature":0.4,"topP":0.8,"maxTokens":256}""",
                    "Be direct",
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "kept message",
                    """[{"type":"text","text":"kept message"}]""",
                    "provider-1",
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    "provider-1",
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            18,
            true,
            AiChatDatabase.MIGRATION_17_18,
        )

        assertFalse(migrated.columnExists("conversations", "default_model"))
        assertFalse(migrated.columnExists("conversations", "model_parameters_json"))
        assertFalse(migrated.columnExists("conversations", "system_prompt"))
        val conversationCursor = migrated.query(
            "SELECT title, default_provider_id FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Chat", it.getString(0))
            assertEquals("provider-1", it.getString(1))
        }
        val messageCursor = migrated.query("SELECT content, model FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("kept message", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
        }
        val imageCursor = migrated.query("SELECT prompt, model FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("gpt-image", it.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migration12To18_runsFullConversationSchemaChain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-full-chain-12-18").absolutePath
        migrationHelper.createDatabase(databaseName, 12).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Full chain",
                    1L,
                    2L,
                    "provider-1",
                    "gpt-test",
                    """{"temperature":0.4}""",
                    "Be direct",
                    1,
                    1,
                    9L,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "full chain message",
                    """[{"type":"text","text":"full chain message"}]""",
                    "provider-1",
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    "provider-1",
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            18,
            true,
            *migrations12To18(),
        )

        assertFalse(migrated.columnExists("conversations", "is_sensitive"))
        assertFalse(migrated.columnExists("conversations", "is_temporary"))
        assertFalse(migrated.columnExists("conversations", "archived_at"))
        assertFalse(migrated.columnExists("conversations", "default_model"))
        assertFalse(migrated.columnExists("conversations", "model_parameters_json"))
        assertFalse(migrated.columnExists("conversations", "system_prompt"))
        val conversationCursor = migrated.query(
            "SELECT title, default_provider_id FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Full chain", it.getString(0))
            assertEquals("provider-1", it.getString(1))
        }
        val messageCursor = migrated.query("SELECT content, model FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("full chain message", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
        }
        val imageCursor = migrated.query("SELECT prompt, model FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("gpt-image", it.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migration15To18_runsFullConversationSchemaChain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("conversation-full-chain-15-18").absolutePath
        migrationHelper.createDatabase(databaseName, 15).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Full chain 15",
                    1L,
                    2L,
                    "provider-1",
                    "gpt-test",
                    """{"maxTokens":256}""",
                    "Be direct",
                    1,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "Assistant",
                    "full chain 15 message",
                    """[{"type":"text","text":"full chain 15 message"}]""",
                    "provider-1",
                    "gpt-test",
                    "Completed",
                    null,
                    3L,
                    4L,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO image_generations (
                    id, conversation_id, prompt, provider_id, model, size, quality, count,
                    original_path, thumbnail_path, status, error_summary, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "image-1",
                    "conversation-1",
                    "draw",
                    "provider-1",
                    "gpt-image",
                    "1024x1024",
                    null,
                    1,
                    "/tmp/original.png",
                    "/tmp/thumb.png",
                    "Completed",
                    null,
                    5L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            18,
            true,
            AiChatDatabase.MIGRATION_15_16,
            AiChatDatabase.MIGRATION_16_17,
            AiChatDatabase.MIGRATION_17_18,
        )

        assertFalse(migrated.columnExists("conversations", "is_temporary"))
        assertFalse(migrated.columnExists("conversations", "default_model"))
        assertFalse(migrated.columnExists("conversations", "model_parameters_json"))
        assertFalse(migrated.columnExists("conversations", "system_prompt"))
        val conversationCursor = migrated.query(
            "SELECT title, default_provider_id FROM conversations WHERE id = 'conversation-1'",
        )
        conversationCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Full chain 15", it.getString(0))
            assertEquals("provider-1", it.getString(1))
        }
        val messageCursor = migrated.query("SELECT content, model FROM messages WHERE id = 'message-1'")
        messageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("full chain 15 message", it.getString(0))
            assertEquals("gpt-test", it.getString(1))
        }
        val imageCursor = migrated.query("SELECT prompt, model FROM image_generations WHERE id = 'image-1'")
        imageCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("draw", it.getString(0))
            assertEquals("gpt-image", it.getString(1))
        }
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableExists(tableName: String): Boolean =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { it.moveToFirst() }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean =
        query("PRAGMA table_info($tableName)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == columnName) return true
            }
            false
        }

    private fun migrations12To18() = arrayOf(
        AiChatDatabase.MIGRATION_12_13,
        AiChatDatabase.MIGRATION_13_14,
        AiChatDatabase.MIGRATION_14_15,
        AiChatDatabase.MIGRATION_15_16,
        AiChatDatabase.MIGRATION_16_17,
        AiChatDatabase.MIGRATION_17_18,
    )
}
