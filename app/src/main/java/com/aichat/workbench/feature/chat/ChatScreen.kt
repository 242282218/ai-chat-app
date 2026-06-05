package com.aichat.workbench.feature.chat

import android.graphics.BitmapFactory
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.feature.image.saveGeneratedImage
import com.aichat.workbench.feature.image.shareGeneratedImage
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.ui.component.FileAttachButton
import com.aichat.workbench.ui.component.FileAttachMimeTypes
import com.aichat.workbench.ui.component.InlineImageBubble
import com.aichat.workbench.provider.preferredModel
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MessageBubble as LinearMessageBubble
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.ToolCallPanel
import com.aichat.workbench.ui.component.ToolCallPanelOutcome
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import com.aichat.workbench.ui.markdown.CodeArtifact
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import java.io.File
import java.util.Base64
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenTools: () -> Unit = {},
    initialConversationId: ConversationId? = null,
    initialDraft: String = "",
    initialTemporary: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmArchiveConversation by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var confirmClearContext by rememberSaveable { mutableStateOf(false) }
    var confirmSendImages by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(false) }
    var starterPromptLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val controlSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.persistReadPermission(uri)
        viewModel.attachFile(uri.toString())
    }
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
            ChatTopBar(
                state = state,
                onBack = {
                    viewModel.deleteTemporaryConversationOnExit()
                    onBack()
                },
                onOpenControls = { showControls = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MessageList(
                messages = state.messages,
                pendingToolCall = state.pendingToolCall,
                hasEnabledProvider = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onUseStarterPrompt = { prompt ->
                    starterPromptLabel = prompt.label
                    when (prompt.action) {
                        ChatStarterAction.PlainText -> viewModel.updateInput(prompt.text)
                        ChatStarterAction.WebSearch -> viewModel.prepareSearchTask(prompt.text)
                        ChatStarterAction.ImageGeneration -> viewModel.regenerateImagePrompt(prompt.text)
                        ChatStarterAction.LocalJs -> viewModel.prepareLocalJsTask(prompt.text)
                        ChatStarterAction.FileRead -> filePickerLauncher.launch(FileAttachMimeTypes)
                        ChatStarterAction.TextTransform -> viewModel.prepareTextTransformTask(prompt.text)
                        ChatStarterAction.CodeDiffPreview -> viewModel.prepareCodeDiffPreviewTask(prompt.text)
                    }
                },
                onEdit = viewModel::editMessage,
                onRetry = viewModel::retryMessage,
                onConfirmToolCall = viewModel::confirmToolCall,
                onDenyToolCall = viewModel::denyToolCall,
                onToolArgumentsChange = viewModel::updatePendingToolArguments,
                onCancelRunningTool = viewModel::stopGeneration,
                onRetryToolWithArguments = { toolCall ->
                    viewModel.updateInput(toolCall.retryPrompt())
                },
                onReuseImagePrompt = viewModel::reuseImagePrompt,
                onRegenerateImagePrompt = viewModel::regenerateImagePrompt,
                onContinueWithToolResult = viewModel::continueWithToolResult,
                onRecoverLocalJsResult = viewModel::prepareLocalJsRecoveryTask,
                onRecoverToolResult = viewModel::prepareToolRecoveryTask,
                onChooseFile = { filePickerLauncher.launch(FileAttachMimeTypes) },
                onGenerateDiff = { artifact ->
                    viewModel.updateInput(artifact.diffPrompt())
                },
                onOpenTools = onOpenTools,
                modifier = Modifier.weight(1f),
            )
            state.pendingToolCall?.let { pending ->
                ToolCallPanel(
                    toolCall = pending.toolCall,
                    result = null,
                    isError = false,
                    isPending = true,
                    displayName = pending.displayName,
                    permissionLevel = pending.permissionLevel,
                    onApprove = viewModel::confirmToolCall,
                    onDeny = viewModel::denyToolCall,
                    onArgumentsChange = viewModel::updatePendingToolArguments,
                    onCancelRunning = viewModel::stopGeneration,
                )
            }
            state.error?.let {
                ChatErrorPanel(
                    message = it,
                    onOpenProviders = onOpenProviders,
                    onRetry = state.messages.lastOrNull { message ->
                        message.role == MessageRole.Assistant &&
                            message.status == MessageStatus.Failed
                    }?.let { failedMessage ->
                        { viewModel.retryMessage(failedMessage.id) }
                    },
                )
            }
            InputBar(
                input = state.input,
                imageDrafts = state.imageDrafts,
                isGenerating = state.isGenerating,
                isEditing = state.editingMessageId != null,
                starterPromptLabel = starterPromptLabel,
                canSend = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onInputChange = {
                    starterPromptLabel = null
                    viewModel.updateInput(it)
                },
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onPickFile = { uri ->
                    context.persistReadPermission(uri)
                    viewModel.attachFile(uri.toString())
                },
                onClearFileTask = viewModel::clearAttachedFileTask,
                onRemoveImage = viewModel::removeImageDraft,
                onSend = {
                    if (shouldConfirmImageSend(state.imageDrafts)) {
                        confirmSendImages = true
                    } else {
                        viewModel.sendMessage()
                    }
                },
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
            message = "删除「${selectedConversation.title}」中的 ${state.selectedConversationMessageCount} 条消息。会话本身会保留。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClearContext = false
                viewModel.clearContext()
            },
            onDismiss = { confirmClearContext = false },
        )
    }

    if (confirmSendImages) {
        WorkbenchConfirmDialog(
            title = "发送图片给模型？",
            message = "当前消息包含 ${state.imageDrafts.size} 张图片。确认后图片会作为多模态内容发送给当前模型。",
            confirmLabel = "发送",
            onConfirm = {
                confirmSendImages = false
                viewModel.sendMessage()
            },
            onDismiss = { confirmSendImages = false },
            tone = StatusTone.Warning,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onOpenControls: () -> Unit,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = selectedConversation?.title ?: "新对话",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chatSubtitle(state, selectedConversation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            WorkbenchIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "返回",
                onClick = onBack,
            )
        },
        actions = {
            WorkbenchIconButton(
                icon = Icons.Filled.MoreVert,
                label = "更多",
                onClick = onOpenControls,
            )
        },
    )
}

