package com.aichat.workbench.tool.model

import com.aichat.workbench.domain.model.ToolPermissionLevel

enum class ToolPermissionPolicy {
    AskEveryTime,
    AllowWithoutPrompt,
}

data class ToolRuntimeSetting(
    val toolName: String,
    val enabled: Boolean = true,
    val permissionPolicy: ToolPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
)

fun Map<String, ToolRuntimeSetting>.runtimeSettingFor(toolName: String): ToolRuntimeSetting {
    val canonicalName = toolName.canonicalToolName()
    return this[canonicalName] ?: ToolRuntimeSetting(toolName = canonicalName)
}

fun Map<String, ToolRuntimeSetting>.runtimeSettingFor(descriptor: ToolDescriptor): ToolRuntimeSetting {
    val canonicalName = descriptor.name.canonicalToolName()
    return this[canonicalName] ?: ToolRuntimeSetting(
        toolName = canonicalName,
        permissionPolicy = descriptor.defaultPermissionPolicy,
    )
}

fun ToolDescriptor.canUsePermissionPolicy(): Boolean =
    permissionLevel.canUsePermissionPolicy()

fun ToolDescriptor.requiresConfirmation(policy: ToolPermissionPolicy = defaultPermissionPolicy): Boolean =
    when {
        permissionLevel == ToolPermissionLevel.ReadOnly -> false
        !canUsePermissionPolicy() -> true
        policy == ToolPermissionPolicy.AllowWithoutPrompt -> false
        else -> true
    }

fun ToolPermissionLevel.canUsePermissionPolicy(): Boolean =
    this == ToolPermissionLevel.Network

fun ToolPermissionLevel.requiresConfirmation(policy: ToolPermissionPolicy): Boolean =
    when {
        this == ToolPermissionLevel.ReadOnly -> false
        !canUsePermissionPolicy() -> true
        policy == ToolPermissionPolicy.AllowWithoutPrompt -> false
        else -> true
    }
