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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataSettingsViewModel = viewModel(factory = DataSettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingClear by remember { mutableStateOf<ClearAction?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val saved = writeText(context, uri, state.exportJson)
            viewModel.updateStatus(if (saved) "Export saved" else "Export save failed.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val importJson = readText(context, uri)
            if (importJson == null) {
                viewModel.updateStatus("Import file read failed.")
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
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                ExportPanel(
                    state = state,
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
            state.status?.let { status ->
                item {
                    OperationStatusPanel(status = status, isBusy = state.isBusy)
                }
            }
            state.importSummary?.let { summary ->
                item {
                    WorkbenchPanel(
                        title = "Import summary",
                        description = "Imported records were merged into local storage.",
                        icon = Icons.Filled.FileUpload,
                    ) {
                        MetadataRow(label = "Providers", value = summary.providers.toString())
                        MetadataRow(label = "Prompts", value = summary.prompts.toString())
                        MetadataRow(label = "Conversations", value = summary.conversations.toString())
                        MetadataRow(label = "Messages", value = summary.messages.toString())
                    }
                }
            }
        }
    }

    pendingClear?.let { action ->
        WorkbenchConfirmDialog(
            title = action.title,
            message = action.message,
            confirmLabel = "Clear",
            onConfirm = {
                pendingClear = null
                action.run(viewModel)
            },
            onDismiss = { pendingClear = null },
        )
    }

    pendingImportJson?.let { json ->
        WorkbenchConfirmDialog(
            title = "Import backup?",
            message = "Merge ${json.length} characters of JSON into local storage. Provider API keys are not restored.",
            confirmLabel = "Import",
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
private fun ExportPanel(
    state: DataSettingsUiState,
    onIncludeChatsChange: (Boolean) -> Unit,
    onCreateExport: () -> Unit,
    onSaveExport: () -> Unit,
) {
    WorkbenchPanel(
        title = "Export",
        description = "Create a local JSON backup without API keys.",
        icon = Icons.Filled.FileDownload,
        trailing = {
            StatusPill(
                text = if (state.includeChats) "Chats" else "Config",
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
                text = "Include chats",
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
                Text(text = "Export")
            }
            OutlinedButton(
                onClick = onSaveExport,
                enabled = state.exportJson.isNotBlank() && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
        }
        BackupJsonSummary(
            json = state.exportJson,
            emptyText = "No export generated",
        )
        OutlinedTextField(
            value = state.exportJson,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Export JSON") },
            minLines = 4,
            maxLines = 8,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun ImportPanel(
    state: DataSettingsUiState,
    onImportJsonChange: (String) -> Unit,
    onImportCurrentJson: () -> Unit,
    onOpenImport: () -> Unit,
) {
    WorkbenchPanel(
        title = "Import",
        description = "Load JSON data into local storage; provider keys are not restored.",
        icon = Icons.Filled.FileUpload,
        trailing = {
            StatusPill(
                text = if (state.importJson.isBlank()) "Waiting" else "Ready",
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
                Text(text = "Open")
            }
            OutlinedButton(
                onClick = onImportCurrentJson,
                enabled = state.importJson.isNotBlank() && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Import")
            }
        }
        BackupJsonSummary(
            json = state.importJson,
            emptyText = "No import loaded",
        )
        OutlinedTextField(
            value = state.importJson,
            onValueChange = onImportJsonChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Import JSON") },
            minLines = 4,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun OperationStatusPanel(
    status: String,
    isBusy: Boolean,
) {
    WorkbenchPanel(
        title = "Status",
        description = status,
        icon = Icons.Filled.Security,
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
                text = if (json.isBlank()) emptyText else "${json.length} chars",
                tone = if (json.isBlank()) StatusTone.Neutral else StatusTone.Accent,
            )
        }
        if (json.isNotBlank()) {
            item {
                StatusPill(
                    text = "${json.lineSequence().count()} lines",
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
        isBusy -> "Working"
        status.isErrorStatus() -> "Error"
        else -> "Done"
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
        normalized.contains("must not")
}

@Composable
private fun ClearPanel(
    state: DataSettingsUiState,
    onClear: (ClearAction) -> Unit,
) {
    WorkbenchPanel(
        title = "Clear data",
        description = "Destructive actions are grouped and confirmed before deletion.",
        icon = Icons.Filled.Delete,
        trailing = {
            StatusPill(text = "Destructive", tone = StatusTone.Critical)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClearAction.entries.forEach { action ->
                OutlinedButton(
                    onClick = { onClear(action) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = action.buttonText)
                }
            }
        }
    }
}

private enum class ClearAction(
    val buttonText: String,
    val title: String,
    val message: String,
    val run: (DataSettingsViewModel) -> Unit,
) {
    Chats(
        buttonText = "Clear chats",
        title = "Clear chats",
        message = "Delete all conversations, messages, and tool results.",
        run = { it.clearChatHistory() },
    ),
    Providers(
        buttonText = "Clear providers",
        title = "Clear providers",
        message = "Delete provider configs, API key references, stored API keys, and model preferences for those providers.",
        run = { it.clearProvidersAndApiKeys() },
    ),
    PromptsModelsImages(
        buttonText = "Clear prompts/images",
        title = "Clear prompts and images",
        message = "Delete prompt presets, model preferences, image history, and stored image files.",
        run = { it.clearPromptsModelsAndImages() },
    ),
    All(
        buttonText = "Clear all data",
        title = "Clear all data",
        message = "Delete all local app data managed by AI Chat.",
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