@Composable
private fun ChatErrorPanel(
    message: String,
    onOpenProviders: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    InlineNotice(
        text = "生成失败：$message",
        icon = Icons.Filled.Info,
        tone = StatusTone.Critical,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        WorkbenchIconButton(
            icon = Icons.Filled.ContentCopy,
            label = "复制错误",
            onClick = { clipboard.setText(AnnotatedString(message)) },
        )
        onRetry?.let { retry ->
            WorkbenchIconButton(
                icon = Icons.Filled.Replay,
                label = "重试回复",
                onClick = retry,
            )
        }
        TextButton(onClick = onOpenProviders) {
            Text(text = "配置")
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
private fun MessageList(
    messages: List<Message>,
    pendingToolCall: PendingToolCall?,
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
    onUseStarterPrompt: (ChatStarterPrompt) -> Unit,
    onEdit: (com.aichat.workbench.domain.model.MessageId) -> Unit,
    onRetry: (com.aichat.workbench.domain.model.MessageId) -> Unit,
    onConfirmToolCall: () -> Unit,
    onDenyToolCall: () -> Unit,
    onToolArgumentsChange: (String) -> Unit,
    onCancelRunningTool: () -> Unit,
    onRetryToolWithArguments: (ToolCall) -> Unit,
    onReuseImagePrompt: (String) -> Unit,
    onRegenerateImagePrompt: (String) -> Unit,
    onContinueWithToolResult: (String, String?) -> Unit,
    onRecoverLocalJsResult: (String?) -> Unit,
    onRecoverToolResult: (String, String?, String) -> Unit,
    onChooseFile: () -> Unit,
    onGenerateDiff: (CodeArtifact) -> Unit,
    onOpenTools: () -> Unit,
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
                    onUseStarterPrompt = onUseStarterPrompt,
                )
            }
        } else {
            items(messages, key = { it.id.value }) { message ->
                if (message.status == MessageStatus.Compressed) {
                    CompressedMessagesCard(message = message)
                } else {
                    MessageItem(
                        message = message,
                        messages = messages,
                        pendingToolCall = pendingToolCall,
                        onEdit = { onEdit(message.id) },
                        onRetry = { onRetry(message.id) },
                        onConfirmToolCall = onConfirmToolCall,
                        onDenyToolCall = onDenyToolCall,
                        onToolArgumentsChange = onToolArgumentsChange,
                        onCancelRunningTool = onCancelRunningTool,
                        onRetryToolWithArguments = onRetryToolWithArguments,
                        onReuseImagePrompt = onReuseImagePrompt,
                        onRegenerateImagePrompt = onRegenerateImagePrompt,
                        onContinueWithToolResult = onContinueWithToolResult,
                        onRecoverLocalJsResult = onRecoverLocalJsResult,
                        onRecoverToolResult = onRecoverToolResult,
                        onChooseFile = onChooseFile,
                        onOpenProviders = onOpenProviders,
                        onOpenTools = onOpenTools,
                        onGenerateDiff = onGenerateDiff,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: Message,
    messages: List<Message>,
    pendingToolCall: PendingToolCall?,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onConfirmToolCall: () -> Unit,
    onDenyToolCall: () -> Unit,
    onToolArgumentsChange: (String) -> Unit,
    onCancelRunningTool: () -> Unit,
    onRetryToolWithArguments: (ToolCall) -> Unit,
    onReuseImagePrompt: (String) -> Unit,
    onRegenerateImagePrompt: (String) -> Unit,
    onContinueWithToolResult: (String, String?) -> Unit,
    onRecoverLocalJsResult: (String?) -> Unit,
    onRecoverToolResult: (String, String?, String) -> Unit,
    onChooseFile: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onGenerateDiff: (CodeArtifact) -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    when {
        message.role == MessageRole.Assistant && message.toolCalls.isNotEmpty() -> {
            AssistantToolPlanMessage(
                message = message,
                pendingToolCall = pendingToolCall,
                onConfirmToolCall = onConfirmToolCall,
                onDenyToolCall = onDenyToolCall,
                onToolArgumentsChange = onToolArgumentsChange,
                onCancelRunningTool = onCancelRunningTool,
                onGenerateDiff = onGenerateDiff,
            )
        }
        message.role == MessageRole.Tool -> {
            val pending = pendingToolCall?.takeIf { it.toolCall.id == message.toolCallId }
            val toolCall = messages.findToolCall(message.toolCallId) ?: pending?.toolCall
            if (toolCall != null) {
                val toolResultText = message.toolResult ?: message.content.takeIf { it.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolCallPanel(
                        toolCall = toolCall,
                        result = toolResultText,
                        isError = message.status == MessageStatus.Failed,
                        isPending = pending != null,
                        displayName = pending?.displayName,
                        permissionLevel = pending?.permissionLevel,
                        outcome = message.toolPanelOutcome(pending != null, toolResultText),
                        onApprove = onConfirmToolCall,
                        onDeny = onDenyToolCall,
                        onArgumentsChange = onToolArgumentsChange,
                        onCancelRunning = onCancelRunningTool,
                        onRetryWithArguments = { onRetryToolWithArguments(toolCall) },
                    )
                    ToolStructuredResultCard(
                        toolName = toolCall.name,
                        toolResult = toolResultText,
                        onContinue = onContinueWithToolResult,
                        onRecoverLocalJs = onRecoverLocalJsResult,
                        onRecoverTool = onRecoverToolResult,
                        onChooseFile = onChooseFile,
                        onOpenProviders = onOpenProviders,
                        onOpenTools = onOpenTools,
                    )
                    ToolImageResultRow(message = message)
                    ToolImageResultActions(
                        message = message,
                        onReusePrompt = onReuseImagePrompt,
                        onRegenerate = onRegenerateImagePrompt,
                    )
                    ToolSearchCitationRow(
                        toolName = toolCall.name,
                        toolResult = toolResultText,
                    )
                }
            } else {
                MessageBubble(
                    message = message,
                    onEdit = onEdit,
                    onRetry = onRetry,
                    onGenerateDiff = onGenerateDiff,
                )
            }
        }
        message.role == MessageRole.Assistant &&
            message.contentParts.any { it is MessagePart.Image } -> {
            val image = message.contentParts.filterIsInstance<MessagePart.Image>().first()
            InlineImageBubble(
                imageUrl = image.uri,
                prompt = message.content.ifBlank { "生成图片" },
                isLoading = message.status == MessageStatus.Streaming,
            )
        }
        (message.role == MessageRole.User || message.role == MessageRole.Assistant) &&
            message.contentParts.isEmpty() &&
            message.errorSummary == null &&
            message.status == MessageStatus.Completed -> {
            LinearMessageBubble(
                message = message,
                onGenerateDiff = onGenerateDiff,
            )
            MessageActionRow(
                message = message,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(message.content))
                },
                onEdit = onEdit,
                onRetry = onRetry,
            )
        }
        else -> MessageBubble(
            message = message,
            onEdit = onEdit,
            onRetry = onRetry,
            onGenerateDiff = onGenerateDiff,
        )
    }
}

@Composable
private fun AssistantToolPlanMessage(
    message: Message,
    pendingToolCall: PendingToolCall?,
    onConfirmToolCall: () -> Unit,
    onDenyToolCall: () -> Unit,
    onToolArgumentsChange: (String) -> Unit,
    onCancelRunningTool: () -> Unit,
    onGenerateDiff: (CodeArtifact) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.content.isNotBlank() || message.contentParts.isNotEmpty() || message.errorSummary != null) {
            MessageBubble(
                message = message.copy(toolCalls = emptyList()),
                onEdit = {},
                onRetry = {},
                onGenerateDiff = onGenerateDiff,
            )
        }
        message.toolCalls.forEach { plannedCall ->
            val pending = pendingToolCall?.takeIf { it.toolCall.id == plannedCall.id }
            ToolCallPanel(
                toolCall = pending?.toolCall ?: plannedCall,
                result = null,
                isError = false,
                isPending = pending != null,
                displayName = pending?.displayName,
                permissionLevel = pending?.permissionLevel,
                onApprove = onConfirmToolCall,
                onDeny = onDenyToolCall,
                onArgumentsChange = onToolArgumentsChange,
                onCancelRunning = onCancelRunningTool,
                isPlanOnly = pending == null,
            )
        }
    }
}

