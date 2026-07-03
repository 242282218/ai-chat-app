package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: (() -> Unit)?,
    onOpenProviders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
    initialConversationId: ConversationId? = null,
    initialDraft: String = "",
    startNewConversation: Boolean = false,
    showConversationDrawer: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filteredMessages = state.filteredMessages
    val matchingMessageIds = state.matchingMessageIds

    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var confirmClearContext by rememberSaveable { mutableStateOf(false) }
    var confirmSendImages by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var showControls by rememberSaveable { mutableStateOf(false) }
    val controlSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }

    var initialSelectionDone by rememberSaveable(initialConversationId) { mutableStateOf(false) }
    LaunchedEffect(startNewConversation, initialConversationId, state.conversations) {
        if (
            !initialSelectionDone &&
            !startNewConversation &&
            initialConversationId != null &&
            state.selectedConversationId != initialConversationId &&
            state.conversations.any { it.id == initialConversationId }
        ) {
            viewModel.selectConversation(initialConversationId)
            initialSelectionDone = true
        }
    }

    LaunchedEffect(startNewConversation, initialDraft) {
        if (startNewConversation) {
            viewModel.startNewConversation()
        }
        viewModel.applyInitialDraft(initialDraft)
    }

    val chatContent: @Composable () -> Unit = {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ChatTopBar(
                    state = state,
                    onBack = onBack,
                    onOpenDrawer = if (showConversationDrawer) {
                        { scope.launch { drawerState.open() } }
                    } else {
                        null
                    },
                    onOpenControls = { showControls = true },
                    onToggleSearch = { viewModel.toggleSearch() },
                    onOpenSettings = onOpenSettings,
                    onOpenImageGeneration = onOpenImageGeneration,
                    onSelectProvider = viewModel::selectProvider,
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
                        matchCount = if (state.searchQuery.isBlank()) 0 else filteredMessages.size,
                        currentMatchIndex = state.currentMatchIndex,
                        onQueryChange = viewModel::updateSearchQuery,
                        onNavigateMatch = viewModel::navigateMatch,
                        onClose = { viewModel.toggleSearch() },
                    )
                }
                ChatMessageList(
                    messages = filteredMessages,
                    hasEnabledProvider = state.providers.any { it.enabled },
                    onOpenProviders = onOpenProviders,
                    onEdit = viewModel::editMessage,
                    onRetry = viewModel::retryMessage,
                    onDelete = { pendingDeleteMessageId = it.value },
                    highlightQuery = state.searchQuery,
                    matchingMessageIds = matchingMessageIds,
                    currentMatchIndex = state.currentMatchIndex,
                    modifier = Modifier.weight(1f),
                )
                ChatInputArea(
                    state = state,
                    viewModel = viewModel,
                    onOpenProviders = onOpenProviders,
                    onImagePicked = { uri ->
                        scope.launch {
                            runCatching { encodeChatImage(context, uri) }
                                .onSuccess(viewModel::addImageDraft)
                                .onFailure { viewModel.reportImageInputError(it.message ?: "图片读取失败。") }
                        }
                    },
                    onConfirmSendImages = { confirmSendImages = true },
                )
            }
        }
    }

    if (showConversationDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ChatSessionDrawer(
                    state = state,
                    onNewConversation = {
                        viewModel.startNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onConversationSelected = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onOpenImageGeneration = {
                        scope.launch { drawerState.close() }
                        onOpenImageGeneration()
                    },
                )
            },
        ) {
            chatContent()
        }
    } else {
        chatContent()
    }

    if (showControls) {
        ModalBottomSheet(
            onDismissRequest = { showControls = false },
            sheetState = controlSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
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

@Composable
private fun SearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNavigateMatch: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "搜索消息") },
            placeholder = { Text(text = "搜索消息...") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = workbenchTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                WorkbenchIconButton(
                    icon = Icons.Filled.Close,
                    label = "关闭搜索",
                    onClick = onClose,
                )
            },
        )
        if (query.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (matchCount > 0) {
                        "${currentMatchIndex + 1}/$matchCount 个匹配"
                    } else {
                        "无匹配"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (matchCount > 0) {
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
                }
            }
        }
    }
}
