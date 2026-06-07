package com.aichat.workbench.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.ui.component.StatusTone

internal fun ToolDescriptor.fixedPermissionPolicyLabel(): String =
    if (!requiresConfirmation(defaultPermissionPolicy)) {
        "直接运行"
    } else {
        "每次确认"
    }

internal fun ToolDescriptor.permissionPolicyTone(): StatusTone =
    when (permissionLevel) {
        ToolPermissionLevel.ReadOnly -> StatusTone.Success
        ToolPermissionLevel.Network -> StatusTone.Warning
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> StatusTone.Critical
    }

internal fun ToolRiskLevel.displayLabel(): String =
    when (this) {
        ToolRiskLevel.Low -> "低风险"
        ToolRiskLevel.Medium -> "中风险"
        ToolRiskLevel.High -> "高风险"
    }

internal fun ToolRiskLevel.tone(): StatusTone =
    when (this) {
        ToolRiskLevel.Low -> StatusTone.Neutral
        ToolRiskLevel.Medium -> StatusTone.Warning
        ToolRiskLevel.High -> StatusTone.Critical
    }

internal fun ToolPermissionPolicy.displayLabel(): String =
    when (this) {
        ToolPermissionPolicy.AskEveryTime -> "每次确认"
        ToolPermissionPolicy.AllowWithoutPrompt -> "免确认"
    }

internal fun ToolPermissionPolicy.tone(): StatusTone =
    when (this) {
        ToolPermissionPolicy.AskEveryTime -> StatusTone.Warning
        ToolPermissionPolicy.AllowWithoutPrompt -> StatusTone.Success
    }

internal fun ToolDescriptor.permissionIcon(): ImageVector =
    permissionLevel.permissionIcon()

internal fun ToolPermissionLevel.permissionIcon(): ImageVector =
    when (this) {
        ToolPermissionLevel.ReadOnly -> Icons.Filled.Info
        ToolPermissionLevel.Network -> Icons.Filled.Public
        ToolPermissionLevel.Execute -> Icons.Filled.Code
        ToolPermissionLevel.HighRisk -> Icons.Filled.Security
    }

internal fun ToolDescriptor.permissionTone(): StatusTone =
    permissionLevel.permissionTone()

internal fun ToolPermissionLevel.permissionTone(): StatusTone =
    when (this) {
        ToolPermissionLevel.ReadOnly -> StatusTone.Neutral
        ToolPermissionLevel.Network -> StatusTone.Warning
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> StatusTone.Critical
    }

internal fun ToolPermissionLevel.displayLabel(): String =
    when (this) {
        ToolPermissionLevel.ReadOnly -> "只读"
        ToolPermissionLevel.Network -> "联网"
        ToolPermissionLevel.Execute -> "执行"
        ToolPermissionLevel.HighRisk -> "高风险"
    }

internal fun ToolStatus.displayLabel(): String =
    when (this) {
        ToolStatus.Queued -> "排队"
        ToolStatus.Pending -> "等待"
        ToolStatus.NeedsApproval -> "待授权"
        ToolStatus.Running -> "运行中"
        ToolStatus.Streaming -> "流式返回"
        ToolStatus.Completed -> "完成"
        ToolStatus.Failed -> "失败"
        ToolStatus.Denied -> "已拒绝"
        ToolStatus.Cancelled -> "已取消"
    }

internal fun ToolStatus.tone(): StatusTone =
    when (this) {
        ToolStatus.Queued,
        ToolStatus.Pending,
        ToolStatus.NeedsApproval,
        ToolStatus.Running,
        ToolStatus.Streaming,
        -> StatusTone.Accent
        ToolStatus.Completed -> StatusTone.Success
        ToolStatus.Failed -> StatusTone.Critical
        ToolStatus.Denied -> StatusTone.Warning
        ToolStatus.Cancelled -> StatusTone.Neutral
    }

internal fun ToolStatus.errorTone(): StatusTone =
    when (this) {
        ToolStatus.Failed -> StatusTone.Critical
        else -> tone()
    }

