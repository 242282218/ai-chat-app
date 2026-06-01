package com.aichat.workbench.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.repository.MessageSearchResult
import com.aichat.workbench.navigation.AppDestination
import com.aichat.workbench.ui.component.IconTile
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
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
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "AI Chat Workbench")
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TaskComposerHero(
                    state = state,
                    onDraftChange = viewModel::updateTaskDraft,
                    onStart = { onStartChat(viewModel.consumeTaskDraft(), false) },
                    onStartTemporary = { onStartChat(viewModel.consumeTaskDraft(), true) },
                    onOpenProviders = { onDestinationClick(AppDestination.Providers) },
                )
            }

            item {
                QuickActionGrid(
                    onNewChat = { onStartChat("", false) },
                    onTemporaryChat = { onStartChat("", true) },
                    onImages = { onDestinationClick(AppDestination.Images) },
                    onSearch = { onDestinationClick(AppDestination.Chat) },
                )
            }

            item {
                SectionHeader(
                    title = "继续",
                    description = "最近的对话和本地消息搜索。",
                )
            }

            item {
                ContinueSection(
                    state = state,
                    onConversationClick = onConversationClick,
                )
            }

            item {
                ConversationSearchPanel(
                    state = state,
                    onQueryChange = viewModel::updateSearchQuery,
                    onResultClick = { result -> onConversationClick(result.conversation.id) },
                )
            }

            item {
                SystemStatusStrip(
                    state = state,
                    onDestinationClick = onDestinationClick,
                )
            }

            item {
                SectionHeader(
                    title = "管理",
                    description = "Provider、Prompt、Tools 和隐私设置放在低频维护区。",
                )
            }

            items(destinations.filterNot { it == AppDestination.Chat || it == AppDestination.Images }, key = { it.route }) {
                destination ->
                ManagementRow(
                    destination = destination,
                    onClick = { onDestinationClick(destination) },
                )
            }
        }
    }
}

