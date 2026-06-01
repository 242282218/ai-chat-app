package com.aichat.workbench.feature.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.ui.component.IconTile
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import java.util.Base64
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    initialConversationId: ConversationId? = null,
    initialDraft: String = "",
    initialTemporary: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmArchiveConversation by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var confirmClearContext by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(false) }
    val controlSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { encodeChatImage(context, uri) }
                .onSuccess(viewModel::addImageDraft)
                .onFailure { viewModel.reportImageInputError(it.message ?: "图片读取失败。") }
        }
    }
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }

    LaunchedEffect(initialConversationId, state.conversations) {
        if (
            initialConversationId != null &&
            state.selectedConversationId != initialConversationId &&
            state.conversations.any { it.id == initialConversationId }
        ) {
            viewModel.selectConversation(initialConversationId)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.deleteTemporaryConversationOnExit() }
    }

    LaunchedEffect(initialDraft, initialTemporary) {
        viewModel.applyInitialDraft(initialDraft, initialTemporary)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "聊天") },
                navigationIcon = {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = {
                            viewModel.deleteTemporaryConversationOnExit()
                            onBack()
                        },
                    )
                },
                actions = {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Add,
                        label = "新建对话",
                        onClick = viewModel::createConversation,
                    )
                    WorkbenchIconButton(
                        icon = Icons.Filled.Timer,
                        label = "新建临时对话",
                        onClick = viewModel::createTemporaryConversation,
                    )
                    WorkbenchIconButton(
                        icon = Icons.Filled.MoreVert,
                        label = "打开控制区",
                        onClick = { showControls = true },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ConversationContextBar(
                state = state,
                onOpenControls = { showControls = true },
            )
            MessageList(
                messages = state.messages,
                hasEnabledProvider = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onEdit = viewModel::editMessage,
                onRetry = viewModel::retryMessage,
                modifier = Modifier.weight(1f),
            )
            state.pendingToolCall?.let { pending ->
                ToolCallConfirmationPanel(
                    pendingToolCall = pending,
                    onConfirm = viewModel::confirmToolCall,
                    onDeny = viewModel::denyToolCall,
                )
            }
            state.error?.let {
                ChatErrorPanel(message = it)
            }
            InputBar(
                input = state.input,
                imageDrafts = state.imageDrafts,
                isGenerating = state.isGenerating,
                isEditing = state.editingMessageId != null,
                canSend = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onInputChange = viewModel::updateInput,
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onRemoveImage = viewModel::removeImageDraft,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                onCancelEdit = viewModel::cancelEdit,
            )
        }
    }

    if (showControls) {
        ModalBottomSheet(
            onDismissRequest = { showControls = false },
            sheetState = controlSheetState,
        ) {
            ChatControlSheet(
                state = state,
                viewModel = viewModel,
                onOpenProviders = onOpenProviders,
                onRequestClearContext = {
                    showControls = false
                    confirmClearContext = true
                },
                onRequestArchiveConversation = {
                    showControls = false
                    confirmArchiveConversation = true
                },
                onRequestDeleteConversation = {
                    showControls = false
                    confirmDeleteConversation = true
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    if (confirmArchiveConversation && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "归档对话？",
            message = "从当前会话列表隐藏「${selectedConversation.title}」。目前 App 内暂无恢复入口。",
            confirmLabel = "归档",
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
            title = "删除对话？",
            message = "这会从本地历史删除「${selectedConversation.title}」及其消息。",
            confirmLabel = "删除",
            onConfirm = {
                confirmDeleteConversation = false
                viewModel.deleteSelectedConversation()
            },
            onDismiss = { confirmDeleteConversation = false },
        )
    }

    if (confirmClearContext && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "清空上下文？",
            message = "删除「${selectedConversation.title}」中的 ${state.messages.size} 条消息。会话本身会保留。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClearContext = false
                viewModel.clearContext()
            },
            onDismiss = { confirmClearContext = false },
        )
    }
}

