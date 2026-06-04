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
    val riskLevel: ToolRiskLevel = permissionLevel.defaultRiskLevel(),
    val requiresNetwork: Boolean = permissionLevel == ToolPermissionLevel.Network,
    val requiresFileAccess: Boolean = false,
    val defaultPermissionPolicy: ToolPermissionPolicy = permissionLevel.defaultPermissionPolicy(),
)

enum class ToolSource {
    BuiltIn,
    Gateway,
    Official,
}

enum class ToolRiskLevel {
    Low,
    Medium,
    High,
}

fun ToolPermissionLevel.requiresConfirmation(): Boolean =
    this != ToolPermissionLevel.ReadOnly

fun ToolPermissionLevel.defaultRiskLevel(): ToolRiskLevel =
    when (this) {
        ToolPermissionLevel.ReadOnly -> ToolRiskLevel.Low
        ToolPermissionLevel.Network -> ToolRiskLevel.Medium
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> ToolRiskLevel.High
    }

fun ToolPermissionLevel.defaultPermissionPolicy(): ToolPermissionPolicy =
    when (this) {
        ToolPermissionLevel.ReadOnly -> ToolPermissionPolicy.AllowWithoutPrompt
        ToolPermissionLevel.Network,
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> ToolPermissionPolicy.AskEveryTime
    }

fun String.canonicalToolName(): String =
    when (val normalized = trim().lowercase().replace("-", "_")) {
        "websearch", "search", "web_search" -> "web_search"
        "websearchlocal", "local_search", "local_web_search", "web_search_local" -> "web_search_local"
        "codesandbox", "sandbox", "code_sandbox" -> "code_sandbox"
        "imagegeneration", "generate_image", "image_generation" -> "image_generation"
        "localjs", "local_javascript", "javascript", "js", "local_js" -> "local_js"
        "fileread", "read_file", "readfile", "file_read" -> "file_read"
        "providerconnectiontest", "provider_test", "test_provider", "provider_connection_test" ->
            "provider_connection_test"
        else -> normalized
    }
