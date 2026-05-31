package com.aichat.workbench.feature.provider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aichat.workbench.app.AppGraph
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.usecase.DeleteProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { AppGraph.providerConfigRepository }
    val saveProvider = remember(repository) { SaveProviderConfigUseCase(repository) }
    val deleteProvider = remember(repository) { DeleteProviderConfigUseCase(repository) }
    val providers by repository.observeProviders().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("OpenAI") }
    var type by rememberSaveable { mutableStateOf(ProviderType.OpenAI) }
    var baseUrl by rememberSaveable { mutableStateOf("https://api.openai.com/v1") }
    var model by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    var storedApiKeyRef by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResetForm by rememberSaveable { mutableStateOf(false) }
    var pendingLoadProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var pendingDeleteProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    val hasProviderDraft = editingId != null ||
        name != "OpenAI" ||
        type != ProviderType.OpenAI ||
        baseUrl != "https://api.openai.com/v1" ||
        model.isNotBlank() ||
        apiKey.isNotBlank() ||
        headers.isNotBlank() ||
        !enabled ||
        allowHttp ||
        storedApiKeyRef != null

    fun loadProvider(provider: ProviderConfig) {
        editingId = provider.id.value
        name = provider.name
        type = provider.type
        baseUrl = provider.baseUrl
        model = provider.defaultModel.orEmpty()
        apiKey = ""
        headers = provider.headers.entries.joinToString("\n") { (key, value) -> "$key: $value" }
        enabled = provider.enabled
        allowHttp = provider.baseUrl.startsWith("http://")
        storedApiKeyRef = provider.apiKeyRef
        message = null
    }

    fun resetForm() {
        editingId = null
        name = "OpenAI"
        type = ProviderType.OpenAI
        baseUrl = "https://api.openai.com/v1"
        model = ""
        apiKey = ""
        headers = ""
        enabled = true
        allowHttp = false
        storedApiKeyRef = null
        message = null
    }

    fun requestResetForm() {
        if (hasProviderDraft) {
            pendingResetForm = true
        } else {
            resetForm()
        }
    }

    fun requestLoadProvider(provider: ProviderConfig) {
        if (hasProviderDraft) {
            pendingLoadProvider = provider
        } else {
            loadProvider(provider)
        }
    }

    fun currentProvider(): ProviderConfig {
        val providerId = ProviderId(editingId ?: UUID.randomUUID().toString())
        val trimmedModel = model.trim()
        return ProviderConfig(
            id = providerId,
            name = name.trim(),
            type = type,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKeyRef = null,
            headers = parseHeaderLines(headers),
            models = if (trimmedModel.isBlank()) {
                emptyList()
            } else {
                listOf(ModelConfig(trimmedModel, trimmedModel, capability = null))
            },
            defaultModel = trimmedModel.ifBlank { null },
            enabled = enabled,
        )
    }
    val canSubmitProvider =
        name.isNotBlank() &&
            baseUrl.isValidProviderBaseUrl(allowHttp) &&
            headers.hasValidHeaderLines()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Providers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { requestResetForm() }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "New provider")
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ProviderForm(
                    editing = editingId != null,
                    name = name,
                    onNameChange = { name = it },
                    type = type,
                    onTypeChange = {
                        type = it
                        if (it == ProviderType.OpenAI && editingId == null) {
                            baseUrl = "https://api.openai.com/v1"
                        }
                    },
                    baseUrl = baseUrl,
                    onBaseUrlChange = { baseUrl = it },
                    model = model,
                    onModelChange = { model = it },
                    apiKey = apiKey,
                    hasStoredKey = storedApiKeyRef != null,
                    onApiKeyChange = { apiKey = it },
                    headers = headers,
                    onHeadersChange = { headers = it },
                    enabled = enabled,
                    onEnabledChange = { enabled = it },
                    allowHttp = allowHttp,
                    onAllowHttpChange = { allowHttp = it },
                    message = message,
                    canSave = canSubmitProvider,
                    canTest = canSubmitProvider,
                    onSave = {
                        val provider = currentProvider()

                        scope.launch {
                            runCatching {
                                saveProvider(provider, apiKey.trim(), allowHttp)
                            }.onSuccess {
                                resetForm()
                                message = "Saved"
                            }.onFailure { error ->
                                message = error.message ?: "Save failed"
                            }
                        }
                    },
                    onTest = {
                        val provider = currentProvider()
                        if (provider.baseUrl.startsWith("http://") && !allowHttp) {
                            message = "Enable Allow HTTP before testing this URL."
                            return@ProviderForm
                        }
                        scope.launch {
                            message = "Testing..."
                            val storedKey = if (apiKey.isBlank()) {
                                repository.getApiKey(provider.id)
                            } else {
                                null
                            }
                            val result = AppGraph.providerConnectionTester.test(
                                provider = provider,
                                apiKey = apiKey.trim().ifBlank { storedKey.orEmpty() },
                            )
                            message = if (result.ok) {
                                "${result.message} (${result.statusCode})"
                            } else {
                                result.message
                            }
                        }
                    },
                )
            }

            item {
                SectionHeader(
                    title = "Configured",
                    description = "Stored locally; API keys stay behind encrypted references.",
                )
            }

            if (providers.isEmpty()) {
                item {
                    WorkbenchPanel(
                        title = "No providers",
                        description = "Add a provider to unlock chat, images, and model routing.",
                        icon = Icons.Filled.Tune,
                    ) {
                        StatusPill(text = "Setup needed", tone = StatusTone.Warning)
                    }
                }
            } else {
                items(providers, key = { it.id.value }) { provider ->
                    ProviderRow(
                        provider = provider,
                        onClick = { requestLoadProvider(provider) },
                        onDelete = { pendingDeleteProvider = provider },
                    )
                }
            }
        }
    }

    pendingDeleteProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "Delete provider?",
            message = "This removes \"${provider.name}\" plus its stored key reference from this device.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDeleteProvider = null
                scope.launch {
                    deleteProvider(provider.id)
                    if (editingId == provider.id.value) {
                        resetForm()
                    }
                }
            },
            onDismiss = { pendingDeleteProvider = null },
        )
    }

    pendingLoadProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "Discard provider draft?",
            message = "Discard the current form values and load \"${provider.name}\".",
            confirmLabel = "Load",
            onConfirm = {
                pendingLoadProvider = null
                loadProvider(provider)
            },
            onDismiss = { pendingLoadProvider = null },
            tone = StatusTone.Warning,
        )
    }

    if (pendingResetForm) {
        WorkbenchConfirmDialog(
            title = "Clear provider draft?",
            message = "Discard the current provider form values and return to a new provider draft.",
            confirmLabel = "Clear",
            onConfirm = {
                pendingResetForm = false
                resetForm()
            },
            onDismiss = { pendingResetForm = false },
            tone = StatusTone.Warning,
        )
    }
}

