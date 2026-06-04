package com.aichat.workbench.data.settings

import android.content.Context
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ToolSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<Map<String, ToolRuntimeSetting>> =
        settings.asStateFlow()

    fun loadSettings() {
        settings.value = readSettings()
    }

    fun currentSettings(): Map<String, ToolRuntimeSetting> =
        readSettings().also { settings.value = it }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        val canonicalName = toolName.canonicalToolName()
        preferences.edit()
            .putBoolean(enabledKey(canonicalName), enabled)
            .apply()
        settings.value = readSettings()
    }

    fun setPermissionPolicy(toolName: String, policy: ToolPermissionPolicy) {
        val canonicalName = toolName.canonicalToolName()
        preferences.edit()
            .putString(policyKey(canonicalName), policy.name)
            .apply()
        settings.value = readSettings()
    }

    private fun readSettings(): Map<String, ToolRuntimeSetting> =
        preferences.all.keys
            .mapNotNull(::toolNameFromKey)
            .distinct()
            .associateWith { toolName ->
                ToolRuntimeSetting(
                    toolName = toolName,
                    enabled = preferences.getBoolean(enabledKey(toolName), true),
                    permissionPolicy = preferences.getString(policyKey(toolName), null).toPermissionPolicy(),
                )
            }

    private fun toolNameFromKey(key: String): String? =
        when {
            key.startsWith(KEY_ENABLED_PREFIX) -> key.removePrefix(KEY_ENABLED_PREFIX)
            key.startsWith(KEY_POLICY_PREFIX) -> key.removePrefix(KEY_POLICY_PREFIX)
            else -> null
        }?.canonicalToolName()?.takeIf { it.isNotBlank() }

    private fun String?.toPermissionPolicy(): ToolPermissionPolicy =
        ToolPermissionPolicy.values()
            .firstOrNull { it.name.equals(this.orEmpty(), ignoreCase = true) }
            ?: ToolPermissionPolicy.AskEveryTime

    private companion object {
        const val PREFS_NAME = "tool_settings"
        const val KEY_ENABLED_PREFIX = "enabled."
        const val KEY_POLICY_PREFIX = "policy."

        fun enabledKey(toolName: String): String = "$KEY_ENABLED_PREFIX$toolName"

        fun policyKey(toolName: String): String = "$KEY_POLICY_PREFIX$toolName"
    }
}
