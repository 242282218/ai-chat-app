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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel

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
                    SectionHeader(
                        title = searchResultHeader(state),
                        description = "Original links stay visible so answers remain traceable.",
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
                SectionHeader(
                    title = "Available tools",
                    description = "Permissions are explicit before network or execution actions run.",
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
    WorkbenchPanel(
        title = "Code sandbox",
        description = "Run short Python snippets through the configured gateway.",
        icon = Icons.Filled.Code,
        trailing = {
            val (label, tone) = sandboxPanelStatus(state)
            StatusPill(text = label, tone = tone)
        },
    ) {
        SandboxPanelSummary(state)
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
            enabled = state.canRunSandbox(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Run")
        }
    }
}

@Composable
private fun SandboxPanelSummary(state: ToolsUiState) {
    val urlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.gatewayEnabled) "Gateway on" else "Gateway off",
                tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone())
        }
        item {
            StatusPill(
                text = if (state.hasSandboxTool()) "Sandbox loaded" else "Manifest needed",
                tone = if (state.hasSandboxTool()) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.sandboxCode.isBlank()) "Code required" else "Code ready",
                tone = if (state.sandboxCode.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
    }
}

@Composable
private fun SandboxErrorRow(error: ToolError) {
    WorkbenchPanel(
        title = error.code,
        description = error.message,
        icon = Icons.Filled.Security,
    ) {
        StatusPill(text = "Sandbox error", tone = StatusTone.Critical)
    }
}

@Composable
private fun SandboxResultRow(result: SandboxRunResponse) {
    WorkbenchPanel(
        title = "Sandbox result",
        icon = Icons.Filled.Code,
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
                text = "Exit ${result.exitCode}",
                tone = if (result.exitCode == 0) StatusTone.Success else StatusTone.Critical,
            )
        }
        item {
            StatusPill(text = "${result.durationMs} ms", tone = StatusTone.Neutral)
        }
        if (result.timedOut) {
            item {
                StatusPill(text = "Timeout", tone = StatusTone.Critical)
            }
        }
        if (result.truncated) {
            item {
                StatusPill(text = "Truncated", tone = StatusTone.Warning)
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
            text = value.ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SearchPanel(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    WorkbenchPanel(
        title = "Web search",
        description = "Fetch structured sources before the model summarizes them.",
        icon = Icons.Filled.Search,
        trailing = {
            val (label, tone) = searchPanelStatus(state)
            StatusPill(text = label, tone = tone)
        },
    ) {
        SearchPanelSummary(state)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Search query") },
                singleLine = true,
            )
            Button(
                onClick = viewModel::requestSearch,
                enabled = state.canSearch(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Search")
            }
        }
    }
}

@Composable
private fun SearchPanelSummary(state: ToolsUiState) {
    val urlStatus = state.gatewayBaseUrlDraft.gatewayUrlStatus()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.gatewayEnabled) "Gateway on" else "Gateway off",
                tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone())
        }
        item {
            StatusPill(
                text = if (state.hasSearchTool()) "Search loaded" else "Manifest needed",
                tone = if (state.hasSearchTool()) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.searchQuery.isBlank()) "Query required" else "Query ready",
                tone = if (state.searchQuery.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
    }
}

@Composable
private fun SearchErrorRow(error: ToolError) {
    WorkbenchPanel(
        title = error.code,
        description = error.message,
        icon = Icons.Filled.Public,
    ) {
        StatusPill(text = "Search error", tone = StatusTone.Critical)
    }
}

