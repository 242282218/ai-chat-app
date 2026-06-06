package com.aichat.workbench.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.coroutines.delay

@Composable
fun ToolCallPanel(
    toolCall: ToolCall,
    result: String?,
    isError: Boolean,
    isPending: Boolean,
    displayName: String? = null,
    permissionLevel: ToolPermissionLevel? = null,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onArgumentsChange: (String) -> Unit = {},
    onRetryWithArguments: (String) -> Unit = {},
    onCancelRunning: (() -> Unit)? = null,
    isPlanOnly: Boolean = false,
    outcome: ToolCallPanelOutcome = toolCallPanelOutcome(
        result = result,
        isError = isError,
        isPending = isPending,
        isPlanOnly = isPlanOnly,
    ),
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(toolCall.id.value) { mutableStateOf(outcome.expandsByDefault()) }
    var editableArguments by rememberSaveable(toolCall.id.value) { mutableStateOf(toolCall.arguments) }
    val resolvedPermissionLevel = permissionLevel ?: toolCall.name.inferredPermissionLevel()
    val info = toolVisualInfo(toolCall.name, displayName ?: toolCall.name.inferredDisplayName())
    val state = toolCardState(
        outcome = outcome,
        permissionLevel = resolvedPermissionLevel,
    )
    val displayResult = result ?: outcome.fallbackResult()
    val clipboard = LocalClipboardManager.current

    // Sync editableArguments only when the underlying toolCall.arguments identity changes
    // (keyed on toolCall.id), avoiding overwriting user edits on every recomposition.
    LaunchedEffect(toolCall.id.value, toolCall.arguments) {
        if (outcome == ToolCallPanelOutcome.Pending) {
            editableArguments = toolCall.arguments
        }
    }

    LaunchedEffect(outcome) {
        if (outcome.expandsByDefault()) {
            expanded = true
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolCardHeader(
                info = info,
                state = state,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
            )
            ToolArgumentsBody(
                arguments = if (outcome == ToolCallPanelOutcome.Pending) editableArguments else toolCall.arguments,
                expanded = expanded,
                editable = outcome == ToolCallPanelOutcome.Pending,
                onArgumentsChange = { value ->
                    editableArguments = value
                    onArgumentsChange(value)
                },
            )
            when {
                outcome == ToolCallPanelOutcome.Pending -> ToolApprovalActions(onApprove = onApprove, onDeny = onDeny)
                displayResult != null -> ToolResultBody(
                    toolName = toolCall.name,
                    arguments = toolCall.arguments,
                    result = displayResult,
                    outcome = outcome,
                    expanded = expanded,
                    onCopy = { clipboard.setText(AnnotatedString(displayResult)) },
                    onCopyArguments = { clipboard.setText(AnnotatedString(toolCall.arguments.ifBlank { "{}" })) },
                    onCopyDebugBundle = {
                        clipboard.setText(
                            AnnotatedString(
                                toolDebugBundle(
                                    toolName = toolCall.name,
                                    displayName = info.label,
                                    permissionLevel = resolvedPermissionLevel,
                                    arguments = toolCall.arguments,
                                    result = displayResult,
                                    isError = outcome != ToolCallPanelOutcome.Completed,
                                    statusLabel = outcome.displayLabel(),
                                ),
                            ),
                        )
                    },
                    onRetryWithArguments = { onRetryWithArguments(toolCall.arguments.ifBlank { "{}" }) },
                )
                outcome == ToolCallPanelOutcome.Planned -> ToolPlanBody(
                    onCopyArguments = {
                        clipboard.setText(AnnotatedString(toolCall.arguments.copyableToolArguments()))
                    },
                    onCopyPlanBundle = {
                        clipboard.setText(
                            AnnotatedString(
                                toolPlanBundle(
                                    toolName = toolCall.name,
                                    displayName = info.label,
                                    permissionLevel = resolvedPermissionLevel,
                                    arguments = toolCall.arguments,
                                ),
                            ),
                        )
                    },
                )
                else -> ToolRunningBody(onCancel = onCancelRunning)
            }
        }
    }
}

enum class ToolCallPanelOutcome {
    Pending,
    Running,
    Streaming,
    Planned,
    Completed,
    Failed,
    Denied,
    Cancelled,
}

