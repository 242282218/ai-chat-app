package com.aichat.workbench.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.feature.chat.ChatViewModel
import com.aichat.workbench.feature.conversations.ConversationsViewModel
import com.aichat.workbench.feature.image.ImageGenerationViewModel
import com.aichat.workbench.feature.provider.ProviderSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppModuleSmokeTest : KoinTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        runCatching { stopKoin() }
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        runCatching {
            GlobalContext.getOrNull()?.get<AiChatDatabase>()?.close()
        }
        runCatching { stopKoin() }
        context.deleteDatabase(DATABASE_NAME)
        Dispatchers.resetMain()
    }

    @Test
    fun appModule_createsCoreRuntimeObjects() {
        startKoin {
            androidContext(context)
            modules(
                appModule,
                module {
                    factory { SavedStateHandle() }
                },
            )
        }

        assertNotNull(get<AiChatDatabase>())
        assertNotNull(get<ConversationsViewModel>())
        assertNotNull(get<ChatViewModel>())
        assertNotNull(get<ImageGenerationViewModel>())
        assertNotNull(get<ProviderSettingsViewModel>())
        dispatcher.scheduler.advanceUntilIdle()
    }

    private companion object {
        const val DATABASE_NAME = "ai_chat.db"
    }
}