@Composable
private fun TaskComposerHero(
    state: HomeUiState,
    onDraftChange: (String) -> Unit,
    onStart: () -> Unit,
    onStartTemporary: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(text = "本地优先", tone = StatusTone.Success)
                StatusPill(text = "BYOK", tone = StatusTone.Neutral)
                if (!state.hasEnabledProvider) {
                    StatusPill(text = "需要 Provider", tone = StatusTone.Warning)
                }
            }
            Text(
                text = "今天想让 AI 帮你做什么？",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (state.hasEnabledProvider) {
                    "输入问题、粘贴材料，或描述要完成的任务。"
                } else {
                    "先添加 OpenAI 或兼容 Provider。请求会从本机发送到你配置的 endpoint。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.taskDraft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "任务输入") },
                placeholder = { Text(text = "例如：总结这段会议记录，列出下一步行动") },
                minLines = 3,
                maxLines = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = if (state.hasEnabledProvider) onStart else onOpenProviders,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (state.hasEnabledProvider) {
                            Icons.AutoMirrored.Filled.Send
                        } else {
                            Icons.Filled.Tune
                        },
                        contentDescription = null,
                    )
                    Text(
                        text = if (state.hasEnabledProvider) "开始聊天" else "配置 Provider 后开始",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = onStartTemporary,
                    enabled = state.hasEnabledProvider,
                ) {
                    Icon(imageVector = Icons.Filled.Bolt, contentDescription = null)
                    Text(text = "临时", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (state.taskDraft.isBlank()) {
                Text(
                    text = "输入内容后可带着草稿进入 Chat；也可以直接新建空对话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    onNewChat: () -> Unit,
    onTemporaryChat: () -> Unit,
    onImages: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "新聊天",
                description = "打开空白对话",
                tone = StatusTone.Accent,
                onClick = onNewChat,
                modifier = Modifier.weight(1f),
            )
            QuickActionCard(
                icon = Icons.Filled.Bolt,
                title = "临时聊天",
                description = "退出后清理",
                tone = StatusTone.Warning,
                onClick = onTemporaryChat,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionCard(
                icon = Icons.Filled.Image,
                title = "图片生成",
                description = "创作图片",
                tone = StatusTone.Neutral,
                onClick = onImages,
                modifier = Modifier.weight(1f),
            )
            QuickActionCard(
                icon = Icons.Filled.Search,
                title = "搜索历史",
                description = "查找消息",
                tone = StatusTone.Success,
                onClick = onSearch,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    tone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
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
            IconTile(icon = icon, tone = tone)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContinueSection(
    state: HomeUiState,
    onConversationClick: (ConversationId) -> Unit,
) {
    if (state.recentConversations.isEmpty()) {
        WorkbenchPanel(
            title = "还没有可以继续的任务",
            description = "先输入一个问题，或创建临时聊天。",
            icon = Icons.Filled.AutoAwesome,
        ) {
            StatusPill(text = "等待输入", tone = StatusTone.Neutral)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.recentConversations.forEach { conversation ->
            RecentConversationRow(
                title = conversation.title,
                description = listOfNotNull(
                    conversation.defaultModel,
                    if (conversation.isTemporary) "临时" else null,
                    if (conversation.isSensitive) "敏感" else null,
                ).ifEmpty { listOf("最近更新") }.joinToString(" · "),
                onClick = { onConversationClick(conversation.id) },
            )
        }
    }
}

@Composable
private fun RecentConversationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon = Icons.AutoMirrored.Filled.Chat, tone = StatusTone.Accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConversationSearchPanel(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onResultClick: (MessageSearchResult) -> Unit,
) {
    WorkbenchPanel(
        title = "搜索对话",
        description = if (state.searchQuery.isBlank()) "本地消息索引" else "${state.searchResults.size} 条结果",
        icon = Icons.Filled.Search,
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "搜索消息") },
            singleLine = true,
        )
        if (state.searchQuery.isNotBlank()) {
            if (state.searchResults.isEmpty()) {
                StatusPill(text = "无结果，可换关键词", tone = StatusTone.Neutral)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.searchResults.take(6).forEach { result ->
                        SearchResultRow(
                            result = result,
                            query = state.searchQuery,
                            onClick = { onResultClick(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: MessageSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon = Icons.AutoMirrored.Filled.Chat, tone = StatusTone.Accent)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = result.conversation.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightedSnippet(result.message.content, query),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

@Composable
private fun SystemStatusStrip(
    state: HomeUiState,
    onDestinationClick: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusSummaryCard(
            icon = Icons.Filled.Tune,
            label = "Provider",
            value = if (state.hasEnabledProvider) "${state.enabledProviderCount} 个可用" else "需要配置",
            tone = if (state.hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
            onClick = { onDestinationClick(AppDestination.Providers) },
            modifier = Modifier.weight(1f),
        )
        StatusSummaryCard(
            icon = Icons.Filled.Shield,
            label = "Tools",
            value = "可选 Gateway",
            tone = StatusTone.Neutral,
            onClick = { onDestinationClick(AppDestination.Tools) },
            modifier = Modifier.weight(1f),
        )
        StatusSummaryCard(
            icon = Icons.Filled.Lock,
            label = "隐私",
            value = "本地保存",
            tone = StatusTone.Success,
            onClick = { onDestinationClick(AppDestination.Settings) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusSummaryCard(
    icon: ImageVector,
    label: String,
    value: String,
    tone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon = icon, tone = tone)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManagementRow(
    destination: AppDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = destination.icon(),
                tone = destination.tone(),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = destination.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                text = destination.badge(),
                tone = destination.tone(),
            )
        }
    }
}

private fun AppDestination.icon(): ImageVector =
    when (this) {
        AppDestination.Chat -> Icons.AutoMirrored.Filled.Chat
        AppDestination.Providers -> Icons.Filled.Tune
        AppDestination.Prompts -> Icons.AutoMirrored.Filled.ViewList
        AppDestination.Images -> Icons.Filled.Image
        AppDestination.Tools -> Icons.Filled.Extension
        AppDestination.Settings -> Icons.Filled.Settings
        AppDestination.Home -> Icons.Filled.AutoAwesome
    }

private fun AppDestination.badge(): String =
    when (this) {
        AppDestination.Chat -> "核心"
        AppDestination.Providers -> "BYOK"
        AppDestination.Prompts -> "本地"
        AppDestination.Images -> "创作"
        AppDestination.Tools -> "Gateway"
        AppDestination.Settings -> "隐私"
        AppDestination.Home -> "首页"
    }

private fun AppDestination.tone(): StatusTone =
    when (this) {
        AppDestination.Chat -> StatusTone.Accent
        AppDestination.Providers -> StatusTone.Success
        AppDestination.Prompts -> StatusTone.Neutral
        AppDestination.Images -> StatusTone.Warning
        AppDestination.Tools -> StatusTone.Warning
        AppDestination.Settings -> StatusTone.Success
        AppDestination.Home -> StatusTone.Accent
    }
