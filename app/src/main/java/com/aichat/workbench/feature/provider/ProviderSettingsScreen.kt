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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aichat.workbench.app.AppGraph
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.usecase.DeleteProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
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
    var message by rememberSaveable { mutableStateOf<String?>(null) }

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
                    onApiKeyChange = { apiKey = it },
                    headers = headers,
                    onHeadersChange = { headers = it },
                    enabled = enabled,
                    onEnabledChange = { enabled = it },
                    allowHttp = allowHttp,
                    onAllowHttpChange = { allowHttp = it },
                    message = message,
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
                Text(
                    text = "Configured",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (providers.isEmpty()) {
                item {
                    Text(
                        text = "No providers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(providers, key = { it.id.value }) { provider ->
                    ProviderRow(
                        provider = provider,
                        onClick = { loadProvider(provider) },
                        onDelete = {
                            scope.launch {
                                deleteProvider(provider.id)
                                if (editingId == provider.id.value) {
                                    resetForm()
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.48f))
                }
            }
        }
    }
}

@Composable
private fun ProviderForm(
    name: String,
    onNameChange: (String) -> Unit,
    type: ProviderType,
    onTypeChange: (ProviderType) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit,
    message: String?,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = headers,
            onValueChange = onHeadersChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            label = { Text(text = "Headers") },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enabled",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = allowHttp, onCheckedChange = onAllowHttpChange)
            Text(text = "Allow HTTP")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = "Save")
            }
            OutlinedButton(onClick = onTest) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = "Test")
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = provider.name) },
        supportingContent = {
            Text(text = "${provider.type.name} / ${provider.defaultModel ?: "No default model"}")
        },
        overlineContent = {
            Text(text = if (provider.apiKeyRef == null) "No key" else "Key stored")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    )
    Spacer(modifier = Modifier.height(0.dp))
}

private fun parseHeaderLines(value: String): Map<String, String> =
    value.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null

            val name = line.substring(0, separator).trim()
            val headerValue = line.substring(separator + 1).trim()
            if (name.isBlank() || headerValue.isBlank()) null else name to headerValue
        }
        .toMap()
