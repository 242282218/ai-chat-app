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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.aichat.workbench.ui.component.WorkbenchHero
import com.aichat.workbench.ui.component.WorkbenchPanel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    destinations: List<AppDestination>,
    onDestinationClick: (AppDestination) -> Unit,
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
                    Text(text = "AI 聊天")
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
                WorkbenchHero(
                    eyebrow = "本地优先工作台",
                    title = "AI 聊天",
                    description = "原生工作台，管理 Model 路由、Prompt、图片、搜索和 Sandbox 工具。",
                    icon = Icons.Filled.AutoAwesome,
                ) {
                    StatusPill(text = "本地", tone = StatusTone.Success)
                    StatusPill(text = "BYOK", tone = StatusTone.Neutral)
                    StatusPill(text = "Tools", tone = StatusTone.Accent)
                }
            }

            item {
                TrustStrip()
            }

            item {
                ConversationSearchPanel(
                    state = state,
                    onQueryChange = viewModel::updateSearchQuery,
                    onResultClick = { result -> onConversationClick(result.conversation.id) },
                )
            }

            item {
                SectionHeader(
                    title = "工作台",
                    description = "从具体任务开始，需要时再调整 Provider 和 Tools。",
                )
            }

            items(destinations, key = { it.route }) { destination ->
                DestinationRow(
                    destination = destination,
                    onClick = { onDestinationClick(destination) },
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
                StatusPill(text = "无结果", tone = StatusTone.Neutral)
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
private fun TrustStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrustMetric(
            icon = Icons.Filled.Lock,
            label = "本地数据",
            value = "默认私有",
            modifier = Modifier.weight(1f),
        )
        TrustMetric(
            icon = Icons.Filled.Shield,
            label = "Gateway",
            value = "可选 Tools",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrustMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon = icon, tone = StatusTone.Success)
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
private fun DestinationRow(
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
