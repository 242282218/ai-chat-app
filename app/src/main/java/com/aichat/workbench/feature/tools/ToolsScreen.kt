package com.aichat.workbench.feature.tools

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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.ui.component.MetadataRow
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
    onSendToChat: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
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
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "工具",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = toolsTopBarSubtitle(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        WorkbenchIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "返回",
                            onClick = onBack,
                        )
                    }
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
                    state = state,
                    setting = state.toolSettings.runtimeSettingFor(tool),
                    onConfirm = { viewModel.requestPermission(tool) },
                    onEnabledChange = { enabled -> viewModel.updateToolEnabled(tool.name, enabled) },
                    onPolicyChange = { policy -> viewModel.updateToolPermissionPolicy(tool.name, policy) },
                    onRerunLatest = viewModel::rerunToolResult,
                    onRefillLatest = viewModel::refillToolResult,
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
                        onSendToChat = onSendToChat,
                    )
                }
            }
            item {
                ToolHistorySection(
                    state = state,
                    viewModel = viewModel,
                    onSendToChat = onSendToChat,
                )
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
                    LocalSearchSettingsPanel(
                        state = state,
                        viewModel = viewModel,
                    )
                }
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

private fun toolsTopBarSubtitle(state: ToolsUiState): String =
    when {
        state.isLoading -> "处理中 · ${state.tools.size} 个工具"
        state.localSearchEnabled -> "本地搜索可用 · ${state.tools.size} 个工具"
        !state.gatewayEnabled -> "本地工具可用 · 网关关闭"
        state.remoteTools.isEmpty() -> "网关已启用 · 清单未加载"
        else -> "${state.tools.size} 个工具 · ${state.remoteTools.size} 个来自网关"
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
                    StatusPill(
                        text = if (state.localSearchEnabled) "本地搜索已启用" else "搜索工具已加载",
                        tone = StatusTone.Success,
                    )
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
                "代码沙箱取决于工具清单；搜索优先使用本地 Provider。"
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
            item {
                StatusPill(
                    text = if (state.localSearchEnabled) "本地搜索开启" else "本地搜索关闭",
                    tone = if (state.localSearchEnabled) StatusTone.Success else StatusTone.Neutral,
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
private fun LocalSearchSettingsPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    val urlStatus = state.localSearchBaseUrlDraft.gatewayUrlStatus()
    var localSearchApiKeyDraft by remember { mutableStateOf("") }
    var showSearchApiKey by remember { mutableStateOf(false) }
    val hasLocalSearchApiKeyInput = localSearchApiKeyDraft.isNotBlank()
    WorkbenchPanel(
        title = "本地搜索",
        description = "App 端直接调用搜索 Provider，聊天中的 web_search_local 会使用这里的配置。",
        icon = Icons.Filled.Search,
        trailing = {
            Switch(
                checked = state.localSearchEnabled,
                onCheckedChange = viewModel::updateLocalSearchEnabled,
            )
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(
                    text = if (state.localSearchEnabled) "已启用" else "未启用",
                    tone = if (state.localSearchEnabled) StatusTone.Success else StatusTone.Neutral,
                )
            }
            item {
                StatusPill(text = urlStatus.label, tone = urlStatus.tone())
            }
            item {
                StatusPill(
                    text = when {
                        hasLocalSearchApiKeyInput -> "Key 已输入"
                        state.localSearchApiKeyAvailable -> "Key 已保存"
                        else -> "Key 未输入"
                    },
                    tone = if (hasLocalSearchApiKeyInput || state.localSearchApiKeyAvailable) {
                        StatusTone.Success
                    } else {
                        StatusTone.Warning
                    },
                )
            }
        }
        MetadataRow(label = "Provider", value = state.localSearchProvider.name)
        OutlinedTextField(
            value = state.localSearchBaseUrlDraft,
            onValueChange = viewModel::updateLocalSearchBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Search Base URL") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        OutlinedTextField(
            value = localSearchApiKeyDraft,
            onValueChange = { localSearchApiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Search API Key") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showSearchApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                WorkbenchIconButton(
                    icon = if (showSearchApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (showSearchApiKey) "隐藏 Search API Key" else "显示 Search API Key",
                    onClick = { showSearchApiKey = !showSearchApiKey },
                )
            },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.localSearchMaxResultsDraft,
                onValueChange = viewModel::updateLocalSearchMaxResults,
                modifier = Modifier.weight(1f),
                label = { Text(text = "结果数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.localSearchDepthDraft,
                onValueChange = viewModel::updateLocalSearchDepth,
                modifier = Modifier.weight(1f),
                label = { Text(text = "深度") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = state.localSearchTopicDraft,
            onValueChange = viewModel::updateLocalSearchTopic,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Topic") },
            singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.saveLocalSearchSettings(localSearchApiKeyDraft.takeIf(String::isNotBlank))
                    localSearchApiKeyDraft = ""
                },
                enabled = state.canSaveLocalSearchSettings(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存本地搜索")
            }
            OutlinedButton(
                onClick = {
                    viewModel.saveLocalSearchSettings("")
                    localSearchApiKeyDraft = ""
                },
                enabled = state.localSearchApiKeyAvailable && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "清除搜索 Key")
            }
        }
    }
}

@Composable
private fun GatewaySettingsPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    val gatewayUrlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    var gatewayApiTokenDraft by remember { mutableStateOf("") }
    var showGatewayApiToken by remember { mutableStateOf(false) }
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
            value = gatewayApiTokenDraft,
            onValueChange = { gatewayApiTokenDraft = it },
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
                onClick = {
                    viewModel.saveGatewaySettings(gatewayApiTokenDraft.takeIf(String::isNotBlank))
                    gatewayApiTokenDraft = ""
                },
                enabled = state.canSaveGatewaySettings(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存")
            }
            OutlinedButton(
                onClick = {
                    viewModel.saveGatewaySettings("")
                    gatewayApiTokenDraft = ""
                },
                enabled = state.gatewayApiTokenAvailable && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "清除 Token")
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
        item {
            StatusPill(
                text = if (state.gatewayApiTokenAvailable) "Token 已保存" else "需要 Token",
                tone = if (state.gatewayApiTokenAvailable) StatusTone.Success else StatusTone.Warning,
            )
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
private fun ToolPermissionDialog(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val warning = when (tool.permissionLevel) {
        ToolPermissionLevel.ReadOnly ->
            "该工具为只读。"
        ToolPermissionLevel.Network ->
            "该工具会通过已配置的搜索 Provider、模型服务或网关访问网络。"
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
