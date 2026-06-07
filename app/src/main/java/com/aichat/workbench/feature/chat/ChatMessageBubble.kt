package com.aichat.workbench.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.markdown.CodeArtifact
import com.aichat.workbench.ui.markdown.MarkdownMessageContent

@Composable
@Suppress("DEPRECATION")
internal fun MessageBubble(
    message: Message,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onGenerateDiff: (CodeArtifact) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by rememberSaveable(message.id.value) {
        mutableStateOf(
            message.role != MessageRole.Tool ||
                message.contentParts.any { it is MessagePart.Image },
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.role == MessageRole.User) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        Surface(
            color = messageContainerColor(message),
            contentColor = messageContentColor(message),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(
                message.containerWidthFraction(),
            ),
            tonalElevation = messageContainerElevation(message),
            border = messageContainerBorder(message),
        ) {
            Column(
                modifier = Modifier.padding(message.contentPadding()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (message.shouldShowHeader()) {
                    MessageHeader(
                        message = message,
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                    )
                }
                message.parentMessageId?.let {
                    Text(
                        text = "关联到 ${it.value.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    val images = message.contentParts.filterIsInstance<MessagePart.Image>()
                    if (images.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(images, key = { it.uri.take(80) }) { image ->
                                ChatImagePreview(
                                    image = image,
                                    modifier = Modifier.size(96.dp),
                                )
                            }
                        }
                    }
                    if (message.content.isBlank() && images.isEmpty()) {
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else if (message.content.isNotBlank()) {
                        MarkdownMessageContent(
                            text = message.content,
                            onGenerateDiff = onGenerateDiff,
                        )
                    }
                } else {
                    Text(
                        text = "工具详情已折叠",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message.errorSummary?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                MessageActionRow(
                    message = message,
                    onCopy = { clipboardManager.setText(AnnotatedString(message.content)) },
                    onEdit = onEdit,
                    onRetry = onRetry,
                )
            }
        }
    }
}

internal fun CodeArtifact.diffPrompt(): String =
    buildString {
        appendLine("请基于下面这段代码准备修改方案，并用 code_diff_preview 生成 Diff 预览。")
        appendLine("要求：先说明计划修改点，再按参数模板填充 modified；只展示 diff，不写入文件。")
        language?.takeIf { it.isNotBlank() }?.let { appendLine("语言：$it") }
        appendLine("工具：code_diff_preview")
        appendLine("参数模板：")
        appendLine("```json")
        appendLine(
            """{"fileName":"${language.toSnippetFileName()}","original":${content.jsonStringLiteral()},"modified":${content.jsonStringLiteral()}}""",
        )
        appendLine("```")
        appendLine()
        appendLine("```" + language.orEmpty())
        appendLine(content)
        appendLine("```")
    }.trim()

internal fun ToolCall.retryPrompt(): String =
    buildString {
        appendLine("请基于下面的工具调用参数重新规划并执行工具。")
        appendLine("工具：$name")
        appendLine("要求：如果参数有问题，先指出需要修改的字段；需要执行时重新发起工具调用。")
        appendLine()
        appendLine("```json")
        appendLine(arguments.ifBlank { "{}" })
        appendLine("```")
    }.trim()

@Composable
private fun MessageHeader(
    message: Message,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MessageHeaderPills(message = message)
        Spacer(modifier = Modifier.weight(1f))
        if (message.role == MessageRole.Tool) {
            WorkbenchIconButton(
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                label = if (expanded) "收起工具详情" else "展开工具详情",
                onClick = onToggleExpanded,
            )
        }
    }
}

@Composable
private fun MessageHeaderPills(message: Message) {
    if (message.role == MessageRole.Tool || message.role == MessageRole.System) {
        StatusPill(
            text = message.role.displayLabel(),
            tone = message.roleTone(),
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    if (message.status != MessageStatus.Completed) {
        StatusPill(
            text = message.status.displayLabel(),
            tone = message.statusTone(),
        )
    }
}

@Composable
internal fun MessageActionRow(
    message: Message,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(top = 0.dp),
    ) {
        item {
            WorkbenchIconButton(
                icon = Icons.Filled.ContentCopy,
                label = "复制消息",
                onClick = onCopy,
            )
        }
        if (message.role == MessageRole.User) {
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.Edit,
                    label = "编辑消息",
                    onClick = onEdit,
                )
            }
        }
        if (message.role == MessageRole.Assistant && message.status == MessageStatus.Failed) {
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.Replay,
                    label = "重试回复",
                    onClick = onRetry,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun messageContainerColor(message: Message) =
    when (message.role) {
        MessageRole.User -> MaterialTheme.colorScheme.primaryContainer
        MessageRole.Assistant -> Color.Transparent
        MessageRole.Tool -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        MessageRole.System -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
    }

@Composable
private fun messageContentColor(message: Message) =
    when (message.role) {
        MessageRole.User -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageRole.Assistant -> MaterialTheme.colorScheme.onSurface
        MessageRole.Tool -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageRole.System -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun messageContainerBorder(message: Message): BorderStroke? =
    when (message.role) {
        MessageRole.Tool -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
        MessageRole.System -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
        MessageRole.User,
        MessageRole.Assistant,
        -> null
    }

private fun messageContainerElevation(message: Message) =
    when (message.role) {
        MessageRole.User,
        MessageRole.Assistant,
        -> 0.dp
        MessageRole.Tool,
        MessageRole.System,
        -> 1.dp
    }

private fun Message.containerWidthFraction(): Float =
    when (role) {
        MessageRole.User -> 0.88f
        MessageRole.Assistant -> 1f
        MessageRole.Tool,
        MessageRole.System,
        -> 0.96f
    }

private fun Message.contentPadding(): PaddingValues =
    when (role) {
        MessageRole.Assistant -> PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        MessageRole.User,
        MessageRole.Tool,
        MessageRole.System,
        -> PaddingValues(14.dp)
    }

private fun Message.roleTone(): StatusTone =
    when (role) {
        MessageRole.User -> StatusTone.Accent
        MessageRole.Assistant -> StatusTone.Success
        MessageRole.Tool -> StatusTone.Warning
        MessageRole.System -> StatusTone.Neutral
    }

private fun Message.statusTone(): StatusTone =
    when (status) {
        MessageStatus.Completed -> StatusTone.Success
        MessageStatus.Failed -> StatusTone.Critical
        MessageStatus.Cancelled -> StatusTone.Warning
        MessageStatus.Streaming,
        MessageStatus.Pending,
        MessageStatus.Draft,
        MessageStatus.Compressed,
        -> StatusTone.Accent
    }

private fun String?.toSnippetFileName(): String =
    when (this?.trim()?.lowercase()) {
        "kotlin", "kt" -> "snippet.kt"
        "java" -> "snippet.java"
        "typescript", "ts" -> "snippet.ts"
        "javascript", "js" -> "snippet.js"
        "python", "py" -> "snippet.py"
        "go" -> "snippet.go"
        "rust", "rs" -> "snippet.rs"
        "swift" -> "snippet.swift"
        "sql" -> "snippet.sql"
        "json" -> "snippet.json"
        "markdown", "md" -> "snippet.md"
        else -> "snippet"
    }