@Composable
private fun ToolCallConfirmationPanel(
    pendingToolCall: PendingToolCall,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
) {
    val arguments = pendingToolCall.toolCall.arguments.ifBlank { "{}" }
    WorkbenchPanel(
        title = "Tool 调用确认",
        description = pendingToolCall.displayName,
        icon = Icons.Filled.AutoAwesome,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        trailing = {
            StatusPill(
                text = pendingToolCall.permissionLevel.displayLabel(),
                tone = pendingToolCall.permissionLevel.tone(),
            )
        },
    ) {
        MetadataRow(label = "Tool", value = pendingToolCall.toolCall.name)
        MetadataRow(
            label = "参数",
            value = if (arguments.length > 360) "${arguments.take(360)}..." else arguments,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "拒绝")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "执行")
            }
        }
    }
}

@Composable
private fun ChatErrorPanel(message: String) {
    WorkbenchPanel(
        title = "聊天错误",
        description = message,
        icon = Icons.Filled.Info,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        StatusPill(text = "需要处理", tone = StatusTone.Critical)
    }
}

@Composable
private fun ConversationContextBar(
    state: ChatUiState,
    onOpenControls: () -> Unit,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    val selectedProvider = state.selectedProviderId
        ?.let { id -> state.providers.firstOrNull { it.id.value == id } }
        ?: state.providers.firstOrNull { it.enabled }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = Icons.AutoMirrored.Filled.Chat,
                tone = if (state.providers.any { it.enabled }) StatusTone.Accent else StatusTone.Warning,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = selectedConversation?.title ?: "新对话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        StatusPill(text = "${state.messages.size} 条", tone = StatusTone.Neutral)
                    }
                    item {
                        StatusPill(
                            text = selectedProvider?.let { "${it.name} / ${state.modelDraft.ifBlank { it.defaultModel ?: "Model" }}" }
                                ?: "需要 Provider",
                            tone = if (selectedProvider != null) StatusTone.Success else StatusTone.Warning,
                        )
                    }
                    if (selectedConversation?.isTemporary == true || state.temporaryDraft) {
                        item { StatusPill(text = "临时", tone = StatusTone.Warning) }
                    }
                    if (selectedConversation?.isSensitive == true || state.sensitiveDraft) {
                        item { StatusPill(text = "敏感", tone = StatusTone.Critical) }
                    }
                    if (state.isGenerating) {
                        item { StatusPill(text = "生成中", tone = StatusTone.Accent) }
                    }
                }
            }
            OutlinedButton(onClick = onOpenControls) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "控制")
            }
        }
    }
}

