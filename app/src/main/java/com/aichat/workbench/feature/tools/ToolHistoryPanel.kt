package com.aichat.workbench.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun ToolHistorySection(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    onSendToChat: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val filteredHistory = state.filteredToolHistory.take(10)
    WorkbenchPanel(
        title = "运行历史",
        description = "按工具和状态筛选最近运行，复制输入摘要和输出用于调试。",
        icon = Icons.Filled.Info,
        trailing = {
            StatusPill(
                text = "${state.toolHistory.size} 条",
                tone = if (state.toolHistory.isEmpty()) StatusTone.Neutral else StatusTone.Accent,
            )
        },
    ) {
        ToolHistoryFilters(state = state, viewModel = viewModel)
        state.refilledToolName?.let { toolName ->
            RefilledToolInputCard(
                toolName = toolName,
                inputJson = state.refilledToolInputJson.orEmpty(),
                onCopyInput = {
                    clipboard.setText(AnnotatedString(state.refilledToolInputJson.orEmpty()))
                },
                onCopyChatInstruction = {
                    clipboard.setText(AnnotatedString(state.chatInstructionForRefilledTool().orEmpty()))
                },
                onSendToChat = {
                    state.chatInstructionForRefilledTool()?.let(onSendToChat)
                },
            )
        }
        if (filteredHistory.isEmpty()) {
            InlineNotice(
                text = "暂无符合条件的工具运行记录。",
                icon = Icons.Filled.Info,
                tone = StatusTone.Neutral,
            )
        } else {
            filteredHistory.forEach { result ->
                ToolHistoryRow(
                    result = result,
                    canRerun = state.canRerunToolResult(result),
                    canRefill = state.canRefillToolResult(result),
                    canSendToChat = state.canSendToolResultToChat(result),
                    onRerun = { viewModel.rerunToolResult(result) },
                    onRefill = { viewModel.refillToolResult(result) },
                    onCopyChatInstruction = {
                        clipboard.setText(AnnotatedString(state.chatInstructionForToolResult(result)))
                    },
                    onSendToChat = {
                        onSendToChat(state.chatInstructionForToolResult(result))
                    },
                    onCopyInput = {
                        clipboard.setText(AnnotatedString(result.rawInputJson ?: result.inputSummary))
                    },
                    onCopyOutput = {
                        clipboard.setText(AnnotatedString(result.rawOutputJson ?: result.output.asPlainText()))
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolHistoryFilters(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    val conversationIds = state.toolHistory.mapNotNull { it.conversationId?.value }.distinct().sorted()
    val toolNames = state.toolHistory.map { it.toolName.canonicalToolName() }.distinct().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversationIds.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SelectableFilterButton(
                        selected = state.toolHistoryConversationFilter == null,
                        text = "全部会话",
                        onClick = { viewModel.updateToolHistoryConversationFilter(null) },
                    )
                }
                items(conversationIds, key = { it }) { conversationId ->
                    SelectableFilterButton(
                        selected = state.toolHistoryConversationFilter == conversationId,
                        text = conversationId.historyLabel("会话"),
                        onClick = { viewModel.updateToolHistoryConversationFilter(conversationId) },
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SelectableFilterButton(
                    selected = state.toolHistoryToolFilter == null,
                    text = "全部工具",
                    onClick = { viewModel.updateToolHistoryToolFilter(null) },
                )
            }
            items(toolNames, key = { it }) { toolName ->
                SelectableFilterButton(
                    selected = state.toolHistoryToolFilter == toolName,
                    text = toolName,
                    onClick = { viewModel.updateToolHistoryToolFilter(toolName) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SelectableFilterButton(
                    selected = state.toolHistoryStatusFilter == null,
                    text = "全部状态",
                    onClick = { viewModel.updateToolHistoryStatusFilter(null) },
                )
            }
            items(ToolStatus.values().toList(), key = { it.name }) { status ->
                SelectableFilterButton(
                    selected = state.toolHistoryStatusFilter == status,
                    text = status.displayLabel(),
                    onClick = { viewModel.updateToolHistoryStatusFilter(status) },
                )
            }
        }
    }
}

@Composable
internal fun SelectableFilterButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolHistoryRow(
    result: ToolResult,
    canRerun: Boolean,
    canRefill: Boolean,
    canSendToChat: Boolean,
    onRerun: () -> Unit,
    onRefill: () -> Unit,
    onCopyChatInstruction: () -> Unit,
    onSendToChat: () -> Unit,
    onCopyInput: () -> Unit,
    onCopyOutput: () -> Unit,
) {
    var showRawPayload by rememberSaveable(result.id.value) { mutableStateOf(false) }
    val rawInput = result.rawInputJson ?: result.inputSummary
    val rawOutput = result.rawOutputJson ?: result.output.asPlainText()
    ToolResultContainer(
        title = result.toolName,
        icon = result.permissionLevel.permissionIcon(),
        trailing = {
            StatusPill(text = result.status.displayLabel(), tone = result.status.tone())
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    StatusPill(text = result.permissionLevel.displayLabel(), tone = result.permissionLevel.permissionTone())
                }
                result.conversationId?.let { conversationId ->
                    item {
                        StatusPill(text = conversationId.value.historyLabel("会话"), tone = StatusTone.Neutral)
                    }
                }
                item {
                    StatusPill(text = result.startedAt.toString(), tone = StatusTone.Neutral)
                }
                result.finishedAt?.let { finishedAt ->
                    item {
                        StatusPill(text = "完成 $finishedAt", tone = StatusTone.Neutral)
                    }
                }
                result.durationMs?.let { durationMs ->
                    item {
                        StatusPill(text = "${durationMs} ms", tone = StatusTone.Neutral)
                    }
                }
                result.canceledAt?.let { canceledAt ->
                    item {
                        StatusPill(text = "取消 $canceledAt", tone = StatusTone.Warning)
                    }
                }
            }
            MetadataRow(label = "输入", value = result.inputSummary.ifBlank { "(空)" })
            result.error?.let { error ->
                InlineNotice(
                    text = error.diagnosticLabel(),
                    icon = Icons.Filled.Security,
                    tone = result.status.errorTone(),
                )
            }
            result.recoveryHintForHistory()?.let { recoveryHint ->
                InlineNotice(
                    text = recoveryHint,
                    icon = Icons.Filled.Info,
                    tone = result.status.errorTone(),
                )
            }
            if (!canRerun && canRefill && result.status.isUserStopped()) {
                InlineNotice(
                    text = "这条记录未实际完成执行，已保留原始参数。请先回填参数，确认后再从聊天或调试入口重新发起。",
                    icon = Icons.Filled.Info,
                    tone = result.status.tone(),
                )
            }
            OutputText(
                label = "输出",
                value = result.output.asPlainText().take(MAX_TOOL_HISTORY_OUTPUT_PREVIEW_CHARS),
            )
            if (showRawPayload) {
                OutputText(
                    label = "原始输入 JSON",
                    value = rawInput.rawPayloadPreview(),
                )
                OutputText(
                    label = "原始输出 JSON",
                    value = rawOutput.rawPayloadPreview(),
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(
                        onClick = onRerun,
                        enabled = canRerun,
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (canRerun) "重跑" else "暂不支持重跑")
                    }
                }
                if (canRefill) {
                    item {
                        OutlinedButton(onClick = onRefill) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "回填参数")
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { showRawPayload = !showRawPayload }) {
                        Icon(
                            imageVector = if (showRawPayload) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (showRawPayload) "隐藏原始" else "查看原始")
                    }
                }
                item {
                    OutlinedButton(onClick = onCopyChatInstruction) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (canSendToChat) "复制聊天指令" else "复制诊断")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onSendToChat,
                        enabled = canSendToChat,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (canSendToChat) "带入聊天" else "不可带入")
                    }
                }
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
}
