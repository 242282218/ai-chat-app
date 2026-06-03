package com.aichat.workbench.feature.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedWorkbenchTab by rememberSaveable { mutableIntStateOf(0) }
    var showGatewayEditor by rememberSaveable { mutableStateOf(false) }
    var toolWorkbenchExpanded by rememberSaveable { mutableStateOf(false) }
    val gatewayEditorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(
        state.searchFetchedAt,
        state.searchError,
        state.sandboxResult,
        state.sandboxError,
    ) {
        if (state.hasToolWorkbenchOutput()) {
            toolWorkbenchExpanded = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "工具") },
                navigationIcon = {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GatewayStatusHeader(state = state)
            }
            item {
                GatewayActionStrip(
                    state = state,
                    onEditGateway = { showGatewayEditor = true },
                    onCheckHealth = viewModel::checkHealth,
                    onFetchManifest = viewModel::fetchManifest,
                )
            }
            item {
                ToolCatalogHeader(state = state)
            }
            items(state.tools, key = { "${it.source}:${it.name}" }) { tool ->
                ToolRow(
                    tool = tool,
                    onConfirm = { viewModel.requestPermission(tool) },
                )
            }
            item {
                ToolWorkbenchDisclosure(
                    state = state,
                    expanded = toolWorkbenchExpanded,
                    onToggleExpanded = { toolWorkbenchExpanded = !toolWorkbenchExpanded },
                )
            }
            if (toolWorkbenchExpanded) {
                item {
                    ToolTestWorkbench(
                        selectedTab = selectedWorkbenchTab,
                        onTabSelected = { selectedWorkbenchTab = it },
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    if (showGatewayEditor) {
        ModalBottomSheet(
            onDismissRequest = { showGatewayEditor = false },
            sheetState = gatewayEditorSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GatewaySettingsPanel(
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    state.pendingConfirmation?.let { tool ->
        ToolPermissionDialog(
            tool = tool,
            onConfirm = viewModel::confirmPermission,
            onDismiss = viewModel::dismissPermission,
        )
    }
}

@Composable
private fun GatewayActionStrip(
    state: ToolsUiState,
    onEditGateway: () -> Unit,
    onCheckHealth: () -> Unit,
    onFetchManifest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "网关操作",
            description = "默认只保留编辑、健康检查和清单加载。",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(
                    onClick = onEditGateway,
                ) {
                    Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (state.gatewayEnabled) "编辑网关" else "配置网关")
                }
            }
            item {
                OutlinedButton(
                    onClick = onCheckHealth,
                    enabled = state.canCheckGatewayHealth(),
                ) {
                    Text(text = "健康检查")
                }
            }
            item {
                OutlinedButton(
                    onClick = onFetchManifest,
                    enabled = state.canFetchGatewayManifest(),
                ) {
                    Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "加载清单")
                }
            }
        }
    }
}

@Composable
private fun ToolCatalogHeader(state: ToolsUiState) {
    val networkCount = state.tools.count { it.permissionLevel == ToolPermissionLevel.Network }
    val executeCount = state.tools.count {
        it.permissionLevel == ToolPermissionLevel.Execute ||
            it.permissionLevel == ToolPermissionLevel.HighRisk
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "工具清单",
            description = "权限级别同时用文字和图标表达；联网或执行类操作运行前会确认。",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = "${state.tools.size} 个工具", tone = StatusTone.Accent)
            }
            if (networkCount > 0) {
                item {
                    StatusPill(text = "$networkCount 个联网", tone = StatusTone.Warning)
                }
            }
            if (executeCount > 0) {
                item {
                    StatusPill(text = "$executeCount 个执行类", tone = StatusTone.Critical)
                }
            }
        }
    }
}

@Composable
private fun ToolWorkbenchDisclosure(
    state: ToolsUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "工具试运行",
            description = if (expanded) {
                "调试网络搜索和代码沙箱；结果会保留来源、退出码和输出。"
            } else {
                "按需展开调试工具，默认页面优先展示权限清单。"
            },
            trailing = {
                StatusPill(
                    text = toolWorkbenchStatusLabel(state),
                    tone = toolWorkbenchStatusTone(state),
                )
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (expanded) "收起试运行" else "展开试运行")
                }
            }
            if (state.hasSearchTool()) {
                item {
                    StatusPill(text = "搜索工具已加载", tone = StatusTone.Success)
                }
            }
            if (state.hasSandboxTool()) {
                item {
                    StatusPill(text = "沙箱工具已加载", tone = StatusTone.Success)
                }
            }
        }
    }
}