@Composable
private fun SearchResultRow(result: SearchResult) {
    val context = LocalContext.current
    WorkbenchPanel(
        title = result.title,
        icon = Icons.Filled.Public,
        trailing = {
            IconButton(onClick = { openUrl(context, result.url) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open source: ${result.title}",
                )
            }
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
private fun SearchResultSummary(result: SearchResult) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = result.source.ifBlank { "Source" },
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
        append("Search results")
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
    val gatewayUrlValid = state.gatewayBaseUrlDraft.isValidGatewayBaseUrl()
    WorkbenchPanel(
        title = "Gateway",
        description = "Optional boundary for web search, manifests, and code execution.",
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
                label = "State",
                value = if (state.gatewayEnabled) "Enabled" else "Disabled",
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::saveGatewaySettings,
                enabled = gatewayUrlValid && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
            OutlinedButton(
                onClick = viewModel::checkHealth,
                enabled = gatewayUrlValid && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Health")
            }
            OutlinedButton(
                onClick = viewModel::fetchManifest,
                enabled = state.gatewayEnabled &&
                    gatewayUrlValid &&
                    !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Manifest")
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
                StatusPill(text = "Working", tone = StatusTone.Accent)
            }
        }
        item {
            StatusPill(
                text = if (state.gatewayEnabled) "Gateway on" else "Gateway off",
                tone = if (state.gatewayEnabled) StatusTone.Success else StatusTone.Neutral,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone())
        }
        if (state.remoteTools.isNotEmpty()) {
            item {
                StatusPill(text = "${state.remoteTools.size} tools", tone = StatusTone.Accent)
            }
        }
    }
}

@Composable
private fun ToolStatusFeedback(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
            text = toolStatusLabel(message),
            tone = toolStatusTone(message),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolRow(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
) {
    WorkbenchPanel(
        title = tool.displayName,
        description = tool.description,
        icon = tool.permissionIcon(),
        trailing = {
            IconButton(onClick = onConfirm) {
                Icon(
                    imageVector = tool.permissionIcon(),
                    contentDescription = tool.permissionActionDescription(),
                )
            }
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(
                    text = tool.permissionLevel.displayLabel(),
                    tone = tool.permissionTone(),
                )
            }
            item {
                StatusPill(text = tool.source.displayLabel(), tone = StatusTone.Neutral)
            }
        }
    }
}

private fun ToolDescriptor.permissionIcon(): ImageVector =
    when (permissionLevel) {
        ToolPermissionLevel.ReadOnly -> Icons.Filled.Info
        ToolPermissionLevel.Network -> Icons.Filled.Public
        ToolPermissionLevel.Execute -> Icons.Filled.Code
        ToolPermissionLevel.HighRisk -> Icons.Filled.Security
    }

private fun ToolDescriptor.permissionActionDescription(): String =
    if (permissionLevel.requiresConfirmation()) {
        "Review ${permissionLevel.displayLabel()} permission for $displayName"
    } else {
        "$displayName is read-only"
    }

@Composable
private fun ToolPermissionDialog(
    tool: ToolDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val warning = when (tool.permissionLevel) {
        ToolPermissionLevel.ReadOnly ->
            "This tool is read-only."
        ToolPermissionLevel.Network ->
            "This tool will access the network through the configured gateway."
        ToolPermissionLevel.Execute ->
            "This tool will run code in the remote gateway sandbox."
        ToolPermissionLevel.HighRisk ->
            "This tool can perform high-risk actions and must be reviewed carefully."
    }
    WorkbenchConfirmDialog(
        title = tool.displayName,
        message = "$warning\n\n${tool.description}",
        confirmLabel = "Confirm",
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
        ToolPermissionLevel.ReadOnly -> "Read-only"
        ToolPermissionLevel.Network -> "Network"
        ToolPermissionLevel.Execute -> "Execute"
        ToolPermissionLevel.HighRisk -> "High risk"
    }

private fun ToolSource.displayLabel(): String =
    when (this) {
        ToolSource.BuiltIn -> "Built-in"
        ToolSource.Gateway -> "Gateway"
    }

private fun searchPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    when {
        state.isLoading -> "Working" to StatusTone.Accent
        state.canSearch() -> "Ready" to StatusTone.Success
        else -> "Needs setup" to StatusTone.Warning
    }

private fun sandboxPanelStatus(state: ToolsUiState): Pair<String, StatusTone> =
    when {
        state.isLoading -> "Working" to StatusTone.Accent
        state.canRunSandbox() -> "Ready" to StatusTone.Success
        else -> "Needs setup" to StatusTone.Warning
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
        toolStatusTone(message) == StatusTone.Critical -> "Attention"
        message == "Saved" -> "Saved"
        else -> "Status"
    }

private fun toolStatusTone(message: String): StatusTone {
    val normalized = message.lowercase()
    return when {
        normalized.contains("failed") ||
            normalized.contains("must not") ||
            normalized.contains("enable gateway") ||
            normalized.contains("load gateway") ||
            normalized.contains("invalid") -> StatusTone.Critical
        message == "Saved" -> StatusTone.Success
        else -> StatusTone.Accent
    }
}
