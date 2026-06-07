package com.aichat.workbench.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.ui.component.FileAttachMimeTypes
import com.aichat.workbench.ui.component.InlineImageBubble
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MessageBubble as LinearMessageBubble
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.ToolCallPanel
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.markdown.CodeArtifact
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import java.io.File
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

    // Track whether the initial conversation selection has been performed, so it
    // runs exactly once when the target appears — not on every conversations update.
    var initialSelectionDone by rememberSaveable(initialConversationId) { mutableStateOf(false) }
    LaunchedEffect(initialConversationId, state.conversations) {
        if (
            !initialSelectionDone &&
            initialConversationId != null &&
            state.selectedConversationId != initialConversationId &&
            state.conversations.any { it.id == initialConversationId }
        ) {
            viewModel.selectConversation(initialConversationId)
            initialSelectionDone = true
        }
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
    // LocalClipboardManager is deprecated but still the standard way to access clipboard in Compose
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