internal fun ToolStatus.isUserStopped(): Boolean =
    this == ToolStatus.Denied ||
        this == ToolStatus.Cancelled

internal fun ToolOutput.asPlainText(): String =
    when (this) {
        is ToolOutput.Text -> text
        is ToolOutput.Json -> value
    }

internal fun ToolError.diagnosticLabel(): String =
    buildString {
        append("$code: $message")
        statusCode?.let { append(" · HTTP $it") }
        retryable?.let { append(" · ${if (it) "可重试" else "不可重试"}") }
    }

internal fun ToolError.statusLabel(): String =
    statusCode?.let { "$code · HTTP $it" } ?: code

internal fun String.rawPayloadPreview(): String {
    val preview = take(MAX_TOOL_HISTORY_RAW_PREVIEW_CHARS)
    return if (length > preview.length) "$preview\n... 已截断显示，可复制完整内容" else preview
}

internal fun String.schemaPreview(): String {
    val preview = take(MAX_TOOL_SCHEMA_PREVIEW_CHARS)
    return if (length > preview.length) "$preview\n... 已截断显示，可复制完整 schema" else preview
}

internal fun String.historyLabel(prefix: String): String =
    "$prefix ${take(8)}"

internal fun ToolSource.displayLabel(): String =
    when (this) {
        ToolSource.BuiltIn -> "内置"
        ToolSource.Gateway -> "Gateway"
        ToolSource.Official -> "官方"
    }

internal fun searchPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    state.searchActionStatus().toStatusTone()

internal fun sandboxPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    state.sandboxActionStatus().toStatusTone()

internal fun GatewayActionStatus.toStatusTone(): Pair<String, StatusTone> =
    when {
        isBusy -> label to StatusTone.Accent
        isReady -> label to StatusTone.Success
        else -> label to StatusTone.Warning
    }

internal fun GatewayUrlStatus.tone(): StatusTone =
    when {
        isValid && isWarning -> StatusTone.Warning
        isValid -> StatusTone.Success
        isWarning -> StatusTone.Warning
        else -> StatusTone.Critical
    }

internal fun toolStatusLabel(message: String): String =
    when {
        toolStatusTone(message) == StatusTone.Critical -> "需要处理"
        message == "已保存" -> "已保存"
        message == "搜索设置已保存" -> "已保存"
        else -> "状态"
    }

internal fun toolStatusTone(message: String): StatusTone {
    val normalized = message.lowercase()
    return when {
        normalized.contains("failed") ||
            normalized.contains("must not") ||
            normalized.contains("enable gateway") ||
            normalized.contains("load gateway") ||
            normalized.contains("invalid") ||
            normalized.contains("不能为空") ||
            normalized.contains("无效") ||
            normalized.contains("请启用 gateway") ||
            normalized.contains("请先加载 gateway") ||
            normalized.contains("失败") -> StatusTone.Critical
        message == "已保存" || message == "搜索设置已保存" -> StatusTone.Success
        else -> StatusTone.Accent
    }
}

internal fun ToolsUiState.hasToolWorkbenchOutput(): Boolean =
    searchResults.isNotEmpty() ||
        searchError != null ||
        sandboxResult != null ||
        sandboxError != null

internal fun toolWorkbenchStatusLabel(state: ToolsUiState): String =
    when {
        state.isLoading -> "处理中"
        state.hasToolWorkbenchOutput() -> "已有结果"
        state.hasSearchTool() || state.hasSandboxTool() -> "可试运行"
        else -> "需要清单"
    }

internal fun toolWorkbenchStatusTone(state: ToolsUiState): StatusTone =
    when {
        state.isLoading -> StatusTone.Accent
        state.hasToolWorkbenchOutput() -> StatusTone.Success
        state.hasSearchTool() || state.hasSandboxTool() -> StatusTone.Neutral
        else -> StatusTone.Warning
    }

internal const val MAX_TOOL_HISTORY_OUTPUT_PREVIEW_CHARS = 1_500

private const val MAX_TOOL_HISTORY_RAW_PREVIEW_CHARS = 4_000
private const val MAX_TOOL_SCHEMA_PREVIEW_CHARS = 4_000