@Composable
private fun ProviderForm(
    editing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    type: ProviderType,
    onTypeChange: (ProviderType) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    apiKey: String,
    hasStoredKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit,
    message: String?,
    canSave: Boolean,
    canTest: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    WorkbenchPanel(
        title = if (editing) "Edit provider" else "New provider",
        description = "Bring your own key; requests go directly to the configured endpoint.",
        icon = Icons.Filled.Tune,
        modifier = modifier,
        trailing = {
            StatusPill(
                text = if (enabled) "Enabled" else "Disabled",
                tone = if (enabled) StatusTone.Success else StatusTone.Neutral,
            )
        },
    ) {
        ProviderFormSummary(
            name = name,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            hasStoredKey = hasStoredKey,
            headers = headers,
            allowHttp = allowHttp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == ProviderType.OpenAI,
                onClick = { onTypeChange(ProviderType.OpenAI) },
                label = { Text(text = "OpenAI") },
            )
            FilterChip(
                selected = type == ProviderType.OpenAICompatible,
                onClick = { onTypeChange(ProviderType.OpenAICompatible) },
                label = { Text(text = "Compatible") },
            )
        }

        MetadataRow(
            label = "Provider type",
            value = type.providerTypeLabel(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Name") },
            singleLine = true,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Base URL") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Default model") },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "API key") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            visualTransformation = if (showApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                    )
                }
            },
        )
        OutlinedTextField(
            value = headers,
            onValueChange = onHeadersChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            label = { Text(text = "Headers") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enabled",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = enabled, onCheckedChange = null)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = allowHttp,
                    role = Role.Checkbox,
                    onValueChange = onAllowHttpChange,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = allowHttp, onCheckedChange = null)
            Text(text = "Allow HTTP")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
            OutlinedButton(
                onClick = onTest,
                enabled = canTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Test")
            }
        }

        message?.let {
            ProviderFormFeedback(message = it)
        }
    }
}

