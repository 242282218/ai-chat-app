package com.aichat.workbench.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel
import com.aichat.workbench.ui.markdown.MarkdownMessageContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    var confirmArchiveConversation by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var confirmClearContext by rememberSaveable { mutableStateOf(false) }
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }

    DisposableEffect(Unit) {
        onDispose { viewModel.deleteTemporaryConversationOnExit() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Chat") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.deleteTemporaryConversationOnExit()
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::createConversation) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New conversation",
                        )
                    }
                    IconButton(onClick = viewModel::createTemporaryConversation) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "New temporary conversation",
                        )
                    }
                    IconButton(
                        onClick = { confirmArchiveConversation = true },
                        enabled = selectedConversation != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Archive conversation",
                        )
                    }
                    IconButton(
                        onClick = { confirmDeleteConversation = true },
                        enabled = selectedConversation != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete conversation",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ConversationStrip(
                state = state,
                viewModel = viewModel,
                onRequestClearContext = { confirmClearContext = true },
            )
            ProviderStrip(state, viewModel)
            if (state.providers.none { it.enabled }) {
                NoProviderPanel(onOpenProviders = onOpenProviders)
            }
            ChatSettingsPanel(state, viewModel)
            PromptPresetStrip(state, viewModel)
            MessageList(
                messages = state.messages,
                onEdit = viewModel::editMessage,
                onRetry = viewModel::retryMessage,
                modifier = Modifier.weight(1f),
            )
            state.error?.let {
                ChatErrorPanel(message = it)
            }
            InputBar(
                input = state.input,
                isGenerating = state.isGenerating,
                isEditing = state.editingMessageId != null,
                canSend = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onInputChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                onCancelEdit = viewModel::cancelEdit,
            )
        }
    }

    if (confirmArchiveConversation && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "Archive conversation?",
            message = "Hide \"${selectedConversation.title}\" from the active conversation list. There is currently no in-app restore action.",
            confirmLabel = "Archive",
            onConfirm = {
                confirmArchiveConversation = false
                viewModel.archiveSelectedConversation()
            },
            onDismiss = { confirmArchiveConversation = false },
            tone = StatusTone.Warning,
        )
    }

    if (confirmDeleteConversation && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "Delete conversation?",
            message = "This will remove \"${selectedConversation.title}\" and its messages from local history.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDeleteConversation = false
                viewModel.deleteSelectedConversation()
            },
            onDismiss = { confirmDeleteConversation = false },
        )
    }

    if (confirmClearContext && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "Clear context?",
            message = "Delete ${state.messages.size} messages from \"${selectedConversation.title}\". The conversation remains available.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClearContext = false
                viewModel.clearContext()
            },
            onDismiss = { confirmClearContext = false },
        )
    }
}

@Composable
private fun ChatErrorPanel(message: String) {
    WorkbenchPanel(
        title = "Chat error",
        description = message,
        icon = Icons.Filled.Info,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        StatusPill(text = "Needs attention", tone = StatusTone.Critical)
    }
}

