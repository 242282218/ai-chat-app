package com.aichat.workbench.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.local.dao.ImageGenerationDao
import com.aichat.workbench.data.local.dao.MemoryDao
import com.aichat.workbench.data.local.dao.ModelPreferenceDao
import com.aichat.workbench.data.local.dao.ModelRolePreferenceDao
import com.aichat.workbench.data.local.dao.PromptPresetDao
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.local.dao.ToolInvocationDao
import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import com.aichat.workbench.data.local.entity.MemoryItemEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.local.entity.MessageFts
import com.aichat.workbench.data.local.entity.ModelPreferenceEntity
import com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity
import com.aichat.workbench.data.local.entity.PromptPresetEntity
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import com.aichat.workbench.data.local.entity.ToolInvocationEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageFts::class,
        ProviderConfigEntity::class,
        PromptPresetEntity::class,
        ModelPreferenceEntity::class,
        ModelRolePreferenceEntity::class,
        ToolInvocationEntity::class,
        ImageGenerationEntity::class,
        MemoryItemEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AiChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun promptPresetDao(): PromptPresetDao

    abstract fun modelPreferenceDao(): ModelPreferenceDao

    abstract fun modelRolePreferenceDao(): ModelRolePreferenceDao

    abstract fun providerConfigDao(): ProviderConfigDao

    abstract fun imageGenerationDao(): ImageGenerationDao

    abstract fun toolInvocationDao(): ToolInvocationDao

    abstract fun memoryDao(): MemoryDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN is_sensitive INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_generations ADD COLUMN size TEXT")
                db.execSQL("ALTER TABLE image_generations ADD COLUMN quality TEXT")
                db.execSQL(
                    "ALTER TABLE image_generations ADD COLUMN count INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("ALTER TABLE image_generations ADD COLUMN error_summary TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tool_invocations_new (
                        id TEXT NOT NULL,
                        conversation_id TEXT,
                        tool_name TEXT NOT NULL,
                        permission_level TEXT NOT NULL,
                        input_summary TEXT NOT NULL,
                        output_json TEXT NOT NULL,
                        status TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        finished_at INTEGER,
                        error_json TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO tool_invocations_new (
                        id, conversation_id, tool_name, permission_level, input_summary,
                        output_json, status, started_at, finished_at, error_json
                    )
                    SELECT
                        id, conversation_id, tool_name, permission_level, input_summary,
                        output_json, status, started_at, finished_at, error_json
                    FROM tool_invocations
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE tool_invocations")
                db.execSQL("ALTER TABLE tool_invocations_new RENAME TO tool_invocations")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tool_invocations_conversation_id_started_at ON tool_invocations(conversation_id, started_at)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tool_invocations_status ON tool_invocations(status)",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE provider_configs SET type = 'openai' WHERE type = 'OpenAI'")
                db.execSQL(
                    "UPDATE provider_configs SET type = 'openai_compatible' WHERE type = 'OpenAICompatible'",
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN tool_calls TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE messages ADD COLUMN tool_result TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING FTS4(content, content=`messages`)")
                db.execSQL("INSERT INTO messages_fts(rowid, content) SELECT rowid, content FROM messages")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_fts_ai AFTER INSERT ON messages BEGIN
                        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_fts_ad AFTER DELETE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content)
                        VALUES('delete', old.rowid, old.content);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_fts_au AFTER UPDATE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content)
                        VALUES('delete', old.rowid, old.content);
                        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
                    END
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS model_role_preferences (
                        id TEXT NOT NULL,
                        provider_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        model TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_model_role_preferences_provider_id_role ON model_role_preferences(provider_id, role)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_model_role_preferences_provider_id ON model_role_preferences(provider_id)",
                )
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tool_invocations ADD COLUMN raw_input_json TEXT")
                db.execSQL("ALTER TABLE tool_invocations ADD COLUMN raw_output_json TEXT")
                db.execSQL("ALTER TABLE tool_invocations ADD COLUMN duration_ms INTEGER")
                db.execSQL("ALTER TABLE tool_invocations ADD COLUMN canceled_at INTEGER")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep persisted tool status names aligned with the domain enum.
                db.execSQL("UPDATE tool_invocations SET status = 'Cancelled' WHERE status = 'Canceled'")

                // Identify and log orphaned model_role_preferences before cleanup
                val orphanedCount = db.query(
                    """
                    SELECT COUNT(*) FROM model_role_preferences
                    WHERE provider_id NOT IN (SELECT id FROM provider_configs)
                    """.trimIndent()
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                if (orphanedCount > 0) {
                    android.util.Log.w(
                        "AiChatDatabase",
                        "Migration 9->10: Found $orphanedCount orphaned model_role_preferences rows. " +
                        "These will be excluded from the new table (provider was deleted in earlier version)."
                    )
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS model_role_preferences_new (
                        id TEXT NOT NULL,
                        provider_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        model TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(provider_id) REFERENCES provider_configs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO model_role_preferences_new (
                        id, provider_id, role, model, created_at, updated_at
                    )
                    SELECT
                        preference.id,
                        preference.provider_id,
                        preference.role,
                        preference.model,
                        preference.created_at,
                        preference.updated_at
                    FROM model_role_preferences AS preference
                    INNER JOIN provider_configs AS provider ON provider.id = preference.provider_id
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE model_role_preferences")
                db.execSQL("ALTER TABLE model_role_preferences_new RENAME TO model_role_preferences")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_model_role_preferences_provider_id_role ON model_role_preferences(provider_id, role)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_model_role_preferences_provider_id ON model_role_preferences(provider_id)",
                )
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_items (
                        id TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        content TEXT NOT NULL,
                        source_conversation_id TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(source_conversation_id) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_kind ON memory_items(kind)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_items_source_conversation_id ON memory_items(source_conversation_id)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_updated_at ON memory_items(updated_at)")
            }
        }
    }
}
