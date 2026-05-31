package com.aichat.workbench.app

import android.app.Application
import androidx.room.Room
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.crypto.AndroidSecretStore
import com.aichat.workbench.data.image.AndroidImageStorage
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomImageGenerationRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.repository.RoomToolInvocationRepository
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
import com.aichat.workbench.provider.image.OpenAiImageGenerationProvider
import com.aichat.workbench.provider.openai.OpenAiChatProvider
import com.aichat.workbench.tool.gateway.GatewayClient
import java.time.Clock

object AppGraph {
    val dispatchers = AppDispatchers()

    lateinit var application: Application
        private set

    val database: AiChatDatabase by lazy {
        Room.databaseBuilder(
            application,
            AiChatDatabase::class.java,
            "ai_chat.db",
        ).addMigrations(
            AiChatDatabase.MIGRATION_1_2,
            AiChatDatabase.MIGRATION_2_3,
            AiChatDatabase.MIGRATION_3_4,
        ).build()
    }

    val clock: Clock = Clock.systemUTC()

    val conversationRepository: RoomConversationRepository by lazy {
        RoomConversationRepository(
            dao = database.conversationDao(),
            clock = clock,
        )
    }

    val providerConfigRepository: RoomProviderConfigRepository by lazy {
        RoomProviderConfigRepository(
            providerDao = database.providerConfigDao(),
            modelPreferenceDao = database.modelPreferenceDao(),
            secretStore = AndroidSecretStore(application),
            clock = clock,
        )
    }

    val promptPresetRepository: RoomPromptPresetRepository by lazy {
        RoomPromptPresetRepository(database.promptPresetDao())
    }

    val imageGenerationRepository: RoomImageGenerationRepository by lazy {
        RoomImageGenerationRepository(database.imageGenerationDao())
    }

    val toolInvocationRepository: RoomToolInvocationRepository by lazy {
        RoomToolInvocationRepository(database.toolInvocationDao())
    }

    val imageStorage: AndroidImageStorage by lazy {
        AndroidImageStorage(application)
    }

    val gatewaySettingsRepository: GatewaySettingsRepository by lazy {
        GatewaySettingsRepository(application)
    }

    val openAiChatProvider: OpenAiChatProvider by lazy {
        OpenAiChatProvider()
    }

    val providerConnectionTester: ProviderConnectionTester by lazy {
        ProviderConnectionTester()
    }

    val compatibleChatProvider: OpenAiCompatibleChatProvider by lazy {
        OpenAiCompatibleChatProvider()
    }

    val imageGenerationProvider: OpenAiImageGenerationProvider by lazy {
        OpenAiImageGenerationProvider()
    }

    val gatewayClient: GatewayClient by lazy {
        GatewayClient()
    }

    val backupService: AppBackupService by lazy {
        AppBackupService(
            database = database,
            providerRepository = providerConfigRepository,
            conversationRepository = conversationRepository,
            imageStorage = imageStorage,
            clock = clock,
        )
    }

    fun initialize(application: Application) {
        this.application = application
    }
}
