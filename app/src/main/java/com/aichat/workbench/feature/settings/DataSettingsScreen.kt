package com.aichat.workbench.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aichat.workbench.data.backup.BackupImportSummary
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.nio.charset.StandardCharsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingClear by remember { mutableStateOf<ClearAction?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showExportJson by rememberSaveable { mutableStateOf(false) }
    var showImportJson by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val saved = writeText(context, uri, state.exportJson)
            viewModel.updateStatus(if (saved) "导出已保存" else "导出保存失败。")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val importJson = readText(context, uri)
            if (importJson == null) {
                viewModel.updateStatus("读取导入文件失败。")
            } else {
                val json = importJson
                viewModel.updateImportJson(json)
                pendingImportJson = json
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "数据与隐私") },
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
                PrivacySummaryHeader(state = state)
            }
            state.status?.let { status ->
                item {
                    OperationStatusPanel(status = status, isBusy = state.isBusy)
                }
            }
            state.importSummary?.let { summary ->
                item {
                    ImportSummaryPanel(summary)
                }
            }
            item {
                ExportPanel(
                    state = state,
                    showRawJson = showExportJson,
                    onToggleRawJson = { showExportJson = !showExportJson },
                    onIncludeChatsChange = viewModel::updateIncludeChats,
                    onCreateExport = viewModel::createExport,
                    onSaveExport = {
                        exportLauncher.launch("ai-chat-backup.json")
                    },
                )
            }
            item {
                ImportPanel(
                    state = state,
                    showRawJson = showImportJson,
                    onToggleRawJson = { showImportJson = !showImportJson },
                    onImportJsonChange = viewModel::updateImportJson,
                    onImportCurrentJson = { pendingImportJson = state.importJson },
                    onOpenImport = {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
                    },
                )
            }
            item {
                ClearPanel(
                    state = state,
                    onClear = { pendingClear = it },
                )
            }
        }
    }

    pendingClear?.let { action ->
        WorkbenchConfirmDialog(
            title = action.title,
            message = action.message,
            confirmLabel = "清空",
            onConfirm = {
                pendingClear = null
                action.run(viewModel)
            },
            onDismiss = { pendingClear = null },
        )
    }

    pendingImportJson?.let { json ->
        WorkbenchConfirmDialog(
            title = "导入备份？",
            message = "将 ${json.length} 个字符的 JSON 合并到本地存储。服务商 API Key 不会恢复。",
            confirmLabel = "导入",
            onConfirm = {
                pendingImportJson = null
                viewModel.importJson(json)
            },
            onDismiss = { pendingImportJson = null },
            tone = StatusTone.Warning,
        )
    }
}

@Composable
private fun PrivacySummaryHeader(state: DataSettingsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuietSectionHeader(
            title = "隐私摘要",
            description = "先确认数据边界，再做导入、导出或清空。",
            trailing = {
                StatusPill(text = "本地优先", tone = StatusTone.Success)
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(
                    text = if (state.includeChats) "备份含聊天" else "仅备份配置",
                    tone = if (state.includeChats) StatusTone.Warning else StatusTone.Success,
                )
            }
            item {
                StatusPill(text = "聊天保存在本机", tone = StatusTone.Success)
            }
            item {
                StatusPill(text = "API Key 不进备份", tone = StatusTone.Success)
            }
            item {
                StatusPill(text = "网关可选", tone = StatusTone.Neutral)
            }
        }
        MetadataRow(
            label = "模型服务请求",
            value = "聊天、图片和工具请求会直接发送到你配置的接口地址或网关。",
        )
        MetadataRow(
            label = "备份范围",
            value = "导出 JSON 可包含配置和聊天内容，但不会恢复服务商 API Key。",
        )
    }
}

@Composable
private fun ImportSummaryPanel(summary: BackupImportSummary) {
    InlineNotice(
        text = "导入记录已合并到本地存储。",
        icon = Icons.Filled.FileUpload,
        tone = StatusTone.Success,
    ) {
        StatusPill(text = summary.importedObjectsLabel(), tone = StatusTone.Success)
    }
}

