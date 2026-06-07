package com.aichat.workbench.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.canUsePermissionPolicy
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone

@Composable
internal fun ToolCatalogHeader(state: ToolsUiState) {
    val enabledTools = state.enabledTools
    val networkCount = enabledTools.count { it.permissionLevel == ToolPermissionLevel.Network }
    val executeCount = enabledTools.count {
        it.permissionLevel == ToolPermissionLevel.Execute ||
            it.permissionLevel == ToolPermissionLevel.HighRisk
    }
    val disabledCount = state.tools.size - enabledTools.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "工具清单",
            description = "管理工具开关和联网工具确认策略；执行类和高风险工具始终每次确认。",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "${enabledTools.size}/${state.tools.size} 已启用", tone = StatusTone.Accent)
            }
            if (networkCount > 0) {
                item {
                    StatusPill(text = "$networkCount 个联网", tone = StatusTone.Warning)
                }
            }
            if (executeCount > 0) {
                item {
                    StatusPill(text = "$executeCount 个执行类", tone = StatusTone.Critical)
                }
            }
            if (disabledCount > 0) {
                item {
                    StatusPill(text = "$disabledCount 个关闭", tone = StatusTone.Neutral)
                }
            }
        }
    }
}

@Composable
internal fun ToolRow(
    tool: ToolDescriptor,
    state: ToolsUiState,
    setting: ToolRuntimeSetting,
    onConfirm: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPolicyChange: (ToolPermissionPolicy) -> Unit,
    onRerunLatest: (ToolResult) -> Unit,
    onRefillLatest: (ToolResult) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showSchema by rememberSaveable(tool.name) { mutableStateOf(false) }
    var showRecentRuns by rememberSaveable(tool.name) { mutableStateOf(false) }
    val outputSchema = tool.outputSchemaJson ?: "(未声明)"
    val latestResult = state.latestToolResultFor(tool)
    val latestRerunnableResult = state.latestRerunnableToolResultFor(tool)
    val latestRefillableResult = state.latestRefillableToolResultFor(tool)
    val recentRuns = state.recentToolResultsFor(tool)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QuietListRow(
            title = tool.displayName,
            description = "${tool.permissionLevel.displayLabel()} / ${tool.source.displayLabel()} · ${tool.description}",
            icon = tool.permissionIcon(),
            onClick = onConfirm,
            enabled = setting.enabled,
            trailing = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Switch(
                        checked = setting.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                    StatusPill(
                        text = if (setting.enabled) "已启用" else "已关闭",
                        tone = if (setting.enabled) StatusTone.Success else StatusTone.Neutral,
                    )
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(
                    text = tool.permissionLevel.displayLabel(),
                    tone = tool.permissionTone(),
                )
            }
            item {
                StatusPill(
                    text = tool.riskLevel.displayLabel(),
                    tone = tool.riskLevel.tone(),
                )
            }
            item {
                if (latestResult == null) {
                    StatusPill(text = "最近未运行", tone = StatusTone.Neutral)
                } else {
                    StatusPill(
                        text = "最近${latestResult.status.displayLabel()}",
                        tone = latestResult.status.tone(),
                    )
                }
            }
            latestResult?.durationMs?.let { durationMs ->
                item {
                    StatusPill(text = "${durationMs} ms", tone = StatusTone.Neutral)
                }
            }
            latestResult?.error?.takeIf { it.code.isNotBlank() }?.let { error ->
                item {
                    StatusPill(text = error.statusLabel(), tone = latestResult.status.errorTone())
                }
            }
            latestRerunnableResult?.let { result ->
                item {
                    OutlinedButton(onClick = { onRerunLatest(result) }) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "重跑最近")
                    }
                }
            }
            latestRefillableResult?.let { result ->
                item {
                    OutlinedButton(onClick = { onRefillLatest(result) }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "回填最近")
                    }
                }
            }
            if (tool.requiresNetwork) {
                item {
                    StatusPill(text = "访问网络", tone = StatusTone.Warning)
                }
            }
            if (tool.requiresFileAccess) {
                item {
                    StatusPill(text = "读取文件", tone = StatusTone.Critical)
                }
            }
            if (tool.canUsePermissionPolicy()) {
                item {
                    SelectableFilterButton(
                        selected = setting.permissionPolicy == ToolPermissionPolicy.AskEveryTime,
                        text = "每次确认",
                        onClick = { onPolicyChange(ToolPermissionPolicy.AskEveryTime) },
                    )
                }
                item {
                    SelectableFilterButton(
                        selected = setting.permissionPolicy == ToolPermissionPolicy.AllowWithoutPrompt,
                        text = "免确认",
                        onClick = { onPolicyChange(ToolPermissionPolicy.AllowWithoutPrompt) },
                    )
                }
            } else {
                item {
                    StatusPill(
                        text = tool.fixedPermissionPolicyLabel(),
                        tone = tool.permissionPolicyTone(),
                    )
                }
            }
            item {
                OutlinedButton(onClick = { showSchema = !showSchema }) {
                    Icon(
                        imageVector = if (showSchema) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (showSchema) "隐藏 schema" else "查看 schema")
                }
            }
            item {
                OutlinedButton(onClick = { showRecentRuns = !showRecentRuns }) {
                    Icon(
                        imageVector = if (showRecentRuns) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (showRecentRuns) "隐藏最近" else "查看最近")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString("input:\n${tool.inputSchemaJson}\n\noutput:\n$outputSchema"),
                        )
                    },
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制 schema")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.sampleInputForTool(tool)))
                    },
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制示例参数")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.chatInstructionForTool(tool)))
                    },
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制聊天指令")
                }
            }
        }
        if (showSchema) {
            OutputText(
                label = "输入 schema",
                value = tool.inputSchemaJson.schemaPreview(),
            )
            OutputText(
                label = "输出 schema",
                value = outputSchema.schemaPreview(),
            )
        }
        if (showRecentRuns) {
            ToolRecentRuns(
                results = recentRuns,
                onCopyInput = { result ->
                    clipboard.setText(AnnotatedString(result.rawInputJson ?: result.inputSummary))
                },
                onCopyOutput = { result ->
                    clipboard.setText(AnnotatedString(result.rawOutputJson ?: result.output.asPlainText()))
                },
            )
        }
    }
}

