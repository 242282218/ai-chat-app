package com.aichat.workbench.feature.prompt

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichat.workbench.app.AppGraph
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPresetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { AppGraph.promptPresetRepository }
    val savePromptPreset = remember(repository) { SavePromptPresetUseCase(repository) }
    val presets by repository.observePromptPresets().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var defaultModel by rememberSaveable { mutableStateOf("") }
    var defaultTools by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun resetForm() {
        editingId = null
        name = ""
        description = ""
        systemPrompt = ""
        defaultModel = ""
        defaultTools = ""
    }

    fun loadPreset(preset: PromptPreset) {
        editingId = preset.id.value
        name = preset.name
        description = preset.description.orEmpty()
        systemPrompt = preset.systemPrompt
        defaultModel = preset.defaultModel.orEmpty()
        defaultTools = preset.defaultToolNames.joinToString(", ")
        message = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Prompts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { resetForm() }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "New prompt")
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
                PromptPresetForm(
                    editing = editingId != null,
                    name = name,
                    onNameChange = { name = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    systemPrompt = systemPrompt,
                    onSystemPromptChange = { systemPrompt = it },
                    defaultModel = defaultModel,
                    onDefaultModelChange = { defaultModel = it },
                    defaultTools = defaultTools,
                    onDefaultToolsChange = { defaultTools = it },
                    message = message,
                    onSave = {
                        val now = AppGraph.clock.instant()
                        val existing = editingId?.let { id ->
                            presets.firstOrNull { it.id.value == id }
                        }
                        val preset = PromptPreset(
                            id = existing?.id ?: PromptPresetId(UUID.randomUUID().toString()),
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            systemPrompt = systemPrompt.trim(),
                            defaultModel = defaultModel.trim().ifBlank { null },
                            defaultToolNames = parseToolNames(defaultTools),
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                        )

                        scope.launch {
                            runCatching {
                                savePromptPreset(preset)
                            }.onSuccess {
                                resetForm()
                                message = "Saved"
                            }.onFailure { error ->
                                message = error.message ?: "Save failed"
                            }
                        }
                    },
                )
            }

            item {
                Text(
                    text = "Saved prompts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (presets.isEmpty()) {
                item {
                    Text(
                        text = "No prompt presets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(presets, key = { it.id.value }) { preset ->
                    PromptPresetRow(
                        preset = preset,
                        onClick = { loadPreset(preset) },
                        onDelete = {
                            scope.launch {
                                repository.deletePromptPreset(preset.id)
                                if (editingId == preset.id.value) resetForm()
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
private fun PromptPresetForm(
    editing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    defaultModel: String,
    onDefaultModelChange: (String) -> Unit,
    defaultTools: String,
    onDefaultToolsChange: (String) -> Unit,
    message: String?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (editing) "Edit prompt" else "New prompt",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Name") },
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Description") },
            singleLine = true,
        )
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            label = { Text(text = "System prompt") },
        )
        OutlinedTextField(
            value = defaultModel,
            onValueChange = onDefaultModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Default model") },
            singleLine = true,
        )
        OutlinedTextField(
            value = defaultTools,
            onValueChange = onDefaultToolsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Default tools") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
            OutlinedButton(onClick = {
                onNameChange("")
                onDescriptionChange("")
                onSystemPromptChange("")
                onDefaultModelChange("")
                onDefaultToolsChange("")
            }) {
                Text(text = "Clear")
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
private fun PromptPresetRow(
    preset: PromptPreset,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = preset.name) },
        supportingContent = {
            Text(text = preset.description ?: preset.systemPrompt.take(80))
        },
        overlineContent = {
            Text(text = preset.defaultModel ?: "No default model")
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
    )
}

private fun parseToolNames(value: String): List<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
