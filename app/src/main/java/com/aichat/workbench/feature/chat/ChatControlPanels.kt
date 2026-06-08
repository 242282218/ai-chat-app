package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
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
    onRequestArchiveConversation: () -> Unit,
    onRequestDeleteConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            QuietSectionHeader(
                title = "控制",
                description = "会话、模型、提示词、参数和危险操作。",
            )
        }
        item {
            ConversationStrip(
                state = state,
                viewModel = viewModel,
                modifier = Modifier,
            )
        }
        item {
            if (state.providers.none { it.enabled }) {
                InlineNotice(
                    text = "添加模型连接后才能发送消息，请求会从本机直接发送到你的接口地址。",
                    icon = Icons.Filled.Tune,
                    tone = StatusTone.Warning,
                ) {
                    TextButton(onClick = onOpenProviders) {
                        Text(text = "配置")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietSectionHeader(
                        title = "模型连接",
                        description = "选择当前对话使用的模型连接。",
                    )
                    ProviderStrip(state = state, viewModel = viewModel, modifier = Modifier)
                }
            }
        }
        item {
            ChatSettingsPanel(state = state, viewModel = viewModel, modifier = Modifier)
        }
        item {
            PromptPresetStrip(state = state, viewModel = viewModel, modifier = Modifier)
        }
        item {
            BuiltInSkillStrip(state = state, viewModel = viewModel, modifier = Modifier)
        }
        item {
            LongTermMemoryPanel(
                state = state,
                viewModel = viewModel,
                selectedConversation = selectedConversation,
                modifier = Modifier,
            )
        }
        item {
            DangerActions(
                messageCount = state.selectedConversationMessageCount,
                canClearContext = state.selectedConversationMessageCount > 0 && !state.isGenerating,
                hasConversation = selectedConversation != null,
                onRequestClearContext = onRequestClearContext,
                onRequestArchiveConversation = onRequestArchiveConversation,
                onRequestDeleteConversation = onRequestDeleteConversation,
            )
        }
    }
}

@Composable
private fun LongTermMemoryPanel(
    state: ChatUiState,
    viewModel: ChatViewModel,
    selectedConversation: Conversation?,
    modifier: Modifier = Modifier,
) {
    val isSensitive = state.sensitiveDraft || selectedConversation?.isSensitive == true
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "长期记忆",
            description = "${state.memories.size} 条用户手动保存的事实",
            trailing = {
                state.memoryStatus?.let {
                    StatusPill(text = it, tone = StatusTone.Success)
                }
            },
        )
        QuietListRow(
            title = "保存当前输入为记忆",
            description = when {
                state.input.isBlank() -> "先在输入框写入要保存的事实"
                isSensitive -> "敏感会话不写入长期记忆"
                state.isGenerating -> "生成结束后再保存"
                else -> "仅保存输入框内容，不自动提取聊天历史"
            },
            icon = Icons.Filled.Save,
            onClick = viewModel::saveDraftAsMemory,
            enabled = state.input.isNotBlank() && !isSensitive && !state.isGenerating,
        )
        if (state.memories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.memories.take(3), key = { it.id.value }) { memory ->
                    MemoryPreviewPill(memory = memory)
                }
            }
        }
    }
}

@Composable
private fun MemoryPreviewPill(memory: MemoryItem) {
    StatusPill(
        text = memory.content.trim().ifBlank { "空记忆" }.take(28),
        tone = StatusTone.Neutral,
    )
}

