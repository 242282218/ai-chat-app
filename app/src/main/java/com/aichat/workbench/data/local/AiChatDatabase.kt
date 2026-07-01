package com.aichat.workbench.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aichat.workbench.data.local.dao.ConversationDao
import com.aichat.workbench.data.local.dao.ImageGenerationDao
import com.aichat.workbench.data.local.dao.ModelRolePreferenceDao
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.local.migration.AiChatMigrations
import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity
import com.aichat.workbench.data.local.entity.ProviderConfigEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
        ModelRolePreferenceEntity::class,
        ImageGenerationEntity::class,
    ],
    version = 18,
    exportSchema = true,
)
abstract class AiChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun modelRolePreferenceDao(): ModelRolePreferenceDao

    abstract fun providerConfigDao(): ProviderConfigDao

    abstract fun imageGenerationDao(): ImageGenerationDao

    companion object {
        val MIGRATION_1_2 = AiChatMigrations.MIGRATION_1_2
        val MIGRATION_2_3 = AiChatMigrations.MIGRATION_2_3
        val MIGRATION_3_4 = AiChatMigrations.MIGRATION_3_4
        val MIGRATION_4_5 = AiChatMigrations.MIGRATION_4_5
        val MIGRATION_5_6 = AiChatMigrations.MIGRATION_5_6
        val MIGRATION_6_7 = AiChatMigrations.MIGRATION_6_7
        val MIGRATION_7_8 = AiChatMigrations.MIGRATION_7_8
        val MIGRATION_8_9 = AiChatMigrations.MIGRATION_8_9
        val MIGRATION_9_10 = AiChatMigrations.MIGRATION_9_10
        val MIGRATION_10_11 = AiChatMigrations.MIGRATION_10_11
        val MIGRATION_11_12 = AiChatMigrations.MIGRATION_11_12
        val MIGRATION_12_13 = AiChatMigrations.MIGRATION_12_13
        val MIGRATION_13_14 = AiChatMigrations.MIGRATION_13_14
        val MIGRATION_14_15 = AiChatMigrations.MIGRATION_14_15
        val MIGRATION_15_16 = AiChatMigrations.MIGRATION_15_16
        val MIGRATION_16_17 = AiChatMigrations.MIGRATION_16_17
        val MIGRATION_17_18 = AiChatMigrations.MIGRATION_17_18
    }
}