@Composable
private fun GatewayStatusHeader(state: ToolsUiState) {
    val gatewayUrlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuietSectionHeader(
            title = "网关状态",
            description = if (state.gatewayEnabled) {
                "网络搜索和代码沙箱取决于工具清单中的能力。"
            } else {
                "可选能力，关闭时聊天仍可用。"
            },
            trailing = {
                StatusPill(
                    text = if (state.gatewayEnabled) "已启用" else "未启用",
                    tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Neutral,
                )
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = gatewayUrlStatus.label, tone = gatewayUrlStatus.tone())
            }
            item {
                StatusPill(
                    text = if (state.remoteTools.isEmpty()) "工具清单未加载" else "${state.remoteTools.size} 个网关工具",
                    tone = when {
                        state.remoteTools.isNotEmpty() -> StatusTone.Success
                        state.gatewayEnabled -> StatusTone.Warning
                        else -> StatusTone.Neutral
                    },
                )
            }
            if (state.remoteTools.isNotEmpty()) {
                item {
                    StatusPill(
                        text = if (state.hasSearchTool()) "网络搜索可用" else "网络搜索未提供",
                        tone = if (state.hasSearchTool()) StatusTone.Success else StatusTone.Neutral,
                    )
                }
                item {
                    StatusPill(
                        text = if (state.hasSandboxTool()) "代码沙箱可用" else "代码沙箱未提供",
                        tone = if (state.hasSandboxTool()) StatusTone.Success else StatusTone.Neutral,
                    )
                }
            }
            if (state.isLoading) {
                item {
                    StatusPill(text = "处理中", tone = StatusTone.Accent)
                }
            }
        }
        MetadataRow(
            label = "Base URL",
            value = state.gatewayBaseUrlDraft.ifBlank { "未配置" },
        )
        state.status?.let { message ->
            ToolStatusFeedback(message)
        }
    }
}

