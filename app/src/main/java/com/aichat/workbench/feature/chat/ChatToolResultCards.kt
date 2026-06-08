package com.aichat.workbench.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.feature.image.saveGeneratedImage
import com.aichat.workbench.feature.image.shareGeneratedImage
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.ui.component.InlineImageBubble
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.ToolCallPanelOutcome
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun ToolImageResultRow(message: Message) {
    val images = message.contentParts.filterIsInstance<MessagePart.Image>()
    if (images.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(
            count = images.size,
            key = { index -> chatLazyItemKey("tool-image", images[index].uri, index) },
        ) { index ->
            val image = images[index]
            InlineImageBubble(
                imageUrl = image.uri,
                prompt = extractImagePrompt(message.toolResult) ?: "生成图片",
                isLoading = false,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

// LocalClipboardManager is deprecated but still the standard way to access clipboard in Compose
@Composable
@Suppress("DEPRECATION")
internal fun ToolImageResultActions(
    message: Message,
    onReusePrompt: (String) -> Unit,
    onRegenerate: (String) -> Unit,
) {
    if (message.contentParts.none { it is MessagePart.Image }) return
    val imagePath = message.contentParts
        .filterIsInstance<MessagePart.Image>()
        .firstNotNullOfOrNull { it.uri.toLocalImagePathOrNull() }
    val actions = imageResultActionState(
        toolResult = message.toolResult,
        fallbackContent = message.content,
        imagePath = imagePath,
    )
    if (!actions.hasAnyActions) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        actions.prompt?.let { prompt ->
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制图片提示词",
                    onClick = { clipboard.setText(AnnotatedString(prompt)) },
                )
            }
            item {
                OutlinedButton(onClick = { onReusePrompt(prompt) }) {
                    Text(text = "复用提示词")
                }
            }
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.Replay,
                    label = "重新生成图片",
                    onClick = { onRegenerate(prompt) },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        actions.imagePath?.let { path ->
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.Share,
                    label = "分享图片",
                    onClick = { shareGeneratedImage(context, path) },
                )
            }
            item {
                WorkbenchIconButton(
                    icon = Icons.Filled.SaveAlt,
                    label = "保存图片",
                    onClick = { saveGeneratedImage(context, path.fileStem(), path) },
                )
            }
        }
    }
}

@Composable
internal fun ToolSearchCitationRow(
    toolName: String,
    toolResult: String?,
) {
    val citations = extractSearchCitations(toolName, toolResult).take(MAX_INLINE_SEARCH_CITATIONS)
    if (citations.isEmpty()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuietSectionHeader(
            title = "来源",
            description = "${citations.size} 个可追溯结果",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                count = citations.size,
                key = { index -> chatLazyItemKey("search-citation", citations[index].url, index) },
            ) { index ->
                val citation = citations[index]
                OutlinedButton(onClick = { context.openUrl(citation.url) }) {
                    Column(
                        modifier = Modifier.widthIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = citation.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = citation.summary.ifBlank { citation.source.ifBlank { citation.url } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                }
            }
        }
    }
}

@Composable
internal fun ToolStructuredResultCard(
    toolName: String,
    toolResult: String?,
    onContinue: (String, String?) -> Unit,
    onRecoverLocalJs: (String?) -> Unit,
    onRecoverTool: (String, String?, String) -> Unit,
    onChooseFile: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val searchResult = extractSearchResultSummary(toolName, toolResult)
    if (searchResult != null) {
        SearchResultCard(
            result = searchResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(toolName, toolResult, searchResult.recoveryReason())
            },
            onOpenTools = onOpenTools,
        )
        return
    }

    val localJsResult = extractLocalJsResult(toolName, toolResult)
    if (localJsResult != null) {
        LocalJsResultCard(
            result = localJsResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = { onRecoverLocalJs(toolResult) },
        )
        return
    }

    val fileReadResult = extractFileReadResult(toolName, toolResult)
    if (fileReadResult != null) {
        FileReadResultCard(
            result = fileReadResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(
                    toolName,
                    toolResult,
                    "文件读取结果不完整，需要重新选择文件、缩小读取范围，或改用受支持的文本格式。",
                )
            },
            onChooseFile = onChooseFile,
        )
        return
    }

    val textTransformResult = extractTextTransformResult(toolName, toolResult)
    if (textTransformResult != null) {
        TextTransformResultCard(
            result = textTransformResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(toolName, toolResult, "文本转换结果已截断，需要缩小输入、调整正则或提高输出限制。")
            },
        )
        return
    }

    val codeDiffPreviewResult = extractCodeDiffPreviewResult(toolName, toolResult)
    if (codeDiffPreviewResult != null) {
        CodeDiffPreviewResultCard(
            result = codeDiffPreviewResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(toolName, toolResult, "Diff 没有变化，需要重新确认 original 和 modified 是否不同。")
            },
        )
        return
    }

    val providerConnectionTestResult = extractProviderConnectionTestResult(toolName, toolResult)
    if (providerConnectionTestResult != null) {
        ProviderConnectionTestResultCard(
            result = providerConnectionTestResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(
                    toolName,
                    toolResult,
                    providerConnectionTestResult.recoveryReason(),
                )
            },
            onOpenProviders = onOpenProviders,
        )
        return
    }

    val toolErrorResult = extractToolErrorResult(toolResult)
    if (toolErrorResult != null) {
        ToolErrorResultCard(
            toolName = toolName,
            result = toolErrorResult,
            onContinue = { onContinue(toolName, toolResult) },
            onRecover = {
                onRecoverTool(toolName, toolResult, toolErrorResult.recoveryHint(toolName))
            },
            onOpenTools = onOpenTools,
            onOpenProviders = onOpenProviders,
        )
    }
}

