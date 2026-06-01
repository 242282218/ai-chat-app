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
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPresetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<PromptPresetRepository>()
    val clock = koinInject<Clock>()
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
                title = { Text(text = "Prompt 预设") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { requestResetForm() }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新建 Prompt")
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
                        val now = clock.instant()
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
                                message = "已保存"
                            }.onFailure { error ->
                                message = error.message ?: "保存失败"
                            }
                        }
                    },
                    onClear = { requestResetForm() },
                )
            }

            item {
                SectionHeader(
                    title = "已保存的 Prompt",
                    description = "用于快速切换任务的本地复用指令。",
                )
            }

            if (presets.isEmpty()) {
                item {
                    WorkbenchPanel(
                        title = "暂无 Prompt 预设",
                        description = "创建后可固定 system prompt、Model 和 Tool 组合。",
                        icon = Icons.Filled.AutoAwesome,
                    ) {
                        StatusPill(text = "本地", tone = StatusTone.Success)
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
            title = "删除 Prompt？",
            message = "这会从本地 Prompt 预设中删除「${preset.name}」。",
            confirmLabel = "删除",
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
            title = "清空 Prompt 草稿？",
            message = "丢弃当前表单内容并回到新 Prompt 草稿。",
            confirmLabel = "清空",
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
        title = if (editing) "编辑 Prompt" else "新建 Prompt",
        description = "重复使用的指令保存在本地，可在聊天中应用。",
        icon = Icons.Filled.AutoAwesome,
        modifier = modifier,
        trailing = {
            StatusPill(
                text = if (editing) "编辑中" else "草稿",
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
            label = { Text(text = "名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "描述") },
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
            label = { Text(text = "默认 Model") },
            singleLine = true,
        )
        OutlinedTextField(
            value = defaultTools,
            onValueChange = onDefaultToolsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "默认 Tools") },
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
                Text(text = "保存")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = canClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "清空草稿")
            }
        }
        message?.let {
            PromptFormFeedback(
                message = it,
                success = it == "已保存",
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
                text = if (name.isBlank()) "需要名称" else "已命名",
                tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (systemPrompt.isBlank()) "需要 System" else "System 就绪",
                tone = if (systemPrompt.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = defaultModel.ifBlank { "无 Model" },
                tone = StatusTone.Neutral,
            )
        }
        item {
            StatusPill(
                text = "${parseToolNames(defaultTools).size} 个 Tools",
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
            text = if (success) "已保存" else "保存失败",
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
                    contentDescription = "删除 Prompt ${preset.name}",
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
            StatusPill(text = preset.defaultModel ?: "无 Model", tone = StatusTone.Neutral)
        }
        item {
            StatusPill(
                text = if (preset.defaultToolNames.isEmpty()) "无 Tools" else "${preset.defaultToolNames.size} 个 Tools",
                tone = if (preset.defaultToolNames.isEmpty()) StatusTone.Neutral else StatusTone.Accent,
            )
        }
        item {
            StatusPill(
                text = "${preset.systemPrompt.length} 字符",
                tone = StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (preset.description == null) "无描述" else "已描述",
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