@Composable
private fun ToolImageResultRow(message: Message) {
    val images = message.contentParts.filterIsInstance<MessagePart.Image>()
    if (images.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(images, key = { it.uri.take(80) }) { image ->
            InlineImageBubble(
                imageUrl = image.uri,
                prompt = extractImagePrompt(message.toolResult) ?: "生成图片",
                isLoading = false,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun ToolImageResultActions(
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
private fun ToolSearchCitationRow(
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
            items(citations, key = { it.url }) { citation ->
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
private fun ToolStructuredResultCard(
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

private fun List<Message>.findToolCall(toolCallId: com.aichat.workbench.domain.model.ToolCallId?): ToolCall? {
    if (toolCallId == null) return null
    return firstNotNullOfOrNull { message ->
        message.toolCalls.firstOrNull { it.id == toolCallId }
    }
}

private fun Message.toolPanelOutcome(
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

private fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun CompressedMessagesCard(message: Message) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "上下文已压缩",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
            }
            MarkdownMessageContent(text = message.content)
        }
    }
}

@Composable
private fun EmptyConversationPanel(
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
    onUseStarterPrompt: (ChatStarterPrompt) -> Unit,
) {
    WorkbenchPanel(
        title = if (hasEnabledProvider) "开始新的会话" else "先连接模型",
        description = if (hasEnabledProvider) {
            "直接输入任务，或用一个起手式快速进入搜索、图片、文件和代码场景。"
        } else {
            "添加模型连接后，请求会从本机直接发送到你的接口地址。"
        },
        icon = Icons.AutoMirrored.Filled.Chat,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        trailing = {
            StatusPill(
                text = if (hasEnabledProvider) "可发送" else "待配置",
                tone = if (hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
            )
        },
    ) {
        if (!hasEnabledProvider) {
            Button(
                onClick = onOpenProviders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "配置模型连接")
            }
        } else {
            QuickCapabilityRow()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 2.dp),
            ) {
                items(chatStarterPrompts) { prompt ->
                    AssistChip(
                        onClick = { onUseStarterPrompt(prompt) },
                        leadingIcon = {
                            Icon(
                                imageVector = prompt.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(
                                text = prompt.label,
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
private fun QuickCapabilityRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(text = "搜新闻", tone = StatusTone.Warning)
        }
        item {
            StatusPill(text = "生图片", tone = StatusTone.Accent)
        }
        item {
            StatusPill(text = "跑 JS", tone = StatusTone.Critical)
        }
        item {
            StatusPill(text = "读文件", tone = StatusTone.Success)
        }
    }
}

private data class ChatStarterPrompt(
    val label: String,
    val text: String,
    val icon: ImageVector,
    val action: ChatStarterAction = ChatStarterAction.PlainText,
)

private enum class ChatStarterAction {
    PlainText,
    WebSearch,
    ImageGeneration,
    LocalJs,
    FileRead,
    TextTransform,
    CodeDiffPreview,
}

private val chatStarterPrompts = listOf(
    ChatStarterPrompt(
        "搜索新闻",
        "今天 AI 行业新闻",
        Icons.AutoMirrored.Filled.OpenInNew,
        ChatStarterAction.WebSearch,
    ),
    ChatStarterPrompt(
        "生成图片",
        "一张移动端 AI 工作台界面概念图，清晰、专业、原生应用风格",
        Icons.Filled.Image,
        ChatStarterAction.ImageGeneration,
    ),
    ChatStarterPrompt(
        "运行 JS",
        """return JSON.stringify({ ok: true, now: new Date(0).toISOString() })""",
        Icons.Filled.Timer,
        ChatStarterAction.LocalJs,
    ),
    ChatStarterPrompt(
        "读取文件",
        "选择 Markdown、JSON 或代码文件后总结重点、风险和下一步行动。",
        Icons.Filled.Archive,
        ChatStarterAction.FileRead,
    ),
    ChatStarterPrompt(
        "格式化 JSON",
        """{"name":"mobile-workbench","tools":["text_transform","code_diff_preview"]}""",
        Icons.Filled.Edit,
        ChatStarterAction.TextTransform,
    ),
    ChatStarterPrompt(
        "预览 Diff",
        """
        fun answer() = "old"
        """.trimIndent(),
        Icons.Filled.Edit,
        ChatStarterAction.CodeDiffPreview,
    ),
    ChatStarterPrompt("总结材料", "请帮我总结下面这段材料，提炼关键结论、风险和下一步行动：", Icons.Filled.AutoAwesome),
    ChatStarterPrompt("拆解方案", "请把这个目标拆成可执行步骤，并说明每一步的验证标准：", Icons.Filled.Tune),
)

@Composable
@Suppress("DEPRECATION")
private fun MessageBubble(
    message: Message,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onGenerateDiff: (CodeArtifact) -> Unit,
) {
    @Suppress("DEPRECATION")
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

private fun String.jsonStringLiteral(): String =
    buildString {
        append('"')
        this@jsonStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun ToolCall.retryPrompt(): String =
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
        val normalized = trim()
        when {
            normalized.startsWith("data:image") -> {
                val base64 = substringAfter("base64,", missingDelimiterValue = "")
                if (base64.isBlank()) return@runCatching null
                val bytes = Base64.getDecoder().decode(base64)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            normalized.startsWith("file://") -> {
                val path = Uri.parse(normalized).path ?: return@runCatching null
                BitmapFactory.decodeFile(path)
            }
            File(normalized).isFile -> BitmapFactory.decodeFile(normalized)
            else -> null
        }?.asImageBitmap()
    }.getOrNull()

private fun String.toLocalImagePathOrNull(): String? {
    val normalized = trim()
    val path = when {
        normalized.startsWith("file://") -> Uri.parse(normalized).path
        normalized.startsWith("data:image") -> null
        normalized.startsWith("http://") || normalized.startsWith("https://") -> null
        else -> normalized
    } ?: return null
    return path.takeIf { File(it).isFile }
}

private fun String.fileStem(): String =
    File(this).nameWithoutExtension.ifBlank { "generated-image" }

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
private fun MessageActionRow(
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

@Composable
private fun InputBar(
    input: String,
    imageDrafts: List<MessagePart.Image>,
    isGenerating: Boolean,
    isEditing: Boolean,
    starterPromptLabel: String?,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onInputChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: (Uri) -> Unit,
    onClearFileTask: () -> Unit,
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
                isGenerating = isGenerating,
                isEditing = isEditing,
                starterPromptLabel = starterPromptLabel,
                hasImageDrafts = imageDrafts.isNotEmpty(),
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
            if (input.hasFileReadInstruction()) {
                InlineNotice(
                    text = "已添加文件读取任务，可清除后重新选择文件。",
                    icon = Icons.Filled.Archive,
                    tone = StatusTone.Neutral,
                ) {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Close,
                        label = "清除文件任务",
                        onClick = onClearFileTask,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                WorkbenchIconButton(
                    icon = Icons.Filled.Image,
                    label = "添加图片",
                    onClick = onPickImage,
                    enabled = !isGenerating,
                )
                FileAttachButton(onFilePicked = onPickFile)
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = if (isEditing) "编辑消息" else "消息") },
                    placeholder = { Text(text = if (isEditing) "修改消息" else "输入消息") },
                    minLines = 1,
                    maxLines = 5,
                )
                FilledIconButton(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || canSubmitMessage(input, canSend, imageDrafts),
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "停止" else "发送",
                    )
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputStatusRow(
    isGenerating: Boolean,
    isEditing: Boolean,
    starterPromptLabel: String?,
    hasImageDrafts: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val status = inputStatus(
        isGenerating = isGenerating,
        isEditing = isEditing,
        starterPromptLabel = starterPromptLabel,
        hasImageDrafts = hasImageDrafts,
        canSend = canSend,
    ) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodySmall,
            color = status.tone.contentColor(),
        )
        Spacer(modifier = Modifier.weight(1f))
        when {
            isEditing -> {
                TextButton(onClick = onCancelEdit) {
                    Text(text = "取消")
                }
            }
            !canSend && !isGenerating -> {
                TextButton(onClick = onOpenProviders) {
                    Text(text = "配置模型连接")
                }
            }
        }
    }
}

internal data class InputStatus(
    val label: String,
    val tone: StatusTone,
)

internal fun inputStatus(
    isGenerating: Boolean,
    isEditing: Boolean,
    starterPromptLabel: String?,
    hasImageDrafts: Boolean,
    canSend: Boolean,
): InputStatus? =
    when {
        isGenerating -> InputStatus(
            label = "生成中",
            tone = StatusTone.Accent,
        )
        isEditing -> InputStatus(
            label = "编辑中",
            tone = StatusTone.Warning,
        )
        starterPromptLabel != null -> InputStatus(
            label = "已套用：$starterPromptLabel",
            tone = StatusTone.Neutral,
        )
        hasImageDrafts && canSend -> InputStatus(
            label = "图片将作为多模态内容发送给模型",
            tone = StatusTone.Warning,
        )
        !canSend -> InputStatus(
            label = "需要模型连接",
            tone = StatusTone.Critical,
        )
        else -> null
    }

private fun Message.shouldShowHeader(): Boolean =
    role == MessageRole.Tool ||
        role == MessageRole.System ||
        status != MessageStatus.Completed

@Composable
private fun StatusTone.contentColor() =
    when (this) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Accent -> MaterialTheme.colorScheme.primary
        StatusTone.Success -> MaterialTheme.colorScheme.secondary
        StatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        StatusTone.Critical -> MaterialTheme.colorScheme.error
    }

private fun chatSubtitle(
    state: ChatUiState,
    selectedConversation: Conversation?,
): String {
    val selectedProvider = selectedChatProvider(state)
    val model = state.modelDraft.ifBlank { selectedProvider?.preferredModel().orEmpty() }
    val providerText = selectedProvider?.let {
        if (model.isBlank()) it.name else "${it.name} / $model"
    } ?: "需要模型连接"
    val stateText = when {
        state.isGenerating -> "生成中"
        selectedConversation?.isTemporary == true || state.temporaryDraft -> "临时会话"
        selectedConversation?.isSensitive == true || state.sensitiveDraft -> "敏感会话"
        else -> null
    }
    return listOfNotNull(stateText, providerText).joinToString(" · ")
}

private fun ChatUiState.shouldShowConversationMetadata(conversation: Conversation): Boolean =
    selectedConversationMessageCount > 0 || conversation.isTemporary || conversation.isSensitive

private fun Conversation.chipStatusText(): String? =
    when {
        isSensitive && isTemporary -> "敏感 · 临时"
        isSensitive -> "敏感"
        isTemporary -> "临时"
        else -> null
    }

private fun selectedChatProvider(state: ChatUiState) =
    state.selectedProviderId
        ?.let { id -> state.providers.firstOrNull { it.id.value == id } }
        ?: state.providers.firstOrNull { it.enabled }

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

internal fun shouldConfirmImageSend(imageDrafts: List<MessagePart.Image>): Boolean =
    imageDrafts.isNotEmpty()

private fun android.content.Context.persistReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private const val MAX_INLINE_SEARCH_CITATIONS = 5

private fun ModelParameterDraftStatus.tone(): StatusTone =
    if (isValid) StatusTone.Neutral else StatusTone.Critical