@Composable
private fun ChatControlSheet(
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
            SectionHeaderLike(
                title = "控制区",
                description = "会话、模型、Prompt 和危险操作都在这里维护。",
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
            WorkbenchPanel(
                title = "Provider",
                description = if (state.providers.any { it.enabled }) "选择当前对话使用的模型连接" else "还不能发送消息",
                icon = Icons.Filled.Tune,
                trailing = {
                    if (state.providers.none { it.enabled }) {
                        StatusPill(text = "需要配置", tone = StatusTone.Warning)
                    }
                },
            ) {
                if (state.providers.none { it.enabled }) {
                    Text(
                        text = "添加 OpenAI 或兼容 Provider 后，请求会从本机直接发送到你的 endpoint。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenProviders,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "配置 Provider")
                    }
                } else {
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
            WorkbenchPanel(
                title = "Danger Zone",
                description = "这些操作会改变或删除当前会话内容。",
                icon = Icons.Filled.Delete,
            ) {
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
                    Text(text = "清空上下文")
                }
                OutlinedButton(
                    onClick = onRequestArchiveConversation,
                    enabled = selectedConversation != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Filled.Archive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "归档对话")
                }
                OutlinedButton(
                    onClick = onRequestDeleteConversation,
                    enabled = selectedConversation != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "删除对话")
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderLike(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationStrip(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    WorkbenchPanel(
        title = "会话",
        description = state.titleDraft.ifBlank { "未命名" },
        icon = Icons.Filled.Tune,
        modifier = modifier,
        trailing = {
            if (state.isGenerating) {
                StatusPill(text = "生成中", tone = StatusTone.Accent)
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
                    StatusPill(text = "${state.messages.size} 条消息", tone = StatusTone.Neutral)
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
    WorkbenchPanel(
        title = "Model 控制",
        description = state.modelDraft.ifBlank { "未覆盖 Model" },
        icon = Icons.Filled.Tune,
        modifier = modifier,
        trailing = {
            WorkbenchIconButton(
                icon = if (state.settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                label = if (state.settingsExpanded) "收起 Model 控制" else "展开 Model 控制",
                onClick = viewModel::toggleSettingsExpanded,
            )
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

    WorkbenchPanel(
        title = "Prompt 库",
        description = "${state.promptPresets.size} 个已保存快捷项",
        icon = Icons.Filled.Edit,
        modifier = modifier,
        trailing = {
            WorkbenchIconButton(
                icon = if (state.promptsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                label = if (state.promptsExpanded) "收起 Prompt 库" else "展开 Prompt 库",
                onClick = viewModel::togglePromptsExpanded,
            )
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "${state.promptPresets.size} 个预设", tone = StatusTone.Accent)
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
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
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
                EmptyConversationPanel(
                    hasEnabledProvider = hasEnabledProvider,
                    onOpenProviders = onOpenProviders,
                )
            }
        } else {
            items(messages, key = { it.id.value }) { message ->
                if (message.status == MessageStatus.Compressed) {
                    CompressedMessagesCard(message = message)
                } else {
                    MessageBubble(
                        message = message,
                        onEdit = { onEdit(message.id) },
                        onRetry = { onRetry(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompressedMessagesCard(message: Message) {
    WorkbenchPanel(
        title = "上下文已压缩",
        description = "早期消息已摘要替代",
        icon = Icons.Filled.AutoAwesome,
        trailing = {
            StatusPill(text = "摘要", tone = StatusTone.Accent)
        },
    ) {
        MarkdownMessageContent(text = message.content)
    }
}

@Composable
private fun EmptyConversationPanel(
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
) {
    WorkbenchPanel(
        title = if (hasEnabledProvider) "当前会话为空" else "还不能发送消息",
        description = if (hasEnabledProvider) {
            "在下方输入问题，或到控制区选择 Prompt。"
        } else {
            "添加 OpenAI 或兼容 Provider 后，请求会从本机直接发送到你的 endpoint。"
        },
        icon = Icons.Filled.AutoAwesome,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "0 条消息", tone = StatusTone.Neutral)
            }
            item {
                StatusPill(
                    text = if (hasEnabledProvider) "就绪" else "需要 Provider",
                    tone = if (hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
                )
            }
            item {
                StatusPill(text = "本地", tone = StatusTone.Accent)
            }
        }
        if (!hasEnabledProvider) {
            Button(
                onClick = onOpenProviders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "配置 Provider")
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
                        text = "Tool 详情已折叠",
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
private fun ChatImagePreview(
    image: MessagePart.Image,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(image.uri) { image.uri.toImageBitmapOrNull() }
    if (bitmap == null) {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = "已选择图片",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small),
    )
}

private fun String.toImageBitmapOrNull() =
    runCatching {
        val base64 = substringAfter("base64,", missingDelimiterValue = "")
        if (base64.isBlank()) return@runCatching null
        val bytes = Base64.getDecoder().decode(base64)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

@Composable
private fun MessageHeader(
    message: Message,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(
            text = message.role.displayLabel(),
            tone = message.roleTone(),
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(
            text = message.status.displayLabel(),
            modifier = Modifier.weight(1f, fill = false),
            tone = message.statusTone(),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (message.role == MessageRole.Tool) {
            WorkbenchIconButton(
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                label = if (expanded) "收起 Tool 详情" else "展开 Tool 详情",
                onClick = onToggleExpanded,
            )
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
                label = "复制",
                onClick = onCopy,
            )
        }
        if (message.role == MessageRole.User) {
            item {
                MessageActionButton(
                    icon = Icons.Filled.Edit,
                    label = "编辑",
                    onClick = onEdit,
                )
            }
        }
        if (message.role == MessageRole.Assistant && message.status == MessageStatus.Failed) {
            item {
                MessageActionButton(
                    icon = Icons.Filled.Replay,
                    label = "重试",
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
        MessageStatus.Compressed,
        -> StatusTone.Accent
    }

@Composable
private fun InputBar(
    input: String,
    imageDrafts: List<MessagePart.Image>,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onInputChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
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
            if (imageDrafts.isNotEmpty()) {
                ImageDraftRow(
                    images = imageDrafts,
                    onRemoveImage = onRemoveImage,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedButton(
                    onClick = onPickImage,
                    enabled = !isGenerating,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Image, contentDescription = null)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = if (isEditing) "修改消息" else "提问或输入指令") },
                    minLines = 1,
                    maxLines = 5,
                )
                Button(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || canSubmitMessage(input, canSend, imageDrafts),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isGenerating) "停止" else "发送")
                }
            }
        }
    }
}

@Composable
private fun ImageDraftRow(
    images: List<MessagePart.Image>,
    onRemoveImage: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images.size, key = { index -> "${images[index].uri.take(32)}-$index" }) { index ->
            Box {
                ChatImagePreview(
                    image = images[index],
                    modifier = Modifier.size(72.dp),
                )
                WorkbenchIconButton(
                    icon = Icons.Filled.Close,
                    label = "移除图片",
                    onClick = { onRemoveImage(index) },
                    modifier = Modifier.align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.error,
                )
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = status.label, tone = status.tone)
            if (input.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(text = "${input.trim().length} 字符", tone = StatusTone.Neutral)
            }
            Spacer(modifier = Modifier.weight(1f))
            when {
                isEditing -> {
                    TextButton(onClick = onCancelEdit) {
                        Text(text = "取消")
                    }
                }
                !canSend && !isGenerating -> {
                    TextButton(onClick = onOpenProviders) {
                        Text(text = "配置 Provider")
                    }
                }
            }
        }
        Text(
            text = status.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class InputStatus(
    val label: String,
    val tone: StatusTone,
    val description: String,
)

private fun inputStatus(
    input: String,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
): InputStatus =
    when {
        isGenerating -> InputStatus(
            label = "生成中",
            tone = StatusTone.Accent,
            description = "正在等待 Provider 响应或接收流式内容，可随时停止。",
        )
        isEditing -> InputStatus(
            label = "编辑中",
            tone = StatusTone.Warning,
            description = "发送后会用当前内容重写这条用户消息并重新生成回答。",
        )
        !canSend -> InputStatus(
            label = "需要 Provider",
            tone = StatusTone.Critical,
            description = "添加 OpenAI 或兼容 Provider 后，请求会从本机发送到你的 endpoint。",
        )
        input.isBlank() -> InputStatus(
            label = "请输入消息",
            tone = StatusTone.Neutral,
            description = "输入问题、粘贴材料，或从控制区选择 Prompt 后再发送。",
        )
        else -> InputStatus(
            label = "就绪",
            tone = StatusTone.Success,
            description = "发送后会创建或继续当前会话，生成期间可以停止。",
        )
    }

private fun MessageRole.displayLabel(): String =
    when (this) {
        MessageRole.System -> "系统"
        MessageRole.User -> "用户"
        MessageRole.Assistant -> "助手"
        MessageRole.Tool -> "工具"
    }

private fun MessageStatus.displayLabel(): String =
    when (this) {
        MessageStatus.Draft -> "草稿"
        MessageStatus.Pending -> "等待中"
        MessageStatus.Streaming -> "生成中"
        MessageStatus.Completed -> "完成"
        MessageStatus.Compressed -> "已压缩"
        MessageStatus.Failed -> "失败"
        MessageStatus.Cancelled -> "已取消"
    }

private fun canSubmitMessage(
    input: String,
    canSend: Boolean,
    imageDrafts: List<MessagePart.Image>,
): Boolean =
    canSend && (input.trim().isNotEmpty() || imageDrafts.isNotEmpty())

private fun ToolPermissionLevel.displayLabel(): String =
    when (this) {
        ToolPermissionLevel.ReadOnly -> "只读"
        ToolPermissionLevel.Network -> "网络"
        ToolPermissionLevel.Execute -> "执行代码"
        ToolPermissionLevel.HighRisk -> "高风险"
    }

private fun ToolPermissionLevel.tone(): StatusTone =
    when (this) {
        ToolPermissionLevel.ReadOnly -> StatusTone.Success
        ToolPermissionLevel.Network -> StatusTone.Warning
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> StatusTone.Critical
    }

private fun ModelParameterDraftStatus.tone(): StatusTone =
    if (isValid) StatusTone.Neutral else StatusTone.Critical
