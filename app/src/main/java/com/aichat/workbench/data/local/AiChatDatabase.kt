package com.aichat.workbench.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.local.dao.ImageGenerationDao
import com.aichat.workbench.data.local.dao.ModelPreferenceDao
import com.aichat.workbench.data.local.dao.PromptPresetDao
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.local.dao.ToolInvocationDao
import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.local.entity.ModelPreferenceEntity
import com.aichat.workbench.data.local.entity.PromptPresetEntity
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import com.aichat.workbench.data.local.entity.ToolInvocationEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
        PromptPresetEntity::class,
        ModelPreferenceEntity::class,
        ToolInvocationEntity::class,
        ImageGenerationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AiChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun promptPresetDao(): PromptPresetDao

    abstract fun modelPreferenceDao(): ModelPreferenceDao

    abstract fun providerConfigDao(): ProviderConfigDao

    abstract fun imageGenerationDao(): ImageGenerationDao

    abstract fun toolInvocationDao(): ToolInvocationDao

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
    }
}