@Composable
private fun ToolRecentRuns(
    results: List<ToolResult>,
    onCopyInput: (ToolResult) -> Unit,
    onCopyOutput: (ToolResult) -> Unit,
) {
    if (results.isEmpty()) {
        InlineNotice(
            text = "这个工具还没有运行记录。",
            icon = Icons.Filled.Info,
            tone = StatusTone.Neutral,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "最近 ${results.size} 次运行",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        results.forEach { result ->
            ToolRecentRunRow(
                result = result,
                onCopyInput = { onCopyInput(result) },
                onCopyOutput = { onCopyOutput(result) },
            )
        }
    }
}

@Composable
private fun ToolRecentRunRow(
    result: ToolResult,
    onCopyInput: () -> Unit,
    onCopyOutput: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = result.status.displayLabel(), tone = result.status.tone())
            }
            item {
                StatusPill(text = result.startedAt.toString(), tone = StatusTone.Neutral)
            }
            result.durationMs?.let { durationMs ->
                item {
                    StatusPill(text = "${durationMs} ms", tone = StatusTone.Neutral)
                }
            }
            result.error?.takeIf { it.code.isNotBlank() }?.let { error ->
                item {
                    StatusPill(text = error.statusLabel(), tone = result.status.errorTone())
                }
            }
        }
        MetadataRow(label = "输入", value = result.inputSummary.ifBlank { "(空)" })
        result.error?.let { error ->
            Text(
                text = error.diagnosticLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = onCopyInput) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制输入")
                }
            }
            item {
                OutlinedButton(onClick = onCopyOutput) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制输出")
                }
            }
        }
    }
}