@Composable
private fun ExportPanel(
    state: DataSettingsUiState,
    showRawJson: Boolean,
    onToggleRawJson: () -> Unit,
    onIncludeChatsChange: (Boolean) -> Unit,
    onCreateExport: () -> Unit,
    onSaveExport: () -> Unit,
) {
    WorkbenchPanel(
        title = "导出",
        description = "创建不含 API Key 的本地 JSON 备份。",
        icon = Icons.Filled.FileDownload,
        trailing = {
            StatusPill(
                text = if (state.includeChats) "聊天" else "配置",
                tone = if (state.includeChats) StatusTone.Warning else StatusTone.Success,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.includeChats,
                    role = Role.Switch,
                    onValueChange = onIncludeChatsChange,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "包含聊天",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = state.includeChats, onCheckedChange = null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreateExport,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "导出")
            }
            OutlinedButton(
                onClick = onSaveExport,
                enabled = state.exportJson.isNotBlank() && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存")
            }
        }
        BackupJsonSummary(
            json = state.exportJson,
            emptyText = "尚未生成导出内容",
        )
        RawJsonToggle(
            expanded = showRawJson,
            onToggle = onToggleRawJson,
            enabled = state.exportJson.isNotBlank(),
            label = "查看原始导出 JSON",
        )
        if (showRawJson) {
            OutlinedTextField(
                value = state.exportJson,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "导出 JSON") },
                minLines = 4,
                maxLines = 8,
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun ImportPanel(
    state: DataSettingsUiState,
    showRawJson: Boolean,
    onToggleRawJson: () -> Unit,
    onImportJsonChange: (String) -> Unit,
    onImportCurrentJson: () -> Unit,
    onOpenImport: () -> Unit,
) {
    WorkbenchPanel(
        title = "导入",
        description = "将 JSON 数据导入本地存储；服务商 API Key 不会恢复。",
        icon = Icons.Filled.FileUpload,
        trailing = {
            StatusPill(
                text = if (state.importJson.isBlank()) "等待中" else "就绪",
                tone = if (state.importJson.isBlank()) StatusTone.Neutral else StatusTone.Accent,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenImport,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "打开")
            }
            OutlinedButton(
                onClick = onImportCurrentJson,
                enabled = state.importJson.isNotBlank() && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "导入")
            }
        }
        BackupJsonSummary(
            json = state.importJson,
            emptyText = "尚未载入导入内容",
        )
        RawJsonToggle(
            expanded = showRawJson,
            onToggle = onToggleRawJson,
            enabled = true,
            label = if (state.importJson.isBlank()) "粘贴原始导入 JSON" else "查看/编辑原始导入 JSON",
        )
        if (showRawJson) {
            OutlinedTextField(
                value = state.importJson,
                onValueChange = onImportJsonChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "导入 JSON") },
                minLines = 4,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun RawJsonToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    label: String,
) {
    OutlinedButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
private fun OperationStatusPanel(
    status: String,
    isBusy: Boolean,
) {
    InlineNotice(
        text = status,
        icon = Icons.Filled.Security,
        tone = operationStatusTone(status, isBusy),
    ) {
        StatusPill(
            text = operationStatusLabel(status, isBusy),
            tone = operationStatusTone(status, isBusy),
        )
    }
}

@Composable
private fun BackupJsonSummary(
    json: String,
    emptyText: String,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (json.isBlank()) emptyText else "${json.length} 字符",
                tone = if (json.isBlank()) StatusTone.Neutral else StatusTone.Accent,
            )
        }
        if (json.isNotBlank()) {
            item {
                StatusPill(
                    text = "${json.lineSequence().count()} 行",
                    tone = StatusTone.Neutral,
                )
            }
        }
    }
}

private fun operationStatusLabel(
    status: String,
    isBusy: Boolean,
): String =
    when {
        isBusy -> "处理中"
        status.isErrorStatus() -> "错误"
        else -> "完成"
    }

private fun operationStatusTone(
    status: String,
    isBusy: Boolean,
): StatusTone =
    when {
        isBusy -> StatusTone.Accent
        status.isErrorStatus() -> StatusTone.Critical
        else -> StatusTone.Success
    }

private fun String.isErrorStatus(): Boolean {
    val normalized = lowercase()
    return normalized.contains("failed") ||
        normalized.contains("error") ||
        normalized.contains("must not") ||
        normalized.contains("失败") ||
        normalized.contains("错误") ||
        normalized.contains("不能为空")
}

@Composable
private fun ClearPanel(
    state: DataSettingsUiState,
    onClear: (ClearAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuietSectionHeader(
            title = "危险操作",
            description = "每项都会先说明影响范围并二次确认。",
            trailing = {
                StatusPill(text = "破坏性", tone = StatusTone.Critical)
            },
        )
        ClearAction.entries.forEach { action ->
            QuietListRow(
                title = action.buttonText,
                description = action.message,
                icon = Icons.Filled.Delete,
                onClick = { onClear(action) },
                enabled = !state.isBusy,
                trailing = {
                    StatusPill(text = "确认", tone = StatusTone.Critical)
                },
            )
        }
    }
}

private fun BackupImportSummary.importedObjectsLabel(): String =
    "${providers + prompts + conversations + messages} 项"

private enum class ClearAction(
    val buttonText: String,
    val title: String,
    val message: String,
    val run: (DataSettingsViewModel) -> Unit,
) {
    Chats(
        buttonText = "清空聊天",
        title = "清空聊天",
        message = "删除所有会话、消息和工具结果。",
        run = { it.clearChatHistory() },
    ),
    Providers(
        buttonText = "清空模型连接",
        title = "清空模型连接",
        message = "删除模型服务配置、API Key 引用、已保存 API Key，以及这些服务商的模型偏好。",
        run = { it.clearProvidersAndApiKeys() },
    ),
    PromptsModelsImages(
        buttonText = "清空提示词和图片",
        title = "清空提示词和图片",
        message = "删除提示词预设、模型偏好、图片历史和已保存图片文件。",
        run = { it.clearPromptsModelsAndImages() },
    ),
    All(
        buttonText = "清空全部数据",
        title = "清空全部数据",
        message = "删除 AI 聊天管理的全部本地数据。",
        run = { it.clearAllData() },
    ),
}

private fun writeText(context: Context, uri: Uri, value: String): Boolean =
    runCatching {
        val output = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
        output.use {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
        }
        true
    }.getOrDefault(false)

private fun readText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
    }.getOrNull()
