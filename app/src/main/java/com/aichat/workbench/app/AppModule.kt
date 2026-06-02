package com.aichat.workbench.app

import androidx.room.Room
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.crypto.AndroidSecretStore
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.image.AndroidImageStorage
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomImageGenerationRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.repository.RoomToolInvocationRepository
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.feature.chat.ChatViewModel
import com.aichat.workbench.feature.chat.ConversationCompactor
import com.aichat.workbench.feature.chat.ConversationManager
import com.aichat.workbench.feature.chat.GenerationController
import com.aichat.workbench.feature.chat.ToolExecutor
import com.aichat.workbench.feature.home.HomeViewModel
import com.aichat.workbench.feature.image.ImageGenerationViewModel
import com.aichat.workbench.feature.settings.DataSettingsViewModel
import com.aichat.workbench.feature.tools.ToolsViewModel
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.OpenAiImageGenerationProvider
import com.aichat.workbench.provider.http.WorkbenchHttpClients
import com.aichat.workbench.provider.openai.OpenAiChatProvider
import com.aichat.workbench.tool.gateway.GatewayClient
import java.time.Clock
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule: Module = module {
    single { AppDispatchers() }
    single { Clock.systemUTC() }
    single<OkHttpClient>(named("jsonHttpClient")) { WorkbenchHttpClients.json() }
    single<OkHttpClient>(named("streamingHttpClient")) { WorkbenchHttpClients.streaming() }
    single<OkHttpClient>(named("longRunningHttpClient")) { WorkbenchHttpClients.longRunning() }
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
        ).build()
    }
    single<SecretStore> { AndroidSecretStore(androidContext()) }
    single<ConversationRepository> {
        RoomConversationRepository(
            dao = get<AiChatDatabase>().conversationDao(),
            clock = get(),
        )
    }
    single<ProviderConfigRepository> {
        RoomProviderConfigRepository(
            providerDao = get<AiChatDatabase>().providerConfigDao(),
            modelPreferenceDao = get<AiChatDatabase>().modelPreferenceDao(),
            secretStore = get(),
            clock = get(),
        )
    }
    single<PromptPresetRepository> {
        RoomPromptPresetRepository(get<AiChatDatabase>().promptPresetDao())
    }
    single<ImageGenerationRepository> {
        RoomImageGenerationRepository(get<AiChatDatabase>().imageGenerationDao())
    }
    single<ToolInvocationRepository> {
        RoomToolInvocationRepository(get<AiChatDatabase>().toolInvocationDao())
    }
    single<ImageStorage> { AndroidImageStorage(androidContext()) }
    single { GatewaySettingsRepository(androidContext(), secretStore = get()) }
    single { OpenAiChatProvider(client = get(named("streamingHttpClient"))) }
    single<ChatProvider>(named("openai")) { get<OpenAiChatProvider>() }
    single { OpenAiCompatibleChatProvider(client = get(named("streamingHttpClient"))) }
    single<ChatProvider>(named("compatible")) { get<OpenAiCompatibleChatProvider>() }
    single {
        ProviderRegistry().apply {
            val openAiProvider = get<ChatProvider>(named("openai"))
            val compatibleProvider = get<ChatProvider>(named("compatible"))
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenAI)), openAiProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenAICompatible)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenRouter)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Ollama)), compatibleProvider)
        }
    }
    factory<ImageGenerationProvider> { OpenAiImageGenerationProvider(client = get(named("longRunningHttpClient"))) }
    single { GatewayClient(client = get(named("jsonHttpClient"))) }
    single {
        val settingsRepository = get<GatewaySettingsRepository>()
        ToolExecutor(
            gatewaySettingsProvider = { settingsRepository.currentSettings() },
            gatewayClientProvider = { get() },
            toolInvocationRepository = get(),
            clock = get(),
        )
    }
    factory {
        AppBackupService(
            database = get(),
            providerRepository = get(),
            conversationRepository = get(),
            imageStorage = get(),
            clock = get(),
        )
    }
    single {
        ProviderConnectionTester(
            client = get(named("jsonHttpClient")),
            providerRegistry = get(),
        )
    }
    factory { ConversationCompactor(conversationRepository = get(), clock = get()) }
    factory { ConversationManager(conversationRepository = get(), clock = get()) }
    factory {
        GenerationController(
            conversationRepository = get(),
            providerRepository = get(),
            conversationManager = get(),
            conversationCompactor = get(),
            providerRegistry = get(),
            toolExecutor = get(),
            clock = get(),
        )
    }
    viewModel {
        HomeViewModel(
            conversationRepositoryProvider = { get() },
            providerRepository = get(),
        )
    }
    viewModel {
        ChatViewModel(
            savedStateHandle = get(),
            conversationRepository = get(),
            providerRepository = get(),
            promptPresetRepository = get(),
            conversationManager = get(),
            generationController = get(),
            providerRegistry = get(),
        )
    }
    viewModel {
        ImageGenerationViewModel(
            imageRepository = get(),
            providerRepository = get(),
            imageProvider = get(),
            imageStorage = get(),
            clock = get(),
        )
    }
    viewModel {
        ToolsViewModel(
            settingsRepository = get(),
            gatewayClient = get(),
            toolInvocationRepository = get(),
            clock = get(),
        )
    }
    viewModel {
        DataSettingsViewModel(
            backupService = get(),
        )
    }
}
