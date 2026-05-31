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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.aichat.workbench.app.AppGraph
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
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
    var pendingClearDraft by rememberSaveable { mutableStateOf(false) }
    var pendingDeletePreset by remember { mutableStateOf<PromptPreset?>(null) }

    val hasPromptDraft = editingId != null ||
        name.isNotBlank() ||
        description.isNotBlank() ||
        systemPrompt.isNotBlank() ||
        defaultModel.isNotBlank() ||
        defaultTools.isNotBlank()

    fun resetForm() {
        editingId = null
        name = ""
        description = ""
        systemPrompt = ""
        defaultModel = ""
        defaultTools = ""
        message = null
    }

    fun requestResetForm() {
        if (hasPromptDraft) {
            pendingClearDraft = true
        } else {
            resetForm()
        }
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
                    IconButton(onClick = { requestResetForm() }) {
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
                    canSave = name.isNotBlank() && systemPrompt.isNotBlank(),
                    canClear = hasPromptDraft,
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
                    onClear = { requestResetForm() },
                )
            }

            item {
                SectionHeader(
                    title = "Saved prompts",
                    description = "Reusable local instructions for fast task switching.",
                )
            }

            if (presets.isEmpty()) {
                item {
                    WorkbenchPanel(
                        title = "No prompt presets",
                        description = "Create one to pin a system prompt, model, and tool set.",
                        icon = Icons.Filled.AutoAwesome,
                    ) {
                        StatusPill(text = "Local", tone = StatusTone.Success)
                    }
                }
            } else {
                items(presets, key = { it.id.value }) { preset ->
                    PromptPresetRow(
                        preset = preset,
                        onClick = { loadPreset(preset) },
                        onDelete = { pendingDeletePreset = preset },
                    )
                }
            }
        }
    }

    pendingDeletePreset?.let { preset ->
        WorkbenchConfirmDialog(
            title = "Delete prompt?",
            message = "This removes \"${preset.name}\" from local prompt presets.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDeletePreset = null
                scope.launch {
                    repository.deletePromptPreset(preset.id)
                    if (editingId == preset.id.value) resetForm()
                }
            },
            onDismiss = { pendingDeletePreset = null },
        )
    }

    if (pendingClearDraft) {
        WorkbenchConfirmDialog(
            title = "Clear prompt draft?",
            message = "Discard the current form values and return to a new prompt draft.",
            confirmLabel = "Clear",
            onConfirm = {
                pendingClearDraft = false
                resetForm()
            },
            onDismiss = { pendingClearDraft = false },
            tone = StatusTone.Warning,
        )
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
    canSave: Boolean,
    canClear: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkbenchPanel(
        title = if (editing) "Edit prompt" else "New prompt",
        description = "Keep repeated instructions local and apply them from chat.",
        icon = Icons.Filled.AutoAwesome,
        modifier = modifier,
        trailing = {
            StatusPill(
                text = if (editing) "Editing" else "Draft",
                tone = if (editing) StatusTone.Accent else StatusTone.Neutral,
            )
        },
    ) {
        PromptFormSummary(
            name = name,
            systemPrompt = systemPrompt,
            defaultModel = defaultModel,
            defaultTools = defaultTools,
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
                onClick = onClear,
                enabled = canClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Clear draft")
            }
        }
        message?.let {
            PromptFormFeedback(
                message = it,
                success = it == "Saved",
            )
        }
    }
}

@Composable
private fun PromptFormSummary(
    name: String,
    systemPrompt: String,
    defaultModel: String,
    defaultTools: String,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (name.isBlank()) "Name required" else "Named",
                tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (systemPrompt.isBlank()) "System required" else "System ready",
                tone = if (systemPrompt.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = defaultModel.ifBlank { "No model" },
                tone = StatusTone.Neutral,
            )
        }
        item {
            StatusPill(
                text = "${parseToolNames(defaultTools).size} tools",
                tone = if (defaultTools.isBlank()) StatusTone.Neutral else StatusTone.Accent,
            )
        }
    }
}

@Composable
private fun PromptFormFeedback(
    message: String,
    success: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
            text = if (success) "Saved" else "Save failed",
            tone = if (success) StatusTone.Success else StatusTone.Critical,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptPresetRow(
    preset: PromptPreset,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    WorkbenchPanel(
        title = preset.name.preview(72),
        description = preset.description ?: preset.systemPrompt.preview(96),
        icon = Icons.Filled.AutoAwesome,
        modifier = Modifier.clickable(onClick = onClick),
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete prompt ${preset.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        PromptPresetSummary(preset)
        MetadataRow(label = "System prompt", value = preset.systemPrompt.preview(128))
    }
}

@Composable
private fun PromptPresetSummary(preset: PromptPreset) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(text = preset.defaultModel ?: "No model", tone = StatusTone.Neutral)
        }
        item {
            StatusPill(
                text = if (preset.defaultToolNames.isEmpty()) "No tools" else "${preset.defaultToolNames.size} tools",
                tone = if (preset.defaultToolNames.isEmpty()) StatusTone.Neutral else StatusTone.Accent,
            )
        }
        item {
            StatusPill(
                text = "${preset.systemPrompt.length} chars",
                tone = StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (preset.description == null) "No description" else "Described",
                tone = if (preset.description == null) StatusTone.Neutral else StatusTone.Success,
            )
        }
    }
}

private fun parseToolNames(value: String): List<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun String.preview(maxLength: Int): String {
    val normalized = trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 3)}..."
    }
}
