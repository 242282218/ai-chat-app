package com.aichat.workbench.feature.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.search.SearchResult
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun ToolTestWorkbench(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    onSendToChat: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolWorkbenchTabs(selectedTab = selectedTab, onTabSelected = onTabSelected)
        when (selectedTab) {
            0 -> SearchWorkbenchContent(state, viewModel, onSendToChat)
            else -> SandboxWorkbenchContent(state, viewModel, onSendToChat)
        }
    }
}

@Composable
private fun ToolWorkbenchTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text(text = "网络搜索") },
                icon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            )
        }
        item {
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text(text = "代码沙箱") },
                icon = { Icon(imageVector = Icons.Filled.Code, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SearchWorkbenchContent(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    onSendToChat: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchPanel(state, viewModel, modifier = Modifier.fillMaxWidth(), framed = false)
        WorkbenchCopyActions(
            onCopyInput = {
                clipboard.setText(AnnotatedString(state.searchWorkbenchInputJson()))
            },
            onCopyOutput = state.searchWorkbenchOutputJson()?.let { outputJson ->
                { clipboard.setText(AnnotatedString(outputJson)) }
            },
            onSendToChat = state.searchWorkbenchChatDraft()?.let { chatDraft ->
                { onSendToChat(chatDraft) }
            },
        )
        state.searchError?.let { error ->
            SearchErrorRow(error, modifier = Modifier.fillMaxWidth())
        }
        if (state.searchResults.isNotEmpty()) {
            QuietSectionHeader(
                title = searchResultHeader(state),
                description = "保留 title / url / snippet，便于回答可追溯。",
            )
            state.searchResults.forEach { result ->
                SearchResultRow(result)
            }
        }
    }
}

@Composable
private fun SandboxWorkbenchContent(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    onSendToChat: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SandboxPanel(state, viewModel, modifier = Modifier.fillMaxWidth(), framed = false)
        WorkbenchCopyActions(
            onCopyInput = {
                clipboard.setText(AnnotatedString(state.sandboxWorkbenchInputJson()))
            },
            onCopyOutput = state.sandboxWorkbenchOutputJson()?.let { outputJson ->
                { clipboard.setText(AnnotatedString(outputJson)) }
            },
            onSendToChat = state.sandboxWorkbenchChatDraft()?.let { chatDraft ->
                { onSendToChat(chatDraft) }
            },
        )
        state.sandboxError?.let { error ->
            SandboxErrorRow(error, modifier = Modifier.fillMaxWidth())
        }
        state.sandboxResult?.let { result ->
            SandboxResultRow(result, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WorkbenchCopyActions(
    onCopyInput: () -> Unit,
    onCopyOutput: (() -> Unit)?,
    onSendToChat: (() -> Unit)? = null,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedButton(onClick = onCopyInput) {
                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "复制参数 JSON")
            }
        }
        if (onCopyOutput != null) {
            item {
                OutlinedButton(onClick = onCopyOutput) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "复制结果 JSON")
                }
            }
        }
        if (onSendToChat != null) {
            item {
                OutlinedButton(onClick = onSendToChat) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "带入聊天")
                }
            }
        }
    }
}

@Composable
private fun SandboxPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        SandboxPanelSummary(state)
        OutlinedTextField(
            value = state.sandboxCode,
            onValueChange = viewModel::updateSandboxCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Python 代码") },
            minLines = 4,
            maxLines = 8,
        )
        Button(
            onClick = viewModel::requestSandboxRun,
            enabled = state.canRunSandbox(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "运行")
        }
    }
    if (framed) {
        WorkbenchPanel(
            title = "代码沙箱",
            description = "通过配置的网关运行短 Python 代码片段。",
            icon = Icons.Filled.Code,
            modifier = modifier,
            trailing = {
                val (label, tone) = sandboxPanelStatus(state)
                StatusPill(text = label, tone = tone)
            },
        ) {
            content()
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuietSectionHeader(
                title = "代码沙箱",
                description = "通过配置的网关运行短 Python 代码片段。",
            )
            val (label, tone) = sandboxPanelStatus(state)
            StatusPill(text = label, tone = tone)
            content()
        }
    }
}

@Composable
private fun SandboxPanelSummary(state: ToolsUiState) {
    val urlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.gatewayEnabled) "网关开启" else "网关关闭",
                tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone())
        }
        item {
            StatusPill(
                text = if (state.hasSandboxTool()) "代码沙箱已加载" else "需要工具清单",
                tone = if (state.hasSandboxTool()) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.sandboxCode.isBlank()) "需要代码" else "代码就绪",
                tone = if (state.sandboxCode.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
    }
}

@Composable
private fun SandboxErrorRow(
    error: ToolError,
    modifier: Modifier = Modifier,
) {
    InlineNotice(
        text = error.diagnosticLabel(),
        icon = Icons.Filled.Security,
        modifier = modifier,
        tone = StatusTone.Critical,
    )
}

@Composable
private fun SandboxResultRow(
    result: SandboxRunResponse,
    modifier: Modifier = Modifier,
) {
    ToolResultContainer(
        title = "代码沙箱结果",
        icon = Icons.Filled.Code,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SandboxResultSummary(result)
            OutputText(label = "stdout", value = result.stdout)
            OutputText(label = "stderr", value = result.stderr)
        }
    }
}

