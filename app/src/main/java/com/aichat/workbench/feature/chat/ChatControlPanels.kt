package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone

@Composable
internal fun ChatControlSheet(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
    onRequestClearContext: () -> Unit,
    onRequestDeleteConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ConversationTitleSection(state = state, viewModel = viewModel)
        }
        item {
            ModelSelectorSection(
                state = state,
                viewModel = viewModel,
                onOpenProviders = onOpenProviders,
            )
        }
        item {
            DangerActions(
                messageCount = state.selectedConversationMessageCount,
                canClearContext = state.selectedConversationMessageCount > 0 && !state.isGenerating,
                hasConversation = state.selectedConversationId != null,
                onRequestClearContext = onRequestClearContext,
                onRequestDeleteConversation = onRequestDeleteConversation,
            )
        }
        item {
            NewConversationButton(onClick = viewModel::createConversation)
        }
    }
}

@Composable
private fun ConversationTitleSection(
    state: ChatUiState,
    viewModel: ChatViewModel,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "对话标题",
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.isGenerating) {
                StatusPill(text = "生成中", tone = StatusTone.Accent)
            }
        }
        Text(
            text = state.titleDraft.ifBlank { "未命名" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelSelectorSection(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "模型",
            description = "选择当前对话使用的模型。",
        )
        if (state.providers.none { it.enabled }) {
            InlineNotice(
                text = "请先配置模型连接",
                icon = Icons.Filled.Tune,
                tone = StatusTone.Warning,
            ) {
                TextButton(
                    onClick = onOpenProviders,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = "配置")
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.providers, key = { it.id.value }) { provider ->
                    FilterChip(
                        selected = state.selectedProviderId == provider.id.value,
                        onClick = { viewModel.selectProvider(provider.id.value) },
                        label = {
                            Text(
                                text = provider.name,
                                modifier = Modifier.widthIn(max = 160.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (state.selectedProviderId == provider.id.value) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        enabled = provider.enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun DangerActions(
    messageCount: Int,
    canClearContext: Boolean,
    hasConversation: Boolean,
    onRequestClearContext: () -> Unit,
    onRequestDeleteConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuietSectionHeader(
            title = "操作",
            description = "管理当前对话内容。",
        )
        QuietListRow(
            title = "清空上下文",
            description = if (messageCount == 0) "当前没有消息" else "删除 $messageCount 条消息，保留会话",
            icon = Icons.Filled.ClearAll,
            onClick = onRequestClearContext,
            enabled = canClearContext,
        )
        QuietListRow(
            title = "删除对话",
            description = "删除本地历史和消息",
            icon = Icons.Filled.Delete,
            onClick = onRequestDeleteConversation,
            enabled = hasConversation,
        )
    }
}

@Composable
private fun NewConversationButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "新建对话")
    }
}