@Composable
private fun ConversationStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onRequestClearContext: () -> Unit,
) {
    WorkbenchPanel(
        title = "Conversation",
        description = state.titleDraft.ifBlank { "Untitled" },
        icon = Icons.Filled.Tune,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        trailing = {
            if (state.isGenerating) {
                StatusPill(text = "Streaming", tone = StatusTone.Accent)
            }
        },
    ) {
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
        if (selectedConversation != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    StatusPill(text = "Messages ${state.messages.size}", tone = StatusTone.Neutral)
                }
                if (selectedConversation.isTemporary) {
                    item {
                        StatusPill(text = "Temporary", tone = StatusTone.Warning)
                    }
                }
                if (selectedConversation.isSensitive) {
                    item {
                        StatusPill(text = "Sensitive", tone = StatusTone.Critical)
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
                label = { Text(text = "Title") },
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
                Text(text = "Save title")
            }
            OutlinedButton(
                onClick = onRequestClearContext,
                enabled = state.messages.isNotEmpty() && !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.ClearAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Clear context")
            }
        }
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
            Text(
                text = conversation.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ProviderStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
private fun NoProviderPanel(onOpenProviders: () -> Unit) {
    WorkbenchPanel(
        title = "Provider required",
        description = "Add an OpenAI or compatible provider before sending.",
        icon = Icons.Filled.Info,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        trailing = {
            StatusPill(text = "Setup needed", tone = StatusTone.Warning)
        },
    ) {
        MetadataRow(
            label = "Routing",
            value = "Direct from device",
        )
        Button(
            onClick = onOpenProviders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Configure provider")
        }
    }
}

@Composable
private fun ChatSettingsPanel(
    state: ChatUiState,
    viewModel: ChatViewModel,
) {
    WorkbenchPanel(
        title = "Model controls",
        description = state.modelDraft.ifBlank { "No model override" },
        icon = Icons.Filled.Tune,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        trailing = {
            IconButton(onClick = viewModel::toggleSettingsExpanded) {
                Icon(
                    imageVector = if (state.settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (state.settingsExpanded) {
                        "Collapse model controls"
                    } else {
                        "Expand model controls"
                    },
                )
            }
        },
    ) {
        ChatSettingsSummary(state)

        if (state.settingsExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.modelDraft,
                    onValueChange = viewModel::updateModelDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Model") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.systemPromptDraft,
                    onValueChange = viewModel::updateSystemPromptDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    label = { Text(text = "System prompt") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.temperatureDraft,
                        onValueChange = viewModel::updateTemperatureDraft,
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "Temp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.topPDraft,
                        onValueChange = viewModel::updateTopPDraft,
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "Top P") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = state.maxTokensDraft,
                    onValueChange = viewModel::updateMaxTokensDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Max tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Temporary",
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
                        text = "Sensitive",
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
                    Text(text = "Save settings")
                }
            }
        }
    }
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

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = temperatureStatus.label,
                tone = temperatureStatus.tone(),
            )
        }
        item {
            StatusPill(
                text = topPStatus.label,
                tone = topPStatus.tone(),
            )
        }
        item {
            StatusPill(
                text = maxTokensStatus.label,
                tone = maxTokensStatus.tone(),
            )
        }
        if (state.temporaryDraft) {
            item {
                StatusPill(text = "Temporary", tone = StatusTone.Warning)
            }
        }
        if (state.sensitiveDraft) {
            item {
                StatusPill(text = "Sensitive", tone = StatusTone.Critical)
            }
        }
    }
}

