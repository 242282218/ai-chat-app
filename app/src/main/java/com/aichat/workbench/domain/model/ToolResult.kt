package com.aichat.workbench.domain.model

import java.time.Instant

data class ToolResult(
    val id: ToolCallId,
    val toolName: String,
    val permissionLevel: ToolPermissionLevel,
    val inputSummary: String,
    val output: ToolOutput,
    val status: ToolStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val error: ToolError?,
)

enum class ToolPermissionLevel {
    ReadOnly,
    Network,
    Execute,
    HighRisk,
}

enum class ToolStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Cancelled,
}

sealed interface ToolOutput {
    data class Text(val text: String) : ToolOutput
    data class Json(val value: String) : ToolOutput
}

data class ToolError(
    val code: String,
    val message: String,
)
