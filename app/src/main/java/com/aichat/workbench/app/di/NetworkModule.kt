package com.aichat.workbench.app.di

import com.aichat.workbench.provider.http.WorkbenchHttpClients
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val networkModule: Module = module {
    single<OkHttpClient>(named("jsonHttpClient")) { WorkbenchHttpClients.json() }
    single<OkHttpClient>(named("streamingHttpClient")) { WorkbenchHttpClients.streaming() }
    single<OkHttpClient>(named("longRunningHttpClient")) { WorkbenchHttpClients.longRunning() }
}
