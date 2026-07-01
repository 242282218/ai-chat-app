package com.aichat.workbench.app.di

import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ProviderConnectionTestClient
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import com.aichat.workbench.provider.createBuiltInProviderRegistry
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.OpenAiImageGenerationProvider
import com.aichat.workbench.provider.openai.OpenAiChatProvider
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val providerModule: Module = module {
    single { OpenAiChatProvider(client = get(named("streamingHttpClient"))) }
    single<ChatProvider>(named("openai")) { get<OpenAiChatProvider>() }
    single { OpenAiCompatibleChatProvider(client = get(named("streamingHttpClient"))) }
    single<ChatProvider>(named("compatible")) { get<OpenAiCompatibleChatProvider>() }
    single<ProviderRegistry> {
        createBuiltInProviderRegistry(
            openAiProvider = get(named("openai")),
            compatibleProvider = get(named("compatible")),
        )
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
}