fun toolCallPanelOutcome(
    result: String?,
    isError: Boolean,
    isPending: Boolean,
    isPlanOnly: Boolean,
    isStreaming: Boolean = false,
): ToolCallPanelOutcome =
    when {
        isPending -> ToolCallPanelOutcome.Pending
        isError -> ToolCallPanelOutcome.Failed
        isStreaming -> ToolCallPanelOutcome.Streaming
        result != null -> ToolCallPanelOutcome.Completed
        isPlanOnly -> ToolCallPanelOutcome.Planned
        else -> ToolCallPanelOutcome.Running
    }

private fun ToolCallPanelOutcome.expandsByDefault(): Boolean =
    this == ToolCallPanelOutcome.Pending ||
        this == ToolCallPanelOutcome.Failed ||
        this == ToolCallPanelOutcome.Denied ||
        this == ToolCallPanelOutcome.Cancelled

private fun ToolCallPanelOutcome.displayLabel(): String =
    when (this) {
        ToolCallPanelOutcome.Pending -> "待授权"
        ToolCallPanelOutcome.Running -> "运行中"
        ToolCallPanelOutcome.Streaming -> "流式返回"
        ToolCallPanelOutcome.Planned -> "已计划"
        ToolCallPanelOutcome.Completed -> "完成"
        ToolCallPanelOutcome.Failed -> "失败"
        ToolCallPanelOutcome.Denied -> "已拒绝"
        ToolCallPanelOutcome.Cancelled -> "已取消"
    }

private fun ToolCallPanelOutcome.resultTone(): StatusTone =
    when (this) {
        ToolCallPanelOutcome.Completed -> StatusTone.Success
        ToolCallPanelOutcome.Streaming -> StatusTone.Accent
        ToolCallPanelOutcome.Denied -> StatusTone.Warning
        ToolCallPanelOutcome.Cancelled -> StatusTone.Neutral
        else -> StatusTone.Critical
    }

private fun ToolCallPanelOutcome.needsRecoveryAction(): Boolean =
    this == ToolCallPanelOutcome.Failed ||
        this == ToolCallPanelOutcome.Denied ||
        this == ToolCallPanelOutcome.Cancelled

private fun ToolCallPanelOutcome.resultDescription(): String =
    when (this) {
        ToolCallPanelOutcome.Completed -> "工具执行完成，可复制结果或继续让模型处理。"
        ToolCallPanelOutcome.Streaming -> "工具结果正在流式回写，可等待完成或停止。"
        ToolCallPanelOutcome.Denied -> "用户已拒绝执行，参数保留在聊天流，可调整后重新发起。"
        ToolCallPanelOutcome.Cancelled -> "工具执行已取消，参数和日志已保留，可调整后重试。"
        else -> "工具执行失败，可复制日志后修改参数重试。"
    }

private fun ToolCallPanelOutcome.fallbackResult(): String? =
    when (this) {
        ToolCallPanelOutcome.Completed -> "工具执行完成，未返回文本结果。"
        ToolCallPanelOutcome.Failed -> "工具执行失败，未返回详细日志。"
        ToolCallPanelOutcome.Denied -> "用户拒绝执行工具。"
        ToolCallPanelOutcome.Cancelled -> "工具执行已取消。"
        else -> null
    }

@Composable
private fun ToolArgumentsBody(
    arguments: String,
    expanded: Boolean,
    editable: Boolean,
    onArgumentsChange: (String) -> Unit,
) {
    if (!editable) {
        Text(
            text = arguments.ifBlank { "{}" }.abbreviate(if (expanded) 1_200 else 140),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) 16 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    OutlinedTextField(
        value = arguments,
        onValueChange = onArgumentsChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("工具参数 JSON") },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        minLines = if (expanded) 4 else 2,
        maxLines = if (expanded) 12 else 4,
    )
}

@Composable
private fun ToolCardHeader(
    info: ToolVisualInfo,
    state: ToolCardState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(text = state.permissionLabel, tone = state.permissionTone)
        StatusPill(text = state.label, tone = state.tone)
        WorkbenchIconButton(
            icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            label = if (expanded) "收起工具详情" else "展开工具详情",
            onClick = onToggleExpanded,
        )
    }
}

@Composable
private fun ToolApprovalActions(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
            Text("拒绝")
        }
        Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
            Text("允许")
        }
    }
}

