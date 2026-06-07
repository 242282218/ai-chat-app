package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
            arrayOf<Any?>("provider-1:Tool", "provider-1", "Tool", "gpt-tool", 1L, 1L),
        )
        val cursor = migrated.query("SELECT model FROM model_role_preferences WHERE provider_id = 'provider-1' AND role = 'Tool'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("gpt-tool", it.getString(0))
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
}