@Composable
private fun ToolTestWorkbench(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolWorkbenchTabs(selectedTab = selectedTab, onTabSelected = onTabSelected)
        when (selectedTab) {
            0 -> SearchWorkbenchContent(state, viewModel)
            else -> SandboxWorkbenchContent(state, viewModel)
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchPanel(state, viewModel, modifier = Modifier.fillMaxWidth(), framed = false)
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SandboxPanel(state, viewModel, modifier = Modifier.fillMaxWidth(), framed = false)
        state.sandboxError?.let { error ->
            SandboxErrorRow(error, modifier = Modifier.fillMaxWidth())
        }
        state.sandboxResult?.let { result ->
            SandboxResultRow(result, modifier = Modifier.fillMaxWidth())
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
        text = "${error.code}: ${error.message}",
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
private fun OutputText(
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
                text = if (state.hasSearchTool()) "网络搜索已加载" else "需要工具清单",
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
        text = "${error.code}: ${error.message}",
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
private fun ToolResultContainer(
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

@Composable
private fun GatewaySettingsPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    val gatewayUrlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    var showGatewayApiToken by rememberSaveable { mutableStateOf(false) }
    WorkbenchPanel(
        title = "工具网关",
        description = "网络搜索、工具清单和代码执行的可选边界。",
        icon = Icons.Filled.CloudSync,
        trailing = {
            Switch(
                checked = state.gatewayEnabled,
                onCheckedChange = viewModel::updateGatewayEnabled,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetadataRow(
                label = "状态",
                value = if (state.gatewayEnabled) "已启用" else "已禁用",
            )
        }
        GatewaySettingsSummary(state, gatewayUrlStatus)
        OutlinedTextField(
            value = state.gatewayBaseUrlDraft,
            onValueChange = viewModel::updateGatewayBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Gateway URL") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.gatewayApiTokenDraft,
            onValueChange = viewModel::updateGatewayApiToken,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Gateway API Token") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showGatewayApiToken) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                WorkbenchIconButton(
                    icon = if (showGatewayApiToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (showGatewayApiToken) "隐藏 Gateway API Token" else "显示 Gateway API Token",
                    onClick = { showGatewayApiToken = !showGatewayApiToken },
                )
            },
            singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::saveGatewaySettings,
                enabled = state.canSaveGatewaySettings(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存")
            }
            OutlinedButton(
                onClick = viewModel::checkHealth,
                enabled = state.canCheckGatewayHealth(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "健康检查")
            }
            OutlinedButton(
                onClick = viewModel::fetchManifest,
                enabled = state.canFetchGatewayManifest(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "加载工具清单")
            }
        }
        state.status?.let { message ->
            ToolStatusFeedback(message)
        }
    }
}

@Composable
private fun GatewaySettingsSummary(
    state: ToolsUiState,
    urlStatus: GatewayUrlStatus,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isLoading) {
            item {
                StatusPill(text = "处理中", tone = StatusTone.Accent)
            }
        }
        item {
            StatusPill(
                text = if (state.gatewayEnabled) "网关开启" else "网关关闭",
                tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Neutral,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone())
        }
        if (state.gatewayApiTokenDraft.isNotBlank()) {
            item {
                StatusPill(text = "Token 已输入", tone = StatusTone.Success)
            }
        }
        if (state.remoteTools.isNotEmpty()) {
            item {
                StatusPill(text = "${state.remoteTools.size} 个工具", tone = StatusTone.Accent)
            }
        }
    }
}

@Composable
private fun ToolStatusFeedback(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            text = toolStatusLabel(message),
            tone = toolStatusTone(message),
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolRow(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
) {
    QuietListRow(
        title = tool.displayName,
        description = "${tool.permissionLevel.displayLabel()} / ${tool.source.displayLabel()} · ${tool.description}",
        icon = tool.permissionIcon(),
        onClick = onConfirm,
        trailing = {
            StatusPill(
                text = tool.permissionLevel.displayLabel(),
                tone = tool.permissionTone(),
            )
        },
    )
}

private fun ToolDescriptor.permissionIcon(): ImageVector =
    when (permissionLevel) {
        ToolPermissionLevel.ReadOnly -> Icons.Filled.Info
        ToolPermissionLevel.Network -> Icons.Filled.Public
        ToolPermissionLevel.Execute -> Icons.Filled.Code
        ToolPermissionLevel.HighRisk -> Icons.Filled.Security
    }

@Composable
private fun ToolPermissionDialog(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val warning = when (tool.permissionLevel) {
        ToolPermissionLevel.ReadOnly ->
            "该工具为只读。"
        ToolPermissionLevel.Network ->
            "该工具会通过配置的网关访问网络。"
        ToolPermissionLevel.Execute ->
            "该工具会在远端网关的代码沙箱中运行代码。"
        ToolPermissionLevel.HighRisk ->
            "该工具可执行高风险操作，请仔细确认。"
    }
    WorkbenchConfirmDialog(
        title = tool.displayName,
        message = "$warning\n\n${tool.description}",
        confirmLabel = "确认",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = tool.permissionTone(),
    )
}

private fun ToolDescriptor.permissionTone(): StatusTone =
    when (permissionLevel) {
        ToolPermissionLevel.ReadOnly -> StatusTone.Neutral
        ToolPermissionLevel.Network -> StatusTone.Warning
        ToolPermissionLevel.Execute,
        ToolPermissionLevel.HighRisk,
        -> StatusTone.Critical
    }

private fun ToolPermissionLevel.displayLabel(): String =
    when (this) {
        ToolPermissionLevel.ReadOnly -> "只读"
        ToolPermissionLevel.Network -> "联网"
        ToolPermissionLevel.Execute -> "执行"
        ToolPermissionLevel.HighRisk -> "高风险"
    }

private fun ToolSource.displayLabel(): String =
    when (this) {
        ToolSource.BuiltIn -> "内置"
        ToolSource.Gateway -> "Gateway"
        ToolSource.Official -> "官方"
    }

private fun searchPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    state.searchActionStatus().toStatusTone()

private fun sandboxPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    state.sandboxActionStatus().toStatusTone()

private fun GatewayActionStatus.toStatusTone(): Pair<String, StatusTone> =
    when {
        isBusy -> label to StatusTone.Accent
        isReady -> label to StatusTone.Success
        else -> label to StatusTone.Warning
    }

private fun GatewayUrlStatus.tone(): StatusTone =
    when {
        isValid && isWarning -> StatusTone.Warning
        isValid -> StatusTone.Success
        isWarning -> StatusTone.Warning
        else -> StatusTone.Critical
    }

private fun toolStatusLabel(message: String): String =
    when {
        toolStatusTone(message) == StatusTone.Critical -> "需要处理"
        message == "已保存" -> "已保存"
        else -> "状态"
    }

private fun toolStatusTone(message: String): StatusTone {
    val normalized = message.lowercase()
    return when {
        normalized.contains("failed") ||
            normalized.contains("must not") ||
            normalized.contains("enable gateway") ||
            normalized.contains("load gateway") ||
            normalized.contains("invalid") ||
            normalized.contains("不能为空") ||
            normalized.contains("无效") ||
            normalized.contains("请启用 gateway") ||
            normalized.contains("请先加载 gateway") ||
            normalized.contains("失败") -> StatusTone.Critical
        message == "已保存" -> StatusTone.Success
        else -> StatusTone.Accent
    }
}

private fun ToolsUiState.hasToolWorkbenchOutput(): Boolean =
    searchResults.isNotEmpty() ||
        searchError != null ||
        sandboxResult != null ||
        sandboxError != null

private fun toolWorkbenchStatusLabel(state: ToolsUiState): String =
    when {
        state.isLoading -> "处理中"
        state.hasToolWorkbenchOutput() -> "已有结果"
        state.hasSearchTool() || state.hasSandboxTool() -> "可试运行"
        else -> "需要清单"
    }

private fun toolWorkbenchStatusTone(state: ToolsUiState): StatusTone =
    when {
        state.isLoading -> StatusTone.Accent
        state.hasToolWorkbenchOutput() -> StatusTone.Success
        state.hasSearchTool() || state.hasSandboxTool() -> StatusTone.Neutral
        else -> StatusTone.Warning
    }