@Composable
@Suppress("DEPRECATION")
private fun StructuredResultTrailing(
    status: @Composable () -> Unit,
    copyText: String,
    copyLabel: String,
    onContinue: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        status()
        WorkbenchIconButton(
            icon = Icons.Filled.ContentCopy,
            label = copyLabel,
            onClick = { clipboard.setText(AnnotatedString(copyText)) },
        )
        WorkbenchIconButton(
            icon = Icons.Filled.PlayArrow,
            label = "继续处理",
            onClick = onContinue,
            tint = MaterialTheme.colorScheme.primary,
        )
        extraAction?.invoke()
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val isEmpty = result.resultCount == 0
    WorkbenchPanel(
        title = "搜索结果",
        description = result.query,
        icon = Icons.Filled.Search,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    StatusPill(
                        text = if (isEmpty) "无结果" else "${result.resultCount} 条",
                        tone = if (isEmpty) StatusTone.Warning else StatusTone.Success,
                    )
                },
                copyText = result.query,
                copyLabel = "复制搜索词",
                onContinue = onContinue,
                extraAction = {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Tune,
                        label = if (isEmpty) "换关键词重搜" else "调整搜索",
                        onClick = onRecover,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (isEmpty) {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Security,
                            label = "打开工具设置",
                            onClick = onOpenTools,
                        )
                    }
                },
            )
        },
    ) {
        Text(
            text = if (isEmpty) {
                "没有可引用来源。请换关键词重搜，或检查搜索 Provider 配置。"
            } else {
                "继续处理时必须保留来源 URL，并把关键结论标注到对应来源。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocalJsResultCard(
    result: LocalJsResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
) {
    WorkbenchPanel(
        title = "JavaScript 结果",
        description = "${result.durationMs}ms",
        icon = Icons.Filled.Timer,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    when {
                        result.timedOut -> StatusPill(text = "超时", tone = StatusTone.Critical)
                        result.truncated -> StatusPill(text = "已截断", tone = StatusTone.Warning)
                        else -> StatusPill(text = "完成", tone = StatusTone.Success)
                    }
                },
                copyText = result.output,
                copyLabel = "复制 JavaScript 输出",
                onContinue = onContinue,
                extraAction = if (result.timedOut || result.truncated) {
                    {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "调整 JS 重跑",
                            onClick = onRecover,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        },
    ) {
        Text(
            text = result.output.ifBlank { "(无输出)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileReadResultCard(
    result: FileReadResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
    onChooseFile: () -> Unit,
) {
    val statusTone = when {
        result.unsupportedReason != null -> StatusTone.Warning
        result.truncated -> StatusTone.Warning
        result.status == "completed" -> StatusTone.Success
        else -> StatusTone.Neutral
    }
    val needsRecovery = result.unsupportedReason != null || result.truncated
    WorkbenchPanel(
        title = result.fileName,
        description = listOfNotNull(result.mimeType, result.sizeBytes?.formatBytes()).joinToString(" / ")
            .ifBlank { "授权文件" },
        icon = Icons.Filled.Archive,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(
                            text = when {
                                result.unsupportedReason != null -> "不支持"
                                result.truncated -> "已截断"
                                result.status == "completed" -> "已读取"
                                else -> result.status.ifBlank { "文件" }
                            },
                            tone = statusTone,
                        )
                        StatusPill(
                            text = result.modelContextLabel(),
                            tone = result.modelContextTone().toStatusTone(),
                        )
                    }
                },
                copyText = result.unsupportedReason ?: result.preview ?: "",
                copyLabel = "复制文件预览",
                onContinue = onContinue,
                extraAction = {
                    if (needsRecovery) {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "调整读取任务",
                            onClick = onRecover,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    WorkbenchIconButton(
                        icon = Icons.Filled.Archive,
                        label = "重新选择文件",
                        onClick = onChooseFile,
                    )
                },
            )
        },
    ) {
        Text(
            text = result.unsupportedReason ?: result.preview ?: "(无文本预览)",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextTransformResultCard(
    result: TextTransformResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
) {
    WorkbenchPanel(
        title = "文本转换",
        description = "${result.operation} / ${result.inputLength} 字符",
        icon = Icons.Filled.Edit,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    when {
                        result.truncated -> StatusPill(text = "已截断", tone = StatusTone.Warning)
                        result.validJson == true -> StatusPill(text = "JSON 有效", tone = StatusTone.Success)
                        result.matches.isNotEmpty() ->
                            StatusPill(text = "${result.matches.size} 个匹配", tone = StatusTone.Accent)
                        else -> StatusPill(text = "完成", tone = StatusTone.Success)
                    }
                },
                copyText = result.previewText(),
                copyLabel = "复制文本转换结果",
                onContinue = onContinue,
                extraAction = if (result.truncated) {
                    {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "调整转换任务",
                            onClick = onRecover,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        },
    ) {
        Text(
            text = result.previewText(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CodeDiffPreviewResultCard(
    result: CodeDiffPreviewResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
) {
    val hasNoChanges = result.additions == 0 && result.deletions == 0
    WorkbenchPanel(
        title = result.fileName,
        description = "+${result.additions} / -${result.deletions}",
        icon = Icons.Filled.Edit,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    StatusPill(
                        text = if (hasNoChanges) "无变化" else "Diff",
                        tone = if (hasNoChanges) {
                            StatusTone.Neutral
                        } else {
                            StatusTone.Accent
                        },
                    )
                },
                copyText = result.diff,
                copyLabel = "复制 Diff",
                onContinue = onContinue,
                extraAction = if (hasNoChanges) {
                    {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "调整 Diff 任务",
                            onClick = onRecover,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        },
    ) {
        Text(
            text = result.diff,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 14,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProviderConnectionTestResultCard(
    result: ProviderConnectionTestResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    WorkbenchPanel(
        title = result.providerName,
        description = listOfNotNull(
            result.providerType.ifBlank { null },
            result.defaultModel,
            result.statusCode?.let { "HTTP $it" },
        ).joinToString(" / ").ifBlank { "Provider 连接测试" },
        icon = Icons.Filled.Tune,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    StatusPill(
                        text = when {
                            !result.enabled -> "未启用"
                            result.ok -> "连接成功"
                            else -> "连接失败"
                        },
                        tone = when {
                            !result.enabled -> StatusTone.Warning
                            result.ok -> StatusTone.Success
                            else -> StatusTone.Critical
                        },
                    )
                },
                copyText = result.diagnosticText(),
                copyLabel = "复制 Provider 诊断",
                onContinue = onContinue,
                extraAction = {
                    if (!result.enabled || !result.ok) {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "调整测试任务",
                            onClick = onRecover,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        WorkbenchIconButton(
                            icon = Icons.Filled.Tune,
                            label = "打开 Provider 设置",
                            onClick = onOpenProviders,
                        )
                    }
                },
            )
        },
    ) {
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolErrorResultCard(
    toolName: String,
    result: ToolErrorResultSummary,
    onContinue: () -> Unit,
    onRecover: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    WorkbenchPanel(
        title = "工具失败诊断",
        description = listOfNotNull(
            result.statusCode?.let { "HTTP $it" },
            result.code,
        ).joinToString(" / "),
        icon = Icons.Filled.Info,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailing = {
            StructuredResultTrailing(
                status = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(
                            text = if (result.retryable == true) "可重试" else "失败",
                            tone = result.statusTone(),
                        )
                        result.statusCode?.let {
                            StatusPill(text = "HTTP $it", tone = result.statusTone())
                        }
                    }
                },
                copyText = result.diagnosticText(toolName),
                copyLabel = "复制工具诊断",
                onContinue = onContinue,
                extraAction = {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Tune,
                        label = "调整工具参数",
                        onClick = onRecover,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (toolName.shouldOfferToolSettingsForError(result)) {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Security,
                            label = "打开工具设置",
                            onClick = onOpenTools,
                        )
                    }
                    if (toolName.shouldOfferProviderSettingsForError(result)) {
                        WorkbenchIconButton(
                            icon = Icons.Filled.Info,
                            label = "打开 Provider 设置",
                            onClick = onOpenProviders,
                        )
                    }
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.recoveryHint(toolName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ProviderConnectionTestResultSummary.recoveryReason(): String =
    when {
        !enabled -> "Provider 未启用，需要先启用或切换到可用 Provider。"
        statusCode == 401 -> "Provider 鉴权失败，需要检查已保存的 API Key、Base URL 和模型配置。"
        statusCode == 429 -> "Provider 测试被限流，需要稍后重试或切换 Provider/模型。"
        statusCode != null && statusCode in 500..599 -> "Provider 服务端异常，需要稍后重试或切换 Provider/模型。"
        !ok -> message.ifBlank { "Provider 连接失败，需要检查配置后重试。" }
        else -> "Provider 连接测试已完成，如需复核请重新规划测试参数。"
    }

internal fun List<Message>.findToolCall(toolCallId: ToolCallId?): ToolCall? {
    if (toolCallId == null) return null
    return firstNotNullOfOrNull { message ->
        message.toolCalls.firstOrNull { it.id == toolCallId }
    }
}

internal fun Message.toolPanelOutcome(
    isPending: Boolean,
    toolResultText: String?,
): ToolCallPanelOutcome =
    toolPanelOutcomeForMessage(
        status = status,
        isPending = isPending,
        toolResultText = toolResultText,
    )

internal fun toolPanelOutcomeForMessage(
    status: MessageStatus,
    isPending: Boolean,
    toolResultText: String?,
): ToolCallPanelOutcome =
    when {
        isPending -> ToolCallPanelOutcome.Pending
        toolResultText.isToolDeniedResult() -> ToolCallPanelOutcome.Denied
        status == MessageStatus.Cancelled -> ToolCallPanelOutcome.Cancelled
        status == MessageStatus.Failed -> ToolCallPanelOutcome.Failed
        status == MessageStatus.Streaming -> ToolCallPanelOutcome.Streaming
        toolResultText != null -> ToolCallPanelOutcome.Completed
        else -> ToolCallPanelOutcome.Running
    }

private fun String?.isToolDeniedResult(): Boolean =
    this?.contains("tool_denied") == true ||
        this?.contains("用户拒绝执行工具") == true

private fun Long.formatBytes(): String =
    when {
        this < 1024L -> "$this B"
        this < 1024L * 1024L -> "${this / 1024L} KB"
        else -> "${this / (1024L * 1024L)} MB"
    }

private fun FileReadModelContextTone.toStatusTone(): StatusTone =
    when (this) {
        FileReadModelContextTone.Success -> StatusTone.Success
        FileReadModelContextTone.Warning -> StatusTone.Warning
        FileReadModelContextTone.Neutral -> StatusTone.Neutral
    }

private fun TextTransformResultSummary.previewText(): String =
    when {
        output != null -> output
        matches.isNotEmpty() -> matches.joinToString(separator = "\n")
        else -> "(无输出)"
    }

private fun ToolErrorResultSummary.statusTone(): StatusTone =
    when {
        statusCode == 429 -> StatusTone.Warning
        retryable == true -> StatusTone.Warning
        else -> StatusTone.Critical
    }

internal fun String.shouldOfferToolSettingsForError(error: ToolErrorResultSummary): Boolean {
    val canonicalName = canonicalToolName()
    if (error.code in TOOL_CONFIGURATION_ERROR_CODES) return true
    if (canonicalName != "web_search" && canonicalName != "web_search_local") return false
    return error.statusCode == 401 ||
        error.statusCode == 429 ||
        error.code.contains("search", ignoreCase = true) ||
        error.message.contains("搜索", ignoreCase = true)
}

internal fun String.shouldOfferProviderSettingsForError(error: ToolErrorResultSummary): Boolean {
    val canonicalName = canonicalToolName()
    if (canonicalName != "image_generation" && canonicalName != "provider_connection_test") return false
    return error.statusCode == 401 ||
        error.statusCode == 429 ||
        error.statusCode != null && error.statusCode in 500..599 ||
        error.code.contains("provider", ignoreCase = true) ||
        error.message.contains("Provider", ignoreCase = true) ||
        error.message.contains("API Key", ignoreCase = true)
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
