package com.aichat.workbench.app.di

import com.aichat.workbench.app.ApplicationScope
import com.aichat.workbench.data.crypto.AndroidSecretStore
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.image.AndroidImageStorage
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomImageGenerationRepository
import com.aichat.workbench.data.repository.RoomModelRolePreferenceRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.settings.DataStoreImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.navigation.DraftHandoffRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule: Module = module {
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
        DataStoreImageGenerationPreferencesRepository(androidContext(), scope = get<ApplicationScope>())
    }
    single { DraftHandoffRepository() }
}