@Composable
private fun ToolPlanBody(
    onCopyArguments: () -> Unit,
    onCopyPlanBundle: () -> Unit,
) {
    InlineNotice(
        text = "工具计划已提交，执行结果会在后续工具卡中显示。",
        icon = Icons.Filled.HourglassEmpty,
        tone = StatusTone.Neutral,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyArguments) {
                    Text("复制计划参数")
                }
                OutlinedButton(onClick = onCopyPlanBundle) {
                    Text("复制计划包")
                }
            }
        },
    )
}

@Composable
private fun ToolRunningBody(onCancel: (() -> Unit)?) {
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    InlineNotice(
        text = "工具正在运行 ${elapsedSeconds.formatElapsedSeconds()}，结果会回写到当前聊天流。",
        icon = Icons.Filled.HourglassEmpty,
        tone = StatusTone.Neutral,
        action = {
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel) {
                    Text("停止")
                }
            }
        },
    )
}

internal fun Int.formatElapsedSeconds(): String =
    when {
        this < 60 -> "${this}s"
        else -> "${this / 60}m ${this % 60}s"
    }

@Composable
private fun ToolResultBody(
    toolName: String,
    arguments: String,
    result: String,
    outcome: ToolCallPanelOutcome,
    expanded: Boolean,
    onCopy: () -> Unit,
    onCopyArguments: () -> Unit,
    onCopyDebugBundle: () -> Unit,
    onRetryWithArguments: () -> Unit,
) {
    val tone = outcome.resultTone()
    val needsRecovery = outcome.needsRecoveryAction()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineNotice(
            text = outcome.resultDescription(),
            icon = if (outcome == ToolCallPanelOutcome.Completed) Icons.Filled.CheckCircle else Icons.Filled.Error,
            tone = tone,
            action = {
                WorkbenchIconButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制工具结果",
                    onClick = onCopy,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (needsRecovery) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyArguments,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制参数")
                }
                OutlinedButton(
                    onClick = onCopyDebugBundle,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制调试包")
                }
                Button(
                    onClick = onRetryWithArguments,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("修改参数")
                }
            }
        }
        Text(
            text = result.abbreviate(if (expanded) 3_000 else 240),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) 24 else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (needsRecovery && arguments.isNotBlank()) {
            Text(
                text = arguments.abbreviate(if (expanded) 1_200 else 180),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) 12 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun toolDebugBundle(
    toolName: String,
    arguments: String,
    result: String,
    isError: Boolean,
    displayName: String? = null,
    permissionLevel: ToolPermissionLevel? = null,
    statusLabel: String = if (isError) "失败" else "完成",
): String =
    buildString {
        val canonicalName = toolName.canonicalToolName()
        appendLine("工具：$canonicalName")
        if (toolName != canonicalName) appendLine("原始工具：$toolName")
        displayName?.takeIf { it.isNotBlank() }?.let { appendLine("显示名：$it") }
        appendLine("权限：${permissionLevel.displayLabel()}")
        appendLine("状态：$statusLabel")
        appendLine("参数：")
        appendLine(arguments.ifBlank { "{}" })
        appendLine("结果：")
        append(result)
    }

fun toolPlanBundle(
    toolName: String,
    displayName: String? = null,
    permissionLevel: ToolPermissionLevel? = null,
    arguments: String,
): String =
    buildString {
        val canonicalName = toolName.canonicalToolName()
        appendLine("工具计划：$canonicalName")
        if (toolName != canonicalName) appendLine("原始工具：$toolName")
        displayName?.takeIf { it.isNotBlank() }?.let { appendLine("显示名：$it") }
        appendLine("权限：${permissionLevel.displayLabel()}")
        appendLine("参数：")
        append(arguments.copyableToolArguments())
    }

internal fun String.copyableToolArguments(): String =
    ifBlank { "{}" }

private data class ToolCardState(
    val label: String,
    val description: String,
    val tone: StatusTone,
    val permissionLabel: String,
    val permissionTone: StatusTone,
)

private fun toolCardState(
    outcome: ToolCallPanelOutcome,
    permissionLevel: ToolPermissionLevel?,
): ToolCardState =
    when (outcome) {
        ToolCallPanelOutcome.Pending -> ToolCardState(
            label = "待授权",
            description = "确认工具计划和参数后再执行。",
            tone = StatusTone.Warning,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Streaming -> ToolCardState(
            label = "流式返回",
            description = "工具结果正在回写到聊天流。",
            tone = StatusTone.Accent,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Failed -> ToolCardState(
            label = "失败",
            description = "错误保留在聊天流，可复制日志或修改参数。",
            tone = StatusTone.Critical,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Denied -> ToolCardState(
            label = "已拒绝",
            description = "工具未执行，可修改参数后重新发起。",
            tone = StatusTone.Warning,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Cancelled -> ToolCardState(
            label = "已取消",
            description = "工具已停止，可复制日志或修改参数。",
            tone = StatusTone.Neutral,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Completed -> ToolCardState(
            label = "完成",
            description = "结果已回写，可继续追问。",
            tone = StatusTone.Success,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Planned -> ToolCardState(
            label = "已计划",
            description = "工具请求已进入执行流程。",
            tone = StatusTone.Neutral,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
        ToolCallPanelOutcome.Running -> ToolCardState(
            label = "运行中",
            description = "正在执行工具调用。",
            tone = StatusTone.Accent,
            permissionLabel = permissionLevel.displayLabel(),
            permissionTone = permissionLevel.statusTone(),
        )
    }

private data class ToolVisualInfo(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

@Composable
private fun toolVisualInfo(name: String, displayName: String?): ToolVisualInfo =
    when (name.lowercase().replace("-", "_")) {
        "web_search", "web_search_local" ->
            ToolVisualInfo(Icons.Outlined.Search, displayName ?: "联网搜索", MaterialTheme.colorScheme.primary)
        "code_sandbox", "local_js", "local_javascript", "javascript", "js" ->
            ToolVisualInfo(Icons.Outlined.Code, displayName ?: "代码执行", MaterialTheme.colorScheme.primary)
        "image_generation" ->
            ToolVisualInfo(Icons.Filled.Image, displayName ?: "图片生成", MaterialTheme.colorScheme.primary)
        "image_upload_to_model" ->
            ToolVisualInfo(Icons.Filled.Image, displayName ?: "图片发送给模型", MaterialTheme.colorScheme.primary)
        else -> ToolVisualInfo(Icons.Outlined.Build, displayName ?: name, MaterialTheme.colorScheme.primary)
    }

private fun ToolPermissionLevel?.displayLabel(): String =
    when (this) {
        ToolPermissionLevel.ReadOnly -> "低风险"
        ToolPermissionLevel.Network -> "联网"
        ToolPermissionLevel.Execute -> "执行"
        ToolPermissionLevel.HighRisk -> "高风险"
        null -> "未知风险"
    }

private fun ToolPermissionLevel?.statusTone(): StatusTone =
    when (this) {
        ToolPermissionLevel.ReadOnly -> StatusTone.Success
        ToolPermissionLevel.Network -> StatusTone.Accent
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        null,
        -> StatusTone.Warning
    }

internal fun String.inferredDisplayName(): String =
    when (canonicalToolName()) {
        "time" -> "本机时间"
        "text_transform" -> "文本转换"
        "code_diff_preview" -> "代码 Diff 预览"
        "web_search", "web_search_local" -> "联网搜索"
        "code_sandbox" -> "远端代码沙箱"
        "local_js" -> "本地 JavaScript"
        "file_read" -> "读取授权文件"
        "provider_connection_test" -> "Provider 连接测试"
        "image_generation" -> "图片生成"
        "image_upload_to_model" -> "图片发送给模型"
        else -> this
    }

internal fun String.inferredPermissionLevel(): ToolPermissionLevel? =
    when (canonicalToolName()) {
        "time",
        "text_transform",
        "code_diff_preview",
        -> ToolPermissionLevel.ReadOnly
        "web_search",
        "web_search_local",
        "provider_connection_test",
        "image_generation",
        -> ToolPermissionLevel.Network
        "code_sandbox" -> ToolPermissionLevel.Execute
        "local_js",
        "file_read",
        "image_upload_to_model",
        -> ToolPermissionLevel.HighRisk
        else -> null
    }

private fun String.abbreviate(maxLength: Int): String =
    if (length > maxLength) "${take(maxLength)}..." else this
