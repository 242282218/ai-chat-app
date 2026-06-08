package com.aichat.workbench

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aichat.workbench.feature.provider.ProviderSettingsScreen
import com.aichat.workbench.ui.theme.AiChatTheme
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityDeviceTest {
    @Test
    fun launchesMainActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun rendersProviderSettingsScreen() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    AiChatTheme {
                        ProviderSettingsScreen(onBack = {})
                    }
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        } finally {
            scenario.close()
        }
    }
}
