package com.aichat.workbench.app.di

import androidx.room.Room
import com.aichat.workbench.data.local.AiChatDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AiChatDatabase::class.java,
            "ai_chat.db",
        ).addMigrations(
            AiChatDatabase.MIGRATION_1_2,
            AiChatDatabase.MIGRATION_2_3,
            AiChatDatabase.MIGRATION_3_4,
            AiChatDatabase.MIGRATION_4_5,
            AiChatDatabase.MIGRATION_5_6,
            AiChatDatabase.MIGRATION_6_7,
            AiChatDatabase.MIGRATION_7_8,
            AiChatDatabase.MIGRATION_8_9,
            AiChatDatabase.MIGRATION_9_10,
            AiChatDatabase.MIGRATION_10_11,
            AiChatDatabase.MIGRATION_11_12,
            AiChatDatabase.MIGRATION_12_13,
            AiChatDatabase.MIGRATION_13_14,
            AiChatDatabase.MIGRATION_14_15,
            AiChatDatabase.MIGRATION_15_16,
            AiChatDatabase.MIGRATION_16_17,
            AiChatDatabase.MIGRATION_17_18,
        ).build()
    }
    single { get<AiChatDatabase>().conversationDao() }
    single { get<AiChatDatabase>().imageGenerationDao() }
    single { get<AiChatDatabase>().modelRolePreferenceDao() }
    single { get<AiChatDatabase>().providerConfigDao() }
}
