package com.aichat.workbench.app.di

import com.aichat.workbench.app.AppDispatchers
import com.aichat.workbench.app.ApplicationScope
import com.aichat.workbench.domain.model.ChatConfig
import java.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    single { AppDispatchers() }
    single { ApplicationScope(dispatchers = get()) }
    single { Clock.systemUTC() }
    single { ChatConfig() }
}

val appModules: List<Module> = listOf(
    appModule,
    databaseModule,
    networkModule,
    repositoryModule,
    providerModule,
    featureModule,
)