@Composable
private fun ConversationStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuietSectionHeader(
            title = "当前会话",
            description = state.titleDraft.ifBlank { "未命名" },
            trailing = {
                if (state.isGenerating) {
                    StatusPill(text = "生成中", tone = StatusTone.Accent)
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.conversations, key = { it.id.value }) { conversation ->
                ConversationChip(
                    conversation = conversation,
                    selected = state.selectedConversationId == conversation.id,
                    onClick = { viewModel.selectConversation(conversation.id) },
                )
            }
        }
        val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
        if (selectedConversation != null && state.shouldShowConversationMetadata(selectedConversation)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.selectedConversationMessageCount > 0) {
                    item {
                        StatusPill(text = "${state.selectedConversationMessageCount} 条消息", tone = StatusTone.Neutral)
                    }
                }
                if (selectedConversation.isTemporary) {
                    item {
                        StatusPill(text = "临时", tone = StatusTone.Warning)
                    }
                }
                if (selectedConversation.isSensitive) {
                    item {
                        StatusPill(text = "敏感", tone = StatusTone.Critical)
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.titleDraft,
                onValueChange = viewModel::updateTitleDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "标题") },
                singleLine = true,
            )
            Button(
                onClick = viewModel::renameSelectedConversation,
                enabled = selectedConversation != null &&
                    state.titleDraft.isNotBlank() &&
                    state.titleDraft.trim() != selectedConversation.title,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存标题")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::createConversation,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "新建")
            }
            OutlinedButton(
                onClick = viewModel::createTemporaryConversation,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.Timer, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "临时")
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
    onRequestArchiveConversation: () -> Unit,
    onRequestDeleteConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuietSectionHeader(
            title = "危险操作",
            description = "这些操作会改变或删除当前会话内容。",
        )
        QuietListRow(
            title = "清空上下文",
            description = if (messageCount == 0) "当前没有消息" else "删除 $messageCount 条消息，保留会话",
            icon = Icons.Filled.ClearAll,
            onClick = onRequestClearContext,
            enabled = canClearContext,
        )
        QuietListRow(
            title = "归档对话",
            description = "从当前会话列表隐藏",
            icon = Icons.Filled.Archive,
            onClick = onRequestArchiveConversation,
            enabled = hasConversation,
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
private fun ConversationChip(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(
                modifier = Modifier.widthIn(max = 168.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                conversation.chipStatusText()?.let { statusText ->
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

@Composable
private fun ProviderStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(state.providers, key = { it.id.value }) { provider ->
            AssistChip(
                onClick = { viewModel.selectProvider(provider.id.value) },
                label = {
                    Text(
                        text = provider.name,
                        modifier = Modifier.widthIn(max = 180.dp),
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

@Composable
private fun ChatSettingsPanel(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "模型控制",
            description = state.modelDraft.ifBlank { "使用默认模型" },
            trailing = {
                if (state.settingsExpanded) {
                    StatusPill(text = "编辑中", tone = StatusTone.Accent)
                }
            },
        )
        ChatSettingsSummary(state)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = viewModel::toggleSettingsExpanded) {
                    Icon(
                        imageVector = if (state.settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (state.settingsExpanded) "收起模型控制" else "展开模型控制")
                }
            }
            if (!state.settingsExpanded && state.systemPromptDraft.isBlank()) {
                item {
                    OutlinedButton(onClick = viewModel::toggleSettingsExpanded) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "设置系统指令")
                    }
                }
            }
        }

        if (state.settingsExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChatModelPicker(
                    provider = selectedChatProvider(state),
                    selectedModel = state.modelDraft,
                    onSelectModel = viewModel::updateModelDraft,
                )
                OutlinedTextField(
                    value = state.modelDraft,
                    onValueChange = viewModel::updateModelDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "模型") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.systemPromptDraft,
                    onValueChange = viewModel::updateSystemPromptDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    label = { Text(text = "系统指令") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.temperatureDraft,
                        onValueChange = viewModel::updateTemperatureDraft,
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "温度") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.topPDraft,
                        onValueChange = viewModel::updateTopPDraft,
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "采样阈值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = state.maxTokensDraft,
                    onValueChange = viewModel::updateMaxTokensDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "最大输出") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "临时会话",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = state.temporaryDraft,
                        onCheckedChange = viewModel::updateTemporaryDraft,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "敏感会话",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = state.sensitiveDraft,
                        onCheckedChange = viewModel::updateSensitiveDraft,
                    )
                }
                Button(
                    onClick = viewModel::saveConversationSettings,
                    enabled = state.hasValidModelParameterDrafts(),
                ) {
                    Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "保存设置")
                }
            }
        }
    }
}