@Composable
private fun PromptPresetStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
) {
    if (state.promptPresets.isEmpty()) return

    WorkbenchPanel(
        title = "Prompt library",
        description = "${state.promptPresets.size} saved shortcuts",
        icon = Icons.Filled.Edit,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        trailing = {
            IconButton(onClick = viewModel::togglePromptsExpanded) {
                Icon(
                    imageVector = if (state.promptsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (state.promptsExpanded) {
                        "Collapse prompt library"
                    } else {
                        "Expand prompt library"
                    },
                )
            }
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "Presets ${state.promptPresets.size}", tone = StatusTone.Accent)
            }
        }
        if (state.promptsExpanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
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
private fun MessageList(
    messages: List<Message>,
    onEdit: (com.aichat.workbench.domain.model.MessageId) -> Unit,
    onRetry: (com.aichat.workbench.domain.model.MessageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                EmptyConversationPanel()
            }
        } else {
            items(messages, key = { it.id.value }) { message ->
                MessageBubble(
                    message = message,
                    onEdit = { onEdit(message.id) },
                    onRetry = { onRetry(message.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationPanel() {
    WorkbenchPanel(
        title = "Thread is empty",
        description = "Start with a prompt or paste context below.",
        icon = Icons.Filled.AutoAwesome,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "0 messages", tone = StatusTone.Neutral)
            }
            item {
                StatusPill(text = "Ready", tone = StatusTone.Success)
            }
            item {
                StatusPill(text = "Local", tone = StatusTone.Accent)
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun MessageBubble(
    message: Message,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var expanded by rememberSaveable(message.id.value) { mutableStateOf(message.role != MessageRole.Tool) }

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
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(
                if (message.role == MessageRole.User) 0.88f else 0.96f,
            ),
            tonalElevation = if (message.role == MessageRole.User) 0.dp else 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MessageHeader(
                    message = message,
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                )
                message.parentMessageId?.let {
                    Text(
                        text = "Linked to ${it.value.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    if (message.content.isBlank()) {
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        MarkdownMessageContent(text = message.content)
                    }
                } else {
                    Text(
                        text = "Tool details collapsed",
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

@Composable
private fun MessageHeader(
    message: Message,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(
            text = message.role.name,
            tone = message.roleTone(),
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(
            text = message.status.name,
            modifier = Modifier.weight(1f, fill = false),
            tone = message.statusTone(),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (message.role == MessageRole.Tool) {
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) {
                        "Collapse tool details"
                    } else {
                        "Expand tool details"
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    message: Message,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 2.dp),
    ) {
        item {
            MessageActionButton(
                icon = Icons.Filled.ContentCopy,
                label = "Copy",
                onClick = onCopy,
            )
        }
        if (message.role == MessageRole.User) {
            item {
                MessageActionButton(
                    icon = Icons.Filled.Edit,
                    label = "Edit",
                    onClick = onEdit,
                )
            }
        }
        if (message.role == MessageRole.Assistant && message.status == MessageStatus.Failed) {
            item {
                MessageActionButton(
                    icon = Icons.Filled.Replay,
                    label = "Retry",
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label)
    }
}

@Composable
private fun messageContainerColor(message: Message) =
    when (message.role) {
        MessageRole.User -> MaterialTheme.colorScheme.primaryContainer
        MessageRole.Assistant -> MaterialTheme.colorScheme.surface
        MessageRole.Tool -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        MessageRole.System -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
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
        -> StatusTone.Accent
    }

@Composable
private fun InputBar(
    input: String,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputStatusRow(
                input = input,
                isGenerating = isGenerating,
                isEditing = isEditing,
                canSend = canSend,
                onOpenProviders = onOpenProviders,
                onCancelEdit = onCancelEdit,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = if (isEditing) "Revise message" else "Ask or instruct") },
                    minLines = 1,
                    maxLines = 5,
                )
                Button(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || canSubmitMessage(input, canSend),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isGenerating) "Stop" else "Send")
                }
            }
        }
    }
}

@Composable
private fun InputStatusRow(
    input: String,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val status = inputStatus(input, isGenerating, isEditing, canSend)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(text = status.first, tone = status.second)
        if (input.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(text = "${input.trim().length} chars", tone = StatusTone.Neutral)
        }
        Spacer(modifier = Modifier.weight(1f))
        when {
            isEditing -> {
                TextButton(onClick = onCancelEdit) {
                    Text(text = "Cancel")
                }
            }
            !canSend && !isGenerating -> {
                TextButton(onClick = onOpenProviders) {
                    Text(text = "Configure provider")
                }
            }
        }
    }
}

private fun inputStatus(
    input: String,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
): Pair<String, StatusTone> =
    when {
        isGenerating -> "Streaming" to StatusTone.Accent
        isEditing -> "Editing" to StatusTone.Warning
        !canSend -> "Provider required" to StatusTone.Critical
        input.isBlank() -> "Message required" to StatusTone.Neutral
        else -> "Ready" to StatusTone.Success
    }

private fun canSubmitMessage(
    input: String,
    canSend: Boolean,
): Boolean =
    canSend && input.trim().isNotEmpty()

private fun ModelParameterDraftStatus.tone(): StatusTone =
    if (isValid) StatusTone.Neutral else StatusTone.Critical
