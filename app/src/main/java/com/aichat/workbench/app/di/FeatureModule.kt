package com.aichat.workbench.app.di

import com.aichat.workbench.domain.model.ChatConfig
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.GenerateImageUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.feature.chat.ChatViewModel
import com.aichat.workbench.feature.chat.ConversationContextBuilder
import com.aichat.workbench.feature.chat.ConversationContextProvider
import com.aichat.workbench.feature.chat.ConversationManager
import com.aichat.workbench.feature.chat.GenerationController
import com.aichat.workbench.feature.chat.SendMessageUseCaseFactory
import com.aichat.workbench.feature.conversations.ConversationsViewModel
import com.aichat.workbench.feature.image.ImageGenerationViewModel
import com.aichat.workbench.feature.provider.ProviderSettingsViewModel
import java.time.Clock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureModule: Module = module {
    factory<ConversationContextProvider> {
        ConversationContextBuilder(
            conversationRepository = get(),
            clock = get(),
        )
    }
    factory { ConversationManager(conversationRepository = get(), createConversationUseCase = get(), clock = get()) }
    factory<SendMessageUseCaseFactory> {
        val conversationRepository = get<ConversationRepository>()
        val clock = get<Clock>()
        val chatConfig = get<ChatConfig>()
        SendMessageUseCaseFactory { chatProvider ->
            SendMessageUseCase(conversationRepository, chatProvider, clock, chatConfig)
        }
    }
    factory {
        GenerationController(
            conversationRepository = get(),
            providerRepository = get(),
            contextProvider = get(),
            providerRegistry = get(),
            createConversationUseCase = get(),
            sendMessageUseCaseFactory = get(),
            clock = get(),
        )
    }
    factory {
        CreateConversationUseCase(
            repository = get(),
            clock = get(),
        )
    }
    factory {
        GenerateImageUseCase(
            repository = get(),
            imageProvider = get(),
            imageStorage = get(),
            clock = get(),
        )
    }
    factory {
        SaveProviderConfigUseCase(
            repository = get(),
        )
    }
    viewModelOf(::ConversationsViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::ProviderSettingsViewModel)
    viewModel {
        ImageGenerationViewModel(
            imageRepository = get(),
            providerRepository = get(),
            preferencesRepository = get(),
            modelRolePreferenceRepository = get(),
            connectionTester = get(),
            clock = get(),
            generateImageUseCase = get(),
        )
    }
}
