package com.aichat.workbench.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class AiChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() != null) return
        startKoin {
            androidContext(this@AiChatApplication)
            modules(appModule)
        }
    }
}
