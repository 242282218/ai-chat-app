package com.aichat.workbench.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
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
                destinations = destinations,
                onSearch = { searchActive = true },
                onSettings = { showManagementSheet = true },
                onOpenProviders = { onDestinationClick(AppDestination.Providers) },
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
                    onDestinationClick(AppDestination.Images)
                },
                onPrompts = {
                    showCreateSheet = false
                    onDestinationClick(AppDestination.Prompts)
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
    destinations: List<AppDestination>,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenProviders: () -> Unit,
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
            HomeTitle(state = state)
        }
        item {
            HomeStatusStrip(state = state)
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
        if (destinations.any { it == AppDestination.Providers }) {
            item {
                Text(
                    text = "模型连接、提示词、工具和数据设置已移到右上角设置入口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTitle(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "会话",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = homeSubtitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeStatusStrip(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = state.conversationCountLabel(),
            tone = if (state.recentConversations.isEmpty()) StatusTone.Neutral else StatusTone.Accent,
        )
        StatusPill(
            text = state.providerCountLabel(),
            tone = if (state.hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
        )
        StatusPill(text = "本地优先", tone = StatusTone.Success)
    }
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
            StatusPill(
                text = conversation.statusLabel(),
                tone = conversation.statusTone(),
            )
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = result.conversation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
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
                title = destination.label,
                description = destination.description,
                onClick = { onDestinationClick(destination) },
            )
        }
    }
}

private fun AppDestination.icon(): ImageVector =
    when (this) {
        AppDestination.Home -> Icons.Filled.Settings
        AppDestination.Chat -> Icons.AutoMirrored.Filled.Chat
        AppDestination.Providers -> Icons.Filled.Tune
        AppDestination.Prompts -> Icons.AutoMirrored.Filled.ViewList
        AppDestination.Images -> Icons.Filled.Image
        AppDestination.Tools -> Icons.Filled.Extension
        AppDestination.Settings -> Icons.Filled.Settings
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

private fun homeSubtitle(state: HomeUiState): String {
    return "${state.conversationCountLabel()} · ${state.providerCountLabel()}"
}

private fun HomeUiState.conversationCountLabel(): String =
    when (recentConversations.size) {
        0 -> "暂无最近会话"
        1 -> "1 个最近会话"
        else -> "${recentConversations.size} 个最近会话"
    }

private fun HomeUiState.providerCountLabel(): String =
    when (enabledProviderCount) {
        0 -> "模型连接未配置"
        1 -> "1 个模型连接可用"
        else -> "${enabledProviderCount} 个模型连接可用"
    }

private fun conversationDescription(conversation: Conversation): String {
    val model = conversation.defaultModel?.takeIf { it.isNotBlank() } ?: "未指定模型"
    return "模型：$model"
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
