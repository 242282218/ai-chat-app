package com.aichat.workbench.feature.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.requiresConfirmation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = viewModel(factory = ToolsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Tools") },
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
                GatewaySettingsPanel(state, viewModel)
            }
            item {
                SearchPanel(state, viewModel)
            }
            state.searchError?.let { error ->
                item {
                    SearchErrorRow(error)
                }
            }
            if (state.searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = searchResultHeader(state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.searchResults, key = { it.url }) { result ->
                    SearchResultRow(result)
                }
            }
            item {
                SandboxPanel(state, viewModel)
            }
            state.sandboxError?.let { error ->
                item {
                    SandboxErrorRow(error)
                }
            }
            state.sandboxResult?.let { result ->
                item {
                    SandboxResultRow(result)
                }
            }
            item {
                Text(
                    text = "Available tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.tools, key = { "${it.source}:${it.name}" }) { tool ->
                ToolRow(
                    tool = tool,
                    onConfirm = { viewModel.requestPermission(tool) },
                )
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
private fun SandboxPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Code sandbox",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = state.sandboxCode,
            onValueChange = viewModel::updateSandboxCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Python code") },
            minLines = 4,
            maxLines = 8,
        )
        Button(
            onClick = viewModel::requestSandboxRun,
            enabled = !state.isLoading,
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Run")
        }
    }
}

@Composable
private fun SandboxErrorRow(error: ToolError) {
    ListItem(
        overlineContent = { Text(text = "Sandbox error") },
        headlineContent = { Text(text = error.code) },
        supportingContent = { Text(text = error.message) },
    )
}

@Composable
private fun SandboxResultRow(result: SandboxRunResponse) {
    ListItem(
        overlineContent = { Text(text = sandboxResultMetadata(result)) },
        headlineContent = { Text(text = "Sandbox result", fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputText(label = "stdout", value = result.stdout)
                OutputText(label = "stderr", value = result.stderr)
            }
        },
    )
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
            text = value.ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun sandboxResultMetadata(result: SandboxRunResponse): String =
    buildList {
        add("language ${result.language}")
        add("exit code ${result.exitCode}")
        add("${result.durationMs} ms")
        if (result.timedOut) {
            add("timeout")
        }
        if (result.truncated) {
            add("truncated")
        }
    }.joinToString(" | ")

@Composable
private fun SearchPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Web search",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.weight(1f),
                label = { Text(text = "Search query") },
                singleLine = true,
            )
            Button(
                onClick = viewModel::requestSearch,
                enabled = !state.isLoading,
            ) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Search")
            }
        }
    }
}

@Composable
private fun SearchErrorRow(error: ToolError) {
    ListItem(
        overlineContent = { Text(text = "Search error") },
        headlineContent = { Text(text = error.code) },
        supportingContent = { Text(text = error.message) },
    )
}

@Composable
private fun SearchResultRow(result: SearchResult) {
    val context = LocalContext.current
    ListItem(
        overlineContent = {
            Text(text = searchResultMetadata(result))
        },
        headlineContent = {
            Text(
                text = result.title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.summary.isNotBlank()) {
                    Text(text = result.summary)
                }
                Text(
                    text = result.url,
                    modifier = Modifier.clickable { openUrl(context, result.url) },
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { openUrl(context, result.url) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open source",
                )
            }
        },
    )
}

private fun searchResultHeader(state: ToolsUiState): String =
    buildString {
        append("Search results")
        state.searchFetchedAt?.let { fetchedAt ->
            append(" | ")
            append(fetchedAt)
        }
    }

private fun searchResultMetadata(result: SearchResult): String =
    listOfNotNull(
        result.source.takeIf { it.isNotBlank() },
        result.publishedAt?.toString(),
    ).joinToString(" | ")

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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Gateway",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Switch(
                checked = state.gatewayEnabled,
                onCheckedChange = viewModel::updateGatewayEnabled,
            )
        }
        OutlinedTextField(
            value = state.gatewayBaseUrlDraft,
            onValueChange = viewModel::updateGatewayBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Gateway URL") },
            singleLine = true,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = viewModel::saveGatewaySettings) {
                    Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Save")
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::checkHealth,
                    enabled = !state.isLoading,
                ) {
                    Text(text = "Health")
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::fetchManifest,
                    enabled = state.gatewayEnabled && !state.isLoading,
                ) {
                    Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Manifest")
                }
            }
        }
        state.status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolRow(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = tool.displayName) },
        supportingContent = { Text(text = tool.description) },
        overlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text(text = tool.permissionLevel.name) },
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text(text = tool.source.name) },
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = if (tool.permissionLevel.requiresConfirmation()) {
                        "Confirm permission"
                    } else {
                        "Read-only tool"
                    },
                )
            }
        },
    )
}

@Composable
private fun ToolPermissionDialog(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val warning = when (tool.permissionLevel) {
        com.aichat.workbench.domain.model.ToolPermissionLevel.ReadOnly ->
            "This tool is read-only."
        com.aichat.workbench.domain.model.ToolPermissionLevel.Network ->
            "This tool will access the network through the configured gateway."
        com.aichat.workbench.domain.model.ToolPermissionLevel.Execute ->
            "This tool will run code in the remote gateway sandbox."
        com.aichat.workbench.domain.model.ToolPermissionLevel.HighRisk ->
            "This tool can perform high-risk actions and must be reviewed carefully."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tool.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = warning)
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
