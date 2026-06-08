package com.aichat.workbench.app

import androidx.room.Room
import com.aichat.workbench.agent.skill.AndroidAssetSkillRegistry
import com.aichat.workbench.agent.skill.SkillRegistry
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.backup.BackupService
import com.aichat.workbench.data.crypto.AndroidSecretStore
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.image.AndroidImageStorage
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomImageGenerationRepository
import com.aichat.workbench.data.repository.RoomMemoryRepository
import com.aichat.workbench.data.repository.RoomModelRolePreferenceRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.repository.RoomToolInvocationRepository
import com.aichat.workbench.data.settings.DataStoreImageGenerationPreferencesRepository
import com.aichat.workbench.data.settings.DataStoreThemeSettingsRepository
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.data.settings.SearchSettingsRepository
import com.aichat.workbench.data.settings.ToolSettingsRepository
import com.aichat.workbench.data.settings.toSearchConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.MemoryRepository
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ThemeSettingsRepository
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.domain.tool.ToolExecutionService
import com.aichat.workbench.domain.usecase.SaveMemoryUseCase
import com.aichat.workbench.feature.chat.ChatViewModel
import com.aichat.workbench.feature.chat.ConversationCompactor
import com.aichat.workbench.feature.chat.ConversationManager
import com.aichat.workbench.feature.chat.GenerationController
import com.aichat.workbench.feature.home.HomeViewModel
import com.aichat.workbench.feature.image.ImageGenerationViewModel
import com.aichat.workbench.feature.settings.DataSettingsViewModel
import com.aichat.workbench.feature.settings.SettingsHubViewModel
import com.aichat.workbench.feature.tools.ToolsViewModel
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderConnectionTestClient
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.OpenAiImageGenerationProvider
import com.aichat.workbench.provider.http.WorkbenchHttpClients
import com.aichat.workbench.provider.openai.OpenAiChatProvider
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.local.AndroidAuthorizedFileReader
import com.aichat.workbench.tool.local.AndroidJavaScriptRunner
import com.aichat.workbench.tool.local.AuthorizedFileReader
import com.aichat.workbench.tool.local.DefaultProviderConnectionTestRunner
import com.aichat.workbench.tool.local.LocalScriptRunner
import com.aichat.workbench.tool.local.LocalToolExecutor
import com.aichat.workbench.tool.local.ProviderConnectionTestRunner
import com.aichat.workbench.tool.local.defaultLocalTools
import com.aichat.workbench.tool.runtime.ToolExecutor
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.TavilyLocalSearchClient
import java.time.Clock
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule: Module = module {
    single { AppDispatchers() }
    single { ApplicationScope(dispatchers = get()) }
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
            AiChatDatabase.MIGRATION_7_8,
            AiChatDatabase.MIGRATION_8_9,
            AiChatDatabase.MIGRATION_9_10,
            AiChatDatabase.MIGRATION_10_11,
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
            modelRolePreferenceDao = get<AiChatDatabase>().modelRolePreferenceDao(),
        )
    }
    single<ModelRolePreferenceRepository> {
        RoomModelRolePreferenceRepository(
            dao = get<AiChatDatabase>().modelRolePreferenceDao(),
            clock = get(),
        )
    }
    single<PromptPresetRepository> {
        RoomPromptPresetRepository(get<AiChatDatabase>().promptPresetDao())
    }
    single<ImageGenerationRepository> {
        RoomImageGenerationRepository(
            dao = get<AiChatDatabase>().imageGenerationDao(),
            imageStorage = get(),
        )
    }
    single<ToolInvocationRepository> {
        RoomToolInvocationRepository(get<AiChatDatabase>().toolInvocationDao())
    }
    single<MemoryRepository> {
        RoomMemoryRepository(get<AiChatDatabase>().memoryDao())
    }
    factory { SaveMemoryUseCase(repository = get(), clock = get()) }
    single<ImageStorage> { AndroidImageStorage(androidContext()) }
    single<ImageGenerationPreferencesRepository> {
        DataStoreImageGenerationPreferencesRepository(androidContext(), dispatchers = get())
    }
    single<ThemeSettingsRepository> {
        DataStoreThemeSettingsRepository(androidContext(), dispatchers = get())
    }
    single { GatewaySettingsRepository(androidContext(), secretStore = get()) }
    single { SearchSettingsRepository(androidContext(), secretStore = get()) }
    single { ToolSettingsRepository(androidContext()) }
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
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.NewApi)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Sub2Api)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Custom)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenRouter)), compatibleProvider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Ollama)), compatibleProvider)
        }
    }
    factory<ImageGenerationProvider> { OpenAiImageGenerationProvider(client = get(named("longRunningHttpClient"))) }
    single { GatewayClient(client = get(named("jsonHttpClient"))) }
    single<LocalSearchClient> { TavilyLocalSearchClient(client = get(named("jsonHttpClient")), clock = get()) }
    single<LocalScriptRunner> { AndroidJavaScriptRunner(androidContext()) }
    single<AuthorizedFileReader> { AndroidAuthorizedFileReader(androidContext()) }
    single<ProviderConnectionTestRunner> { DefaultProviderConnectionTestRunner(get()) }
    single<SkillRegistry> { AndroidAssetSkillRegistry(androidContext()) }
    single {
        val searchSettingsRepository = get<SearchSettingsRepository>()
        LocalToolExecutor(
            defaultLocalTools(
                clock = get(),
                scriptRunner = get(),
                fileReader = get(),
                providerRepository = get(),
                providerConnectionRunner = get(),
                skillRegistry = get(),
                searchConfigProvider = { searchSettingsRepository.currentSettings().toSearchConfig() },
                searchClient = get(),
            ),
        )
    }
    single<ToolExecutionService> {
        val settingsRepository = get<GatewaySettingsRepository>()
        val toolSettingsRepository = get<ToolSettingsRepository>()
        ToolExecutor(
            gatewaySettingsProvider = { settingsRepository.currentSettings() },
            gatewayClientProvider = { get() },
            toolInvocationRepository = get(),
            providerRepository = get(),
            preferencesRepository = get(),
            modelRolePreferenceRepository = get(),
            imageGenerationRepository = get(),
            imageProvider = get(),
            imageStorage = get(),
            clock = get(),
            localToolExecutor = get(),
            toolSettingsProvider = { toolSettingsRepository.currentSettings() },
        )
    }
    factory<BackupService> {
        AppBackupService(
            database = get(),
            providerRepository = get(),
            conversationRepository = get(),
            imageStorage = get(),
            clock = get(),
        )
    }
    single {
        ProviderModelDiscoveryClient(
            client = get(named("jsonHttpClient")),
            providerRegistry = get(),
        )
    }
    single {
        ProviderConnectionTester(
            client = get(named("jsonHttpClient")),
            providerRegistry = get(),
        )
    }
    single<ProviderConnectionTestClient> { get<ProviderConnectionTester>() }
    factory {
        ConversationCompactor(
            conversationRepository = get(),
            memoryRepository = get(),
            clock = get(),
        )
    }
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
    viewModelOf(::ChatViewModel)
    viewModel {
        ImageGenerationViewModel(
            imageRepository = get(),
            providerRepository = get(),
            preferencesRepository = get(),
            modelRolePreferenceRepository = get(),
            imageProvider = get(),
            imageStorage = get(),
            connectionTester = get(),
            clock = get(),
        )
    }
    viewModel {
        ToolsViewModel(
            settingsRepository = get(),
            searchSettingsRepository = get(),
            toolSettingsRepository = get(),
            gatewayClient = get(),
            localSearchClient = get(),
            toolInvocationRepository = get(),
            clock = get(),
        )
    }
    viewModel {
        SettingsHubViewModel(
            themeSettingsRepository = get(),
        )
    }
    viewModel {
        DataSettingsViewModel(
            backupService = get(),
        )
    }
}
