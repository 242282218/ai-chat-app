package com.aichat.workbench.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MessageBubble as LinearMessageBubble
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    initialConversationId: ConversationId? = null,
    initialDraft: String = "",
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var confirmClearContext by rememberSaveable { mutableStateOf(false) }
    var confirmSendImages by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteMessageId by rememberSaveable { mutableStateOf<String?>(null) }
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

    LaunchedEffect(initialDraft) {
        viewModel.applyInitialDraft(initialDraft)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                state = state,
                onBack = onBack,
                onOpenControls = androidx.compose.runtime.remember { { showControls = true } },
                onToggleSearch = { viewModel.toggleSearch() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isSearchActive) {
                SearchBar(
                    query = state.searchQuery,
                    matchCount = state.searchMatchCount,
                    currentMatchIndex = state.currentMatchIndex,
                    onQueryChange = viewModel::updateSearchQuery,
                    onNavigateMatch = viewModel::navigateMatch,
                    onClose = { viewModel.toggleSearch() },
                )
            }
            MessageList(
                messages = state.filteredMessages,
                hasEnabledProvider = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onEdit = viewModel::editMessage,
                onRetry = viewModel::retryMessage,
                onDelete = { pendingDeleteMessageId = it.value },
                highlightQuery = state.searchQuery,
                matchingMessageIds = state.matchingMessageIds,
                currentMatchIndex = state.currentMatchIndex,
                modifier = Modifier.weight(1f),
            )
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
                canSend = state.providers.any { it.enabled },
                onOpenProviders = onOpenProviders,
                onInputChange = viewModel::updateInput,
                onPickImage = androidx.compose.runtime.remember(imagePickerLauncher) { { imagePickerLauncher.launch("image/*") } },
                onRemoveImage = viewModel::removeImageDraft,
                onSend = androidx.compose.runtime.remember(state.imageDrafts) {
                    {
                        if (shouldConfirmImageSend(state.imageDrafts)) {
                            confirmSendImages = true
                        } else {
                            viewModel.sendMessage()
                        }
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
                onRequestDeleteConversation = {
                    showControls = false
                    confirmDeleteConversation = true
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    if (confirmDeleteConversation && selectedConversation != null) {
        WorkbenchConfirmDialog(
            title = "删除对话？",
            message = "这会从本地历史删除「${selectedConversation.title}」及其消息，删除后无法恢复。",
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

    pendingDeleteMessageId?.let { messageId ->
        WorkbenchConfirmDialog(
            title = "删除消息？",
            message = "删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDeleteMessageId = null
                viewModel.deleteMessage(com.aichat.workbench.domain.model.MessageId(messageId))
            },
            onDismiss = { pendingDeleteMessageId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onOpenControls: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = selectedConversation?.title ?: "新对话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = chatSubtitle(state)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                icon = Icons.Filled.Search,
                label = "搜索消息",
                onClick = onToggleSearch,
            )
            WorkbenchIconButton(
                icon = Icons.Filled.MoreVert,
                label = "更多",
                onClick = onOpenControls,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun ChatErrorPanel(
    message: String,
    onOpenProviders: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val clipboard = LocalClipboardManager.current
    InlineNotice(
        text = "回复生成失败，内容未完成。$message",
        icon = Icons.Filled.Info,
        tone = StatusTone.Critical,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
private fun SearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNavigateMatch: (Int) -> Unit,
    onClose: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        placeholder = { Text(text = "搜索消息...") },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = workbenchTextFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank() && matchCount > 0) {
                    Text(
                        text = "${currentMatchIndex + 1}/$matchCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    WorkbenchIconButton(
                        icon = Icons.Filled.KeyboardArrowUp,
                        label = "上一个匹配",
                        onClick = { onNavigateMatch(-1) },
                    )
                    WorkbenchIconButton(
                        icon = Icons.Filled.KeyboardArrowDown,
                        label = "下一个匹配",
                        onClick = { onNavigateMatch(1) },
                    )
                } else if (query.isNotBlank()) {
                    Text(
                        text = "无匹配",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                WorkbenchIconButton(
                    icon = Icons.Filled.Close,
                    label = "关闭搜索",
                    onClick = onClose,
                )
            }
        },
    )
}

@Composable
private fun MessageList(
        messages: List<Message>,
        hasEnabledProvider: Boolean,
        onOpenProviders: () -> Unit,
        onEdit: (com.aichat.workbench.domain.model.MessageId) -> Unit,
        onRetry: (com.aichat.workbench.domain.model.MessageId) -> Unit,
        onDelete: (com.aichat.workbench.domain.model.MessageId) -> Unit,
        highlightQuery: String = "",
        matchingMessageIds: List<com.aichat.workbench.domain.model.MessageId> = emptyList(),
        currentMatchIndex: Int = 0,
        modifier: Modifier = Modifier,
    ) {
    val listState = rememberLazyListState()
    // Track whether user is near the bottom; auto-scroll on new content if so
    var isNearBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index >= listState.layoutInfo.totalItemsCount - 1
        }.collect { atBottom ->
            isNearBottom = atBottom
        }
    }
    // Auto-scroll on new messages or streaming content when near bottom
    val lastMessage = messages.lastOrNull()
    val contentKey = lastMessage?.let { "${it.id.value}:${it.content.length}" }
    LaunchedEffect(contentKey) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    // Auto-scroll to current search match
    LaunchedEffect(currentMatchIndex, matchingMessageIds) {
        if (matchingMessageIds.isNotEmpty() && currentMatchIndex in matchingMessageIds.indices) {
            val targetId = matchingMessageIds[currentMatchIndex]
            val scrollIndex = messages.indexOfFirst { it.id == targetId }
            if (scrollIndex >= 0) {
                isNearBottom = false // prevent auto-scroll fight
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                EmptyConversationPanel(
                    hasEnabledProvider = hasEnabledProvider,
                    onOpenProviders = onOpenProviders,
                )
            }
        } else {
            items(
                messages,
                key = { it.id.value },
            ) { message ->
                val editAction = androidx.compose.runtime.remember(message.id) { { onEdit(message.id) } }
                val retryAction = androidx.compose.runtime.remember(message.id) { { onRetry(message.id) } }
                val deleteAction = androidx.compose.runtime.remember(message.id) { { onDelete(message.id) } }
                MessageItem(
                    message = message,
                    onEdit = editAction,
                    onRetry = retryAction,
                    onDelete = deleteAction,
                    highlightQuery = highlightQuery,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun MessageItem(
        message: Message,
        onEdit: () -> Unit,
        onRetry: () -> Unit,
        onDelete: () -> Unit = {},
        highlightQuery: String = "",
        modifier: Modifier = Modifier,
    ) {
    val clipboardManager = LocalClipboardManager.current
    val copyState = rememberCopyState(message.id)

    if (message.status == MessageStatus.Compressed) {
        CompressedMessagesCard(message = message, highlightQuery = highlightQuery)
        return
    }
    when {
        (message.role == MessageRole.User || message.role == MessageRole.Assistant) &&
            message.contentParts.isEmpty() &&
            message.errorSummary == null &&
            message.status == MessageStatus.Completed -> {
            LinearMessageBubble(
                message = message,
                highlightQuery = highlightQuery,
            )
            MessageActionRow(
                message = message,
                copyState = copyState.value,
                onCopy = {
                    try {
                        clipboardManager.setText(AnnotatedString(message.content))
                        copyState.value = CopyState.Copied
                    } catch (_: Exception) {
                        copyState.value = CopyState.Failed
                    }
                },
                onEdit = onEdit,
                onRetry = onRetry,
            )
        }
        else -> MessageBubble(
            message = message,
            onEdit = onEdit,
            onRetry = onRetry,
            onDelete = onDelete,
            highlightQuery = highlightQuery,
        )
    }
}

@Composable
private fun CompressedMessagesCard(message: Message, highlightQuery: String = "") {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "上下文已压缩",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            MarkdownMessageContent(text = message.content, highlightQuery = highlightQuery)
        }
    }
}