@Composable
private fun ProviderFormSummary(
    name: String,
    baseUrl: String,
    model: String,
    apiKey: String,
    hasStoredKey: Boolean,
    headers: String,
    allowHttp: Boolean,
) {
    val urlStatus = baseUrl.providerUrlStatus(allowHttp)
    val headerStatus = headers.headerStatus()
    val keyStatus = providerKeyStatus(apiKey, hasStoredKey)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (name.isBlank()) "Name required" else "Named",
                tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone)
        }
        item {
            StatusPill(
                text = model.ifBlank { "No default model" },
                tone = if (model.isBlank()) StatusTone.Neutral else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = keyStatus.label,
                tone = keyStatus.tone,
            )
        }
        item {
            StatusPill(
                text = headerStatus.label,
                tone = headerStatus.tone,
            )
        }
    }
}

@Composable
private fun ProviderFormFeedback(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
            text = providerMessageLabel(message),
            tone = providerMessageTone(message),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun providerMessageLabel(message: String): String =
    when {
        message == "Saved" -> "Saved"
        message == "Testing..." -> "Testing"
        providerMessageTone(message) == StatusTone.Critical -> "Attention"
        else -> "Connection"
    }

private fun providerMessageTone(message: String): StatusTone {
    val normalized = message.lowercase()
    return when {
        message == "Saved" -> StatusTone.Success
        message == "Testing..." -> StatusTone.Accent
        normalized.contains("failed") ||
            normalized.contains("invalid") ||
            normalized.contains("missing") ||
            normalized.contains("returned") ||
            normalized.contains("enable allow http") ||
            normalized.contains("must") ->
            StatusTone.Critical
        else -> StatusTone.Success
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    WorkbenchPanel(
        title = provider.name,
        description = "${provider.type.providerTypeLabel()} / ${provider.defaultModel ?: "No default model"}",
        icon = Icons.Filled.CheckCircle,
        modifier = Modifier.clickable(onClick = onClick),
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete provider ${provider.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ProviderRowSummary(provider)
        MetadataRow(label = "Base URL", value = provider.baseUrl)
    }
}

@Composable
private fun ProviderRowSummary(provider: ProviderConfig) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (provider.enabled) "Enabled" else "Disabled",
                tone = if (provider.enabled) StatusTone.Success else StatusTone.Neutral,
            )
        }
        item {
            StatusPill(
                text = if (provider.apiKeyRef == null) "No key" else "Key stored",
                tone = if (provider.apiKeyRef == null) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(text = provider.type.providerTypeLabel(), tone = StatusTone.Neutral)
        }
        item {
            StatusPill(
                text = if (provider.models.isEmpty()) "No models" else "${provider.models.size} models",
                tone = if (provider.models.isEmpty()) StatusTone.Warning else StatusTone.Success,
            )
        }
        if (provider.supportsImageGeneration()) {
            item {
                StatusPill(text = "Images", tone = StatusTone.Accent)
            }
        }
        if (provider.supportsToolCalling()) {
            item {
                StatusPill(text = "Tools", tone = StatusTone.Accent)
            }
        }
    }
}

private fun ProviderConfig.supportsImageGeneration(): Boolean =
    models.any { it.capability?.imageGeneration == true }

private fun ProviderConfig.supportsToolCalling(): Boolean =
    models.any { it.capability?.toolCalling == true }
