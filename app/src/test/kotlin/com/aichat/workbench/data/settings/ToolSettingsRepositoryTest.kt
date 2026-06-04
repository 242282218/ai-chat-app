package com.aichat.workbench.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.runtimeSettingFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToolSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("tool_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun defaultsUnknownToolToEnabledAndAskEveryTime() {
        val repository = ToolSettingsRepository(context)

        val setting = repository.currentSettings().runtimeSettingFor("local-search")

        assertEquals("web_search_local", setting.toolName)
        assertEquals(true, setting.enabled)
        assertEquals(ToolPermissionPolicy.AskEveryTime, setting.permissionPolicy)
    }

    @Test
    fun savesEnabledAndPermissionPolicyWithCanonicalToolName() {
        val repository = ToolSettingsRepository(context)

        repository.setToolEnabled("local-search", false)
        repository.setPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)

        val reloaded = ToolSettingsRepository(context).currentSettings().runtimeSettingFor("web-search-local")
        assertEquals("web_search_local", reloaded.toolName)
        assertEquals(false, reloaded.enabled)
        assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, reloaded.permissionPolicy)
    }

    @Test
    fun invalidStoredPolicyFallsBackToAskEveryTime() {
        preferences.edit()
            .putString("policy.web_search_local", "FuturePolicy")
            .apply()

        val setting = ToolSettingsRepository(context).currentSettings().runtimeSettingFor("web_search_local")

        assertEquals(ToolPermissionPolicy.AskEveryTime, setting.permissionPolicy)
    }
}
