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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            writeText(context, uri, state.exportJson)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            readText(context, uri)?.let { json ->
                viewModel.updateImportJson(json)
                viewModel.importJson(json)
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
                    onImportCurrentJson = viewModel::importCurrentJson,
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
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.importSummary?.let { summary ->
                item {
                    ListItem(
                        overlineContent = { Text(text = "Import summary") },
                        headlineContent = {
                            Text(
                                text = "${summary.providers} providers, ${summary.prompts} prompts, ${summary.conversations} conversations",
                            )
                        },
                        supportingContent = {
                            Text(text = "${summary.modelPreferences} model preferences, ${summary.messages} messages")
                        },
                    )
                }
            }
        }
    }

    pendingClear?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(text = action.title) },
            text = { Text(text = action.message) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingClear = null
                        action.run(viewModel)
                    },
                ) {
                    Text(text = "Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) {
                    Text(text = "Cancel")
                }
            },
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Export",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Switch(
                checked = state.includeChats,
                onCheckedChange = onIncludeChatsChange,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(
                    onClick = onCreateExport,
                    enabled = !state.isBusy,
                ) {
                    Icon(imageVector = Icons.Filled.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Export")
                }
            }
            item {
                OutlinedButton(
                    onClick = onSaveExport,
                    enabled = state.exportJson.isNotBlank() && !state.isBusy,
                ) {
                    Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Save")
                }
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Import",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(
                    onClick = onOpenImport,
                    enabled = !state.isBusy,
                ) {
                    Icon(imageVector = Icons.Filled.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Open")
                }
            }
            item {
                OutlinedButton(
                    onClick = onImportCurrentJson,
                    enabled = state.importJson.isNotBlank() && !state.isBusy,
                ) {
                    Text(text = "Import")
                }
            }
        }
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
private fun ClearPanel(
    state: DataSettingsUiState,
    onClear: (ClearAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Clear data",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClearAction.entries.forEach { action ->
                item {
                    OutlinedButton(
                        onClick = { onClear(action) },
                        enabled = !state.isBusy,
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = action.buttonText)
                    }
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
        buttonText = "Chats",
        title = "Clear chats",
        message = "Delete all conversations, messages, and tool results.",
        run = { it.clearChatHistory() },
    ),
    Providers(
        buttonText = "Providers",
        title = "Clear providers",
        message = "Delete provider configs, API key references, stored API keys, and model preferences for those providers.",
        run = { it.clearProvidersAndApiKeys() },
    ),
    PromptsModelsImages(
        buttonText = "Prompts/images",
        title = "Clear prompts and images",
        message = "Delete prompt presets, model preferences, image history, and stored image files.",
        run = { it.clearPromptsModelsAndImages() },
    ),
    All(
        buttonText = "All",
        title = "Clear all data",
        message = "Delete all local app data managed by AI Chat.",
        run = { it.clearAllData() },
    ),
}

private fun writeText(context: Context, uri: Uri, value: String) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(value.toByteArray(StandardCharsets.UTF_8))
        }
    }
}

private fun readText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
    }.getOrNull()
