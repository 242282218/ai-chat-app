package com.aichat.workbench.app

import androidx.room.Room
import com.aichat.workbench.data.crypto.AndroidSecretStore
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.image.AndroidImageStorage
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomImageGenerationRepository
import com.aichat.workbench.data.repository.RoomModelRolePreferenceRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.settings.DataStoreImageGenerationPreferencesRepository
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.feature.chat.ChatViewModel
import com.aichat.workbench.feature.chat.ConversationCompactor
import com.aichat.workbench.feature.chat.ConversationContextProvider
import com.aichat.workbench.feature.chat.ConversationManager
import com.aichat.workbench.feature.chat.GenerationController
import com.aichat.workbench.feature.conversations.ConversationsViewModel
import com.aichat.workbench.feature.image.ImageGenerationViewModel
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
            AiChatDatabase.MIGRATION_11_12,
            AiChatDatabase.MIGRATION_12_13,
            AiChatDatabase.MIGRATION_13_14,
            AiChatDatabase.MIGRATION_14_15,
            AiChatDatabase.MIGRATION_15_16,
            AiChatDatabase.MIGRATION_16_17,
            AiChatDatabase.MIGRATION_17_18,
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
    single<ImageGenerationRepository> {
        RoomImageGenerationRepository(
            dao = get<AiChatDatabase>().imageGenerationDao(),
            imageStorage = get(),
        )
    }
    single<ImageStorage> { AndroidImageStorage(androidContext()) }
    single<ImageGenerationPreferencesRepository> {
        DataStoreImageGenerationPreferencesRepository(androidContext(), dispatchers = get())
    }
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
    factory<ConversationContextProvider> {
        ConversationCompactor(
            conversationRepository = get(),
            clock = get(),
        )
    }
    factory { ConversationManager(conversationRepository = get(), clock = get()) }
    factory {
        GenerationController(
            conversationRepository = get(),
            providerRepository = get(),
            contextProvider = get(),
            providerRegistry = get(),
            clock = get(),
        )
    }
    viewModelOf(::ConversationsViewModel)
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
}
