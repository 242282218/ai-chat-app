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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun ToolCallPanel(
    toolCall: ToolCall,
    result: String?,
    isError: Boolean,
    isPending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(toolCall.id.value) { mutableStateOf(isPending || isError) }
    val info = toolVisualInfo(toolCall.name)
    val state = toolCardState(result = result, isError = isError, isPending = isPending)
    val clipboard = LocalClipboardManager.current

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
            Text(
                text = toolCall.arguments.ifBlank { "{}" }.abbreviate(if (expanded) 1_200 else 140),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) 16 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                isPending -> ToolApprovalActions(onApprove = onApprove, onDeny = onDeny)
                result != null -> ToolResultBody(
                    result = result,
                    isError = isError,
                    expanded = expanded,
                    onCopy = { clipboard.setText(AnnotatedString(result)) },
                )
                else -> ToolRunningBody()
            }
        }
    }
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
private fun ToolRunningBody() {
    InlineNotice(
        text = "工具正在运行，结果会回写到当前聊天流。",
        icon = Icons.Filled.HourglassEmpty,
        tone = StatusTone.Neutral,
    )
}

@Composable
private fun ToolResultBody(
    result: String,
    isError: Boolean,
    expanded: Boolean,
    onCopy: () -> Unit,
) {
    val tone = if (isError) StatusTone.Critical else StatusTone.Success
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineNotice(
            text = if (isError) "工具执行失败，可复制日志后修改参数重试。" else "工具执行完成，可复制结果或继续让模型处理。",
            icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
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
        Text(
            text = result.abbreviate(if (expanded) 3_000 else 240),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) 24 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ToolCardState(
    val label: String,
    val description: String,
    val tone: StatusTone,
)

private fun toolCardState(
    result: String?,
    isError: Boolean,
    isPending: Boolean,
): ToolCardState =
    when {
        isPending -> ToolCardState("待授权", "确认工具计划和参数后再执行。", StatusTone.Warning)
        isError -> ToolCardState("失败", "错误保留在聊天流，可复制日志。", StatusTone.Critical)
        result != null -> ToolCardState("完成", "结果已回写，可继续追问。", StatusTone.Success)
        else -> ToolCardState("运行中", "正在执行工具调用。", StatusTone.Accent)
    }

private data class ToolVisualInfo(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

@Composable
private fun toolVisualInfo(name: String): ToolVisualInfo =
    when (name) {
        "web_search" -> ToolVisualInfo(Icons.Outlined.Search, "联网搜索", MaterialTheme.colorScheme.primary)
        "code_sandbox", "local_js" -> ToolVisualInfo(Icons.Outlined.Code, "代码执行", MaterialTheme.colorScheme.primary)
        "image_generation" -> ToolVisualInfo(Icons.Filled.Image, "图片生成", MaterialTheme.colorScheme.primary)
        else -> ToolVisualInfo(Icons.Outlined.Build, name, MaterialTheme.colorScheme.primary)
    }

private fun String.abbreviate(maxLength: Int): String =
    if (length > maxLength) "${take(maxLength)}..." else this