@Composable
private fun SandboxResultSummary(result: SandboxRunResponse) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = "退出码 ${result.exitCode}",
                tone = if (result.exitCode == 0) StatusTone.Success else StatusTone.Critical,
            )
        }
        item {
            StatusPill(text = "${result.durationMs} ms", tone = StatusTone.Neutral)
        }
        if (result.timedOut) {
            item {
                StatusPill(text = "超时", tone = StatusTone.Critical)
            }
        }
        if (result.truncated) {
            item {
                StatusPill(text = "已截断", tone = StatusTone.Warning)
            }
        }
    }
}

@Composable
internal fun OutputText(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value.ifBlank { "(空)" },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun RefilledToolInputCard(
    toolName: String,
    inputJson: String,
    onCopyInput: () -> Unit,
    onCopyChatInstruction: () -> Unit,
    onSendToChat: () -> Unit,
) {
    InlineNotice(
        text = "已回填 $toolName 参数，可复制或带入聊天继续执行。",
        icon = Icons.Filled.Edit,
        tone = StatusTone.Accent,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkbenchIconButton(
                icon = Icons.Filled.ContentCopy,
                label = "复制回填参数",
                onClick = onCopyInput,
            )
            WorkbenchIconButton(
                icon = Icons.Filled.Edit,
                label = "复制聊天指令",
                onClick = onCopyChatInstruction,
            )
            WorkbenchIconButton(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = "带入聊天",
                onClick = onSendToChat,
            )
        }
    }
    OutputText(
        label = "回填参数",
        value = inputJson.rawPayloadPreview(),
    )
}

@Composable
private fun SearchPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        SearchPanelSummary(state)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "搜索 query") },
                singleLine = true,
            )
            Button(
                onClick = viewModel::requestSearch,
                enabled = state.canSearch(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "搜索")
            }
        }
    }
    if (framed) {
        WorkbenchPanel(
            title = "网络搜索",
            description = "先获取结构化来源，再交给模型汇总。",
            icon = Icons.Filled.Search,
            modifier = modifier,
            trailing = {
                val (label, tone) = searchPanelStatus(state)
                StatusPill(text = label, tone = tone)
            },
        ) {
            content()
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuietSectionHeader(
                title = "网络搜索",
                description = "先获取结构化来源，再交给模型汇总。",
            )
            val (label, tone) = searchPanelStatus(state)
            StatusPill(text = label, tone = tone)
            content()
        }
    }
}

@Composable
private fun SearchPanelSummary(state: ToolsUiState) {
    val gatewayUrlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    val localSearchUrlStatus = state.localSearchBaseUrlDraft.gatewayUrlStatus()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.localSearchEnabled) "本地搜索开启" else "本地搜索关闭",
                tone = if (state.localSearchEnabled) StatusTone.Success else StatusTone.Warning,
            )
        }
        if (state.localSearchEnabled) {
            item {
                StatusPill(text = localSearchUrlStatus.label, tone = localSearchUrlStatus.tone())
            }
            item {
                StatusPill(
                    text = if (state.localSearchApiKeyAvailable) "搜索 Key 已保存" else "需要搜索 Key",
                    tone = if (state.localSearchApiKeyAvailable) StatusTone.Success else StatusTone.Warning,
                )
            }
        } else {
            item {
                StatusPill(
                    text = if (state.gatewayEnabled) "网关开启" else "网关关闭",
                    tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Warning,
                )
            }
            item {
                StatusPill(text = gatewayUrlStatus.label, tone = gatewayUrlStatus.tone())
            }
        }
        item {
            StatusPill(
                text = if (state.hasSearchTool()) "搜索工具可用" else "需要工具清单",
                tone = if (state.hasSearchTool()) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.searchQuery.isBlank()) "需要关键词" else "关键词就绪",
                tone = if (state.searchQuery.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
    }
}

@Composable
private fun SearchErrorRow(
    error: ToolError,
    modifier: Modifier = Modifier,
) {
    InlineNotice(
        text = error.diagnosticLabel(),
        icon = Icons.Filled.Public,
        modifier = modifier,
        tone = StatusTone.Critical,
    )
}

@Composable
private fun SearchResultRow(result: SearchResult) {
    val context = LocalContext.current
    ToolResultContainer(
        title = result.title,
        icon = Icons.Filled.Public,
        trailing = {
            WorkbenchIconButton(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = "打开来源：${result.title}",
                onClick = { openUrl(context, result.url) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchResultSummary(result)
            if (result.summary.isNotBlank()) {
                Text(text = result.summary)
            }
            Text(
                text = result.url,
                modifier = Modifier.clickable { openUrl(context, result.url) },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
internal fun ToolResultContainer(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun SearchResultSummary(result: SearchResult) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = result.source.ifBlank { "来源" },
                tone = StatusTone.Neutral,
            )
        }
        result.publishedAt?.let { publishedAt ->
            item {
                StatusPill(text = publishedAt.toString(), tone = StatusTone.Neutral)
            }
        }
    }
}

private fun searchResultHeader(state: ToolsUiState): String =
    buildString {
        append("搜索结果")
        state.searchFetchedAt?.let { fetchedAt ->
            append(" | ")
            append(fetchedAt)
        }
    }

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
