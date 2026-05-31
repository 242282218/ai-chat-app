package com.aichat.workbench.app

import android.app.Application

class AiChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
    }
}
