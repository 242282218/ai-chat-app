package com.aichat.workbench.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.repository.MessageSearchResult
import com.aichat.workbench.navigation.AppDestination
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    destinations: List<AppDestination>,
    onDestinationClick: (AppDestination) -> Unit,
    onStartChat: (String, Boolean) -> Unit,
    onConversationClick: (ConversationId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var showManagementSheet by rememberSaveable { mutableStateOf(false) }
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val managementSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!searchActive) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                    text = { Text(text = "创建会话") },
                )
            }
        },
    ) { innerPadding ->
        if (searchActive) {
            SearchHomeContent(
                state = state,
                onQueryChange = viewModel::updateSearchQuery,
                onBack = {
                    searchActive = false
                    viewModel.updateSearchQuery("")
                },
                onResultClick = { result -> onConversationClick(result.conversation.id) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            ConversationHomeContent(
                state = state,
                onSearch = { searchActive = true },
                onSettings = { showManagementSheet = true },
                onOpenProviders = { onDestinationClick(AppDestination.ProviderSettings) },
                onTaskDraftChange = viewModel::updateTaskDraft,
                onSubmitTask = {
                    val draft = viewModel.consumeTaskDraft()
                    onStartChat(draft, false)
                },
                onNewChat = { onStartChat("", false) },
                onTemporaryChat = { onStartChat("", true) },
                onImages = { onDestinationClick(AppDestination.ImageGen) },
                onPrompts = { onDestinationClick(AppDestination.PromptPresets) },
                onConversationClick = onConversationClick,
                onCreateConversation = { showCreateSheet = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = createSheetState,
        ) {
            CreateConversationSheet(
                onNewChat = {
                    showCreateSheet = false
                    onStartChat("", false)
                },
                onTemporaryChat = {
                    showCreateSheet = false
                    onStartChat("", true)
                },
                onImages = {
                    showCreateSheet = false
                    onDestinationClick(AppDestination.ImageGen)
                },
                onPrompts = {
                    showCreateSheet = false
                    onDestinationClick(AppDestination.PromptPresets)
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    if (showManagementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManagementSheet = false },
            sheetState = managementSheetState,
        ) {
            ManagementSheet(
                destinations = destinations,
                onDestinationClick = { destination ->
                    showManagementSheet = false
                    onDestinationClick(destination)
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun ConversationHomeContent(
    state: HomeUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onTaskDraftChange: (String) -> Unit,
    onSubmitTask: () -> Unit,
    onNewChat: () -> Unit,
    onTemporaryChat: () -> Unit,
    onImages: () -> Unit,
    onPrompts: () -> Unit,
    onConversationClick: (ConversationId) -> Unit,
    onCreateConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HomeActionRow(
                onSearch = onSearch,
                onSettings = onSettings,
            )
        }
        item {
            HomeTitle()
        }
        item {
            HomeTaskComposer(
                draft = state.taskDraft,
                enabledProviderCount = state.enabledProviderCount,
                onDraftChange = onTaskDraftChange,
                onSubmitTask = onSubmitTask,
                onOpenProviders = onOpenProviders,
            )
        }
        item {
            HomeQuickActions(
                onNewChat = onNewChat,
                onTemporaryChat = onTemporaryChat,
                onImages = onImages,
                onPrompts = onPrompts,
            )
        }
        if (!state.hasEnabledProvider) {
            item {
                InlineNotice(
                    text = "需要配置模型连接才能发送消息",
                    icon = Icons.Filled.Tune,
                    tone = StatusTone.Warning,
                    action = {
                        TextButton(onClick = onOpenProviders) {
                            Text(text = "配置")
                        }
                    },
                )
            }
        }
        item {
            QuietSectionHeader(
                title = "最近会话",
                description = homeSummaryLabel(state),
            )
        }
        if (state.recentConversations.isEmpty()) {
            item {
                EmptyConversationState(onCreateConversation = onCreateConversation)
            }
        } else {
            items(state.recentConversations, key = { it.id.value }) { conversation ->
                ConversationListRow(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeTitle(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "AI 工作台",
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun HomeActionRow(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkbenchIconButton(
            icon = Icons.Filled.Search,
            label = "搜索消息",
            onClick = onSearch,
        )
        WorkbenchIconButton(
            icon = Icons.Filled.Settings,
            label = "设置",
            onClick = onSettings,
        )
    }
}

@Composable
private fun HomeTaskComposer(
    draft: String,
    enabledProviderCount: Int,
    onDraftChange: (String) -> Unit,
    onSubmitTask: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEnabledProvider = enabledProviderCount > 0
    val hasDraft = draft.isNotBlank()
    WorkbenchPanel(
        title = "直接开始任务",
        description = if (hasEnabledProvider) {
            "写代码、搜资料、生成图片前的想法，都可以先从这里带入会话。"
        } else {
            "配置模型连接后即可发送任务。"
        },
        icon = Icons.Filled.Bolt,
        modifier = modifier,
        trailing = {
            StatusPill(
                text = if (hasEnabledProvider) "${enabledProviderCount} 个模型可用" else "待配置",
                tone = if (hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
            )
        },
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "任务") },
            placeholder = { Text(text = "例如：帮我写一个爬虫，或搜索今天 AI 新闻") },
            minLines = 1,
            maxLines = 4,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                StatusPill(
                    text = "本地保存会话",
                    tone = StatusTone.Neutral,
                )
            }
            item {
                StatusPill(
                    text = "支持图片输入",
                    tone = StatusTone.Accent,
                )
            }
        }
        Button(
            onClick = if (hasEnabledProvider) onSubmitTask else onOpenProviders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (hasEnabledProvider) Icons.AutoMirrored.Filled.Chat else Icons.Filled.Tune,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !hasEnabledProvider -> "配置模型连接"
                    hasDraft -> "带任务开始"
                    else -> "新建会话"
                },
            )
        }
    }
}

@Composable
private fun HomeQuickActions(
    onNewChat: () -> Unit,
    onTemporaryChat: () -> Unit,
    onImages: () -> Unit,
    onPrompts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkbenchPanel(
        title = "常用入口",
        description = "把高频动作放在首屏，减少进入设置页的次数。",
        icon = Icons.Filled.Add,
        modifier = modifier,
    ) {
        CreationActionRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = "新建会话",
            description = "持久保存上下文和模型设置",
            onClick = onNewChat,
            modifier = Modifier,
        )
        CreationActionRow(
            icon = Icons.Filled.Bolt,
            title = "临时会话",
            description = "退出后自动清理当前内容",
            onClick = onTemporaryChat,
            modifier = Modifier,
        )
        CreationActionRow(
            icon = Icons.Filled.Image,
            title = "图片生成",
            description = "使用已配置的图片模型",
            onClick = onImages,
            modifier = Modifier,
        )
        CreationActionRow(
            icon = Icons.AutoMirrored.Filled.ViewList,
            title = "提示词",
            description = "从本地预设开始任务",
            onClick = onPrompts,
            modifier = Modifier,
        )
    }
}

@Composable
private fun ConversationListRow(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuietListRow(
        title = conversation.title,
        description = conversationDescription(conversation),
        icon = Icons.AutoMirrored.Filled.Chat,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            ConversationStatusPill(conversation)
        },
    )
}

@Composable
private fun EmptyConversationState(
    onCreateConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "创建一个会话，开始保存你的 AI 工作流。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCreateConversation) {
                Text(text = "创建会话")
            }
        }
    }
}

@Composable
private fun SearchHomeContent(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onResultClick: (MessageSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkbenchIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "返回",
                    onClick = onBack,
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "搜索消息") },
                    placeholder = { Text(text = "搜索消息") },
                    singleLine = true,
                )
            }
        }
        item {
            Text(
                text = "搜索结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        when {
            state.searchQuery.isBlank() -> {
                item {
                    SearchHint(text = "输入关键词搜索本地消息")
                }
            }
            state.searchResults.isEmpty() -> {
                item {
                    SearchHint(text = "没有找到相关消息")
                }
            }
            else -> {
                items(state.searchResults, key = { "${it.conversation.id.value}-${it.message.id.value}" }) { result ->
                    SearchResultListRow(
                        result = result,
                        query = state.searchQuery,
                        onClick = { onResultClick(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp),
    )
}

@Composable
private fun SearchResultListRow(
    result: MessageSearchResult,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.conversation.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ConversationStatusPill(result.conversation)
            }
            Text(
                text = conversationDescription(result.conversation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightedSnippet(result.message.content, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CreateConversationSheet(
    onNewChat: () -> Unit,
    onTemporaryChat: () -> Unit,
    onImages: () -> Unit,
    onPrompts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "创建",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        CreationActionRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = "新建会话",
            description = "普通持久会话",
            onClick = onNewChat,
        )
        CreationActionRow(
            icon = Icons.Filled.Bolt,
            title = "临时会话",
            description = "退出后清理",
            onClick = onTemporaryChat,
        )
        CreationActionRow(
            icon = Icons.Filled.Image,
            title = "图片生成",
            description = "进入图片创作",
            onClick = onImages,
        )
        CreationActionRow(
            icon = Icons.AutoMirrored.Filled.ViewList,
            title = "从提示词开始",
            description = "打开本地提示词预设",
            onClick = onPrompts,
        )
    }
}

@Composable
private fun ManagementSheet(
    destinations: List<AppDestination>,
    onDestinationClick: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        destinations.forEach { destination ->
            CreationActionRow(
                icon = destination.icon(),
                title = destination.displayLabel(),
                description = destination.description(),
                onClick = { onDestinationClick(destination) },
            )
        }
    }
}

private fun AppDestination.icon(): ImageVector =
    when (this) {
        AppDestination.Chat -> Icons.AutoMirrored.Filled.Chat
        AppDestination.Conversations -> Icons.AutoMirrored.Filled.Chat
        AppDestination.ImageGen -> Icons.Filled.Image
        AppDestination.ToolsHub -> Icons.Filled.Extension
        AppDestination.SettingsHub -> Icons.Filled.Settings
        AppDestination.ProviderSettings -> Icons.Filled.Tune
        AppDestination.PromptPresets -> Icons.AutoMirrored.Filled.ViewList
        AppDestination.DataSettings -> Icons.Filled.Settings
    }

private fun AppDestination.displayLabel(): String =
    when (this) {
        AppDestination.Conversations -> "聊天"
        AppDestination.ImageGen -> "画图"
        AppDestination.ToolsHub -> "工具"
        AppDestination.SettingsHub -> "设置"
        AppDestination.Chat -> "聊天"
        AppDestination.ProviderSettings -> "模型连接"
        AppDestination.PromptPresets -> "提示词"
        AppDestination.DataSettings -> "数据管理"
    }

private fun AppDestination.description(): String =
    when (this) {
        AppDestination.Conversations -> "最近对话"
        AppDestination.ImageGen -> "生成并查看图片"
        AppDestination.ToolsHub -> "配置可选工具网关"
        AppDestination.SettingsHub -> "模型、提示词和数据"
        AppDestination.Chat -> "开始或继续一个会话"
        AppDestination.ProviderSettings -> "配置模型服务"
        AppDestination.PromptPresets -> "管理本地提示词预设"
        AppDestination.DataSettings -> "管理应用数据和隐私"
    }

@Composable
private fun CreationActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuietListRow(
        title = title,
        description = description,
        icon = icon,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

private fun conversationDescription(conversation: Conversation): String {
    val model = conversation.defaultModel?.takeIf { it.isNotBlank() } ?: "未指定模型"
    return "模型：$model"
}

private fun homeSummaryLabel(state: HomeUiState): String =
    when {
        state.recentConversations.isEmpty() -> "还没有保存的会话"
        state.enabledProviderCount == 0 -> "${state.recentConversations.size} 个会话 · 待配置模型"
        else -> "${state.recentConversations.size} 个会话 · ${state.enabledProviderCount} 个模型连接可用"
    }

@Composable
private fun ConversationStatusPill(conversation: Conversation) {
    if (!conversation.isTemporary && !conversation.isSensitive) return

    StatusPill(
        text = conversation.statusLabel(),
        tone = conversation.statusTone(),
    )
}

private fun Conversation.statusLabel(): String =
    when {
        isSensitive && isTemporary -> "敏感 · 临时"
        isSensitive -> "敏感"
        isTemporary -> "临时"
        else -> "普通"
    }

private fun Conversation.statusTone(): StatusTone =
    when {
        isSensitive -> StatusTone.Critical
        isTemporary -> StatusTone.Warning
        else -> StatusTone.Neutral
    }

@Composable
private fun highlightedSnippet(text: String, query: String): AnnotatedString {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) return AnnotatedString("")
    val needle = query.trim()
    if (needle.isBlank()) return AnnotatedString(normalized.take(160))
    val index = normalized.lowercase().indexOf(needle.lowercase())
    val start = if (index > 40) index - 40 else 0
    val end = minOf(normalized.length, start + 160)
    val snippet = "${if (start > 0) "..." else ""}${normalized.substring(start, end)}"
    val highlightStart = snippet.lowercase().indexOf(needle.lowercase())
    if (highlightStart < 0) return AnnotatedString(snippet)
    val highlightEnd = highlightStart + needle.length
    return buildAnnotatedString {
        append(snippet.substring(0, highlightStart))
        pushStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        append(snippet.substring(highlightStart, highlightEnd))
        pop()
        append(snippet.substring(highlightEnd))
    }
}
