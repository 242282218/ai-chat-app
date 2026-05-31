package com.aichat.workbench.tool.model

import com.aichat.workbench.domain.model.ToolPermissionLevel
import java.time.Instant

data class ToolManifest(
    val version: Int,
    val generatedAt: Instant,
    val tools: List<ToolDescriptor>,
)

data class ToolDescriptor(
    val name: String,
    val displayName: String,
    val description: String,
    val permissionLevel: ToolPermissionLevel,
    val inputSchemaJson: String,
    val outputSchemaJson: String?,
    val timeoutSeconds: Int?,
    val source: ToolSource,
)

enum class ToolSource {
    BuiltIn,
    Gateway,
}

fun ToolPermissionLevel.requiresConfirmation(): Boolean =
    this != ToolPermissionLevel.ReadOnly