@Composable
private fun ChatModelPicker(
    provider: ProviderConfig?,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
) {
    val models = provider.availableChatModels()
    if (models.isEmpty()) {
        Text(
            text = "当前连接还没有同步对话模型，可手动填写模型名。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetadataRow(
            label = "可用模型",
            value = "${provider?.name.orEmpty()} · ${models.size} 个",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models, key = { it.id }) { model ->
                ModelChip(
                    model = model,
                    selected = model.id == selectedModel.trim(),
                    onClick = { onSelectModel(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelChip(
    model: ModelConfig,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = model.displayName.ifBlank { model.id },
                modifier = Modifier.widthIn(max = 180.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            }
        },
    )
}

@Composable
private fun ChatSettingsSummary(state: ChatUiState) {
    val temperatureStatus = modelParameterDraftStatus(
        value = state.temperatureDraft,
        kind = ModelParameterDraftKind.Temperature,
    )
    val topPStatus = modelParameterDraftStatus(
        value = state.topPDraft,
        kind = ModelParameterDraftKind.TopP,
    )
    val maxTokensStatus = modelParameterDraftStatus(
        value = state.maxTokensDraft,
        kind = ModelParameterDraftKind.MaxTokens,
    )
    val visibleParameterStatuses = listOf(
        state.temperatureDraft to temperatureStatus,
        state.topPDraft to topPStatus,
        state.maxTokensDraft to maxTokensStatus,
    ).filter { (value, status) -> value.isNotBlank() || !status.isValid }
    val hasAnySummaryPill = state.systemPromptDraft.isNotBlank() ||
        visibleParameterStatuses.isNotEmpty() ||
        state.temporaryDraft ||
        state.sensitiveDraft

    if (!hasAnySummaryPill) {
        Text(
            text = "使用默认上下文和模型参数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.systemPromptDraft.isNotBlank()) {
            item {
                StatusPill(text = "系统指令已启用", tone = StatusTone.Accent)
            }
        }
        visibleParameterStatuses.forEach { (_, status) ->
            item {
                StatusPill(
                    text = status.label,
                    tone = status.tone(),
                )
            }
        }
        if (state.temporaryDraft) {
            item {
                StatusPill(text = "临时", tone = StatusTone.Warning)
            }
        }
        if (state.sensitiveDraft) {
            item {
                StatusPill(text = "敏感", tone = StatusTone.Critical)
            }
        }
    }
}

@Composable
private fun PromptPresetStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    if (state.promptPresets.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "提示词库",
            description = "${state.promptPresets.size} 个已保存快捷项",
            trailing = {
                if (state.promptsExpanded) {
                    StatusPill(text = "选择中", tone = StatusTone.Accent)
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = viewModel::togglePromptsExpanded) {
                    Icon(
                        imageVector = if (state.promptsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (state.promptsExpanded) "收起提示词" else "展开提示词")
                }
            }
        }
        if (state.promptsExpanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.promptPresets, key = { it.id.value }) { preset ->
                    AssistChip(
                        onClick = { viewModel.applyPromptPreset(preset.id) },
                        label = {
                            Text(
                                text = preset.name,
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInSkillStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    if (state.skills.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "内置 Skill",
            description = "${state.skills.size} 个任务工作流",
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(state.skills, key = { it.id.value }) { skill ->
                val selected = state.systemPromptDraft.contains("ID: ${skill.id.value}")
                AssistChip(
                    onClick = { viewModel.applySkill(skill.id) },
                    label = {
                        Text(
                            text = skill.name,
                            modifier = Modifier.widthIn(max = 180.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}
