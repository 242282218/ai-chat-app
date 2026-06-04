package com.aichat.workbench.feature.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.time.Clock
import java.util.UUID
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val presets by repository.observePromptPresets().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var defaultModel by rememberSaveable { mutableStateOf("") }
    var defaultTools by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingClearDraft by rememberSaveable { mutableStateOf(false) }
    var showPromptEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDeletePreset by remember { mutableStateOf<PromptPreset?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filteredPresets = remember(presets, searchQuery) {
        presets.filterByQuery(searchQuery)
    }
    val saveStatus = promptSaveStatus(
        name = name,
        systemPrompt = systemPrompt,
    )

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
            showPromptEditor = true
        }
    }

    fun openNewPromptEditor() {
        resetForm()
        showPromptEditor = true
    }

    fun loadPreset(preset: PromptPreset) {
        editingId = preset.id.value
        name = preset.name
        description = preset.description.orEmpty()
        systemPrompt = preset.systemPrompt
        defaultModel = preset.defaultModel.orEmpty()
        defaultTools = preset.defaultToolNames.joinToString(", ")
        message = null
        showPromptEditor = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openNewPromptEditor() },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = "新建提示词") },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "提示词",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = promptTopBarSubtitle(presets, filteredPresets, searchQuery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
            contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                PromptLibraryHeader(
                    presets = presets,
                )
            }

            item {
                PromptSearchPanel(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    resultCount = filteredPresets.size,
                )
            }

            if (presets.isEmpty()) {
                item {
                    EmptyPromptLibraryState(onCreate = { openNewPromptEditor() })
                }
            } else if (filteredPresets.isEmpty()) {
                item {
                    InlineNotice(
                        text = "没有匹配的提示词。换一个关键词，或清空搜索后浏览全部预设。",
                        icon = Icons.Filled.Search,
                        tone = StatusTone.Neutral,
                    ) {
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(text = "清空搜索")
                        }
                    }
                }
            } else {
                items(filteredPresets, key = { it.id.value }) { preset ->
                    PromptPresetRow(
                        preset = preset,
                        onClick = { loadPreset(preset) },
                        onDelete = { pendingDeletePreset = preset },
                    )
                }
            }
        }
    }

    if (showPromptEditor) {
        ModalBottomSheet(
            onDismissRequest = { showPromptEditor = false },
            sheetState = editorSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        formKey = editingId ?: "new",
                        message = message,
                        canSave = saveStatus.isReady,
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
                                defaultToolNames = parsePromptToolNames(defaultTools),
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now,
                            )

                            scope.launch {
                                runCatching {
                                    savePromptPreset(preset)
                                }.onSuccess {
                                    resetForm()
                                    showPromptEditor = false
                                }.onFailure { error ->
                                    message = error.message ?: "保存失败"
                                }
                            }
                        },
                        onClear = { requestResetForm() },
                    )
                }
            }
        }
    }

    pendingDeletePreset?.let { preset ->
        WorkbenchConfirmDialog(
            title = "删除提示词？",
            message = "这会从本地提示词库中删除「${preset.name}」。",
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
            title = "清空提示词草稿？",
            message = "丢弃当前表单内容并回到新提示词草稿。",
            confirmLabel = "清空",
            onConfirm = {
                pendingClearDraft = false
                resetForm()
                showPromptEditor = true
            },
            onDismiss = { pendingClearDraft = false },
            tone = StatusTone.Warning,
        )
    }
}

@Composable
private fun EmptyPromptLibraryState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "还没有提示词",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "保存常用系统指令，聊天时按需套用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCreate) {
            Text(text = "创建提示词")
        }
    }
}

private fun promptTopBarSubtitle(
    presets: List<PromptPreset>,
    filteredPresets: List<PromptPreset>,
    searchQuery: String,
): String =
    when {
        presets.isEmpty() -> "本地提示词库为空"
        searchQuery.isNotBlank() -> "${filteredPresets.size} 个匹配 · ${presets.size} 个总数"
        else -> "${presets.size} 个本地模板"
    }

@Composable
private fun PromptLibraryHeader(
    presets: List<PromptPreset>,
) {
    val withModelCount = presets.count { it.defaultModel != null }
    val withToolsCount = presets.count { it.defaultToolNames.isNotEmpty() }
    val describedCount = presets.count { it.description != null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietSectionHeader(
            title = "提示词库",
            description = "本地任务模板，聊天页按需应用。",
            trailing = {
                StatusPill(
                    text = "${presets.size} 个",
                    tone = if (presets.isEmpty()) StatusTone.Neutral else StatusTone.Success,
                )
            },
        )
        if (withModelCount > 0 || withToolsCount > 0 || describedCount > 0) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (withModelCount > 0) {
                    item {
                        StatusPill(text = "$withModelCount 个带默认模型", tone = StatusTone.Accent)
                    }
                }
                if (withToolsCount > 0) {
                    item {
                        StatusPill(text = "$withToolsCount 个带工具", tone = StatusTone.Accent)
                    }
                }
                if (describedCount > 0) {
                    item {
                        StatusPill(text = "$describedCount 个已描述", tone = StatusTone.Success)
                    }
                }
            }
        }
        Text(
            text = "提示词保存在本机，可固定系统指令、默认模型和工具组合。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "搜索",
            description = if (query.isBlank()) "按名称、描述、系统指令、模型或工具搜索。" else "$resultCount 个匹配结果",
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "搜索提示词") },
            placeholder = { Text(text = "搜索提示词") },
            singleLine = true,
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
    formKey: String,
    message: String?,
    canSave: Boolean,
    canClear: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDefaultsDraft = defaultModel.isNotBlank() || defaultTools.isNotBlank()
    var defaultsExpanded by rememberSaveable(formKey) { mutableStateOf(hasDefaultsDraft) }

    WorkbenchPanel(
        title = if (editing) "编辑提示词" else "新建提示词",
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
            label = { Text(text = "名称 *") },
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
            label = { Text(text = "系统指令 *") },
        )
        PromptDefaultsFields(
            expanded = defaultsExpanded,
            defaultModel = defaultModel,
            defaultTools = defaultTools,
            onToggleExpanded = { defaultsExpanded = !defaultsExpanded },
            onDefaultModelChange = onDefaultModelChange,
            onDefaultToolsChange = onDefaultToolsChange,
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
private fun PromptDefaultsFields(
    expanded: Boolean,
    defaultModel: String,
    defaultTools: String,
    onToggleExpanded: () -> Unit,
    onDefaultModelChange: (String) -> Unit,
    onDefaultToolsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onToggleExpanded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (expanded) "收起默认上下文" else "默认上下文",
                modifier = Modifier.weight(1f),
            )
            if (defaultModel.isNotBlank() || defaultTools.isNotBlank()) {
                StatusPill(
                    text = promptDefaultsLabel(defaultModel, defaultTools),
                    tone = promptDefaultsTone(defaultModel, defaultTools),
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = defaultModel,
                onValueChange = onDefaultModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "默认模型") },
                singleLine = true,
            )
            OutlinedTextField(
                value = defaultTools,
                onValueChange = onDefaultToolsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "默认工具") },
                singleLine = true,
            )
        }
    }
}

private fun promptDefaultsTone(defaultModel: String, defaultTools: String): StatusTone =
    if (defaultModel.isNotBlank() || defaultTools.isNotBlank()) {
        StatusTone.Accent
    } else {
        StatusTone.Neutral
    }

@Composable
private fun PromptFormSummary(
    name: String,
    systemPrompt: String,
    defaultModel: String,
    defaultTools: String,
) {
    val toolCount = parsePromptToolNames(defaultTools).size
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (name.isBlank()) "需要名称" else "已命名",
                tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (systemPrompt.isBlank()) "需要系统指令" else "系统指令就绪",
                tone = if (systemPrompt.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        if (defaultModel.isNotBlank()) {
            item {
                StatusPill(text = defaultModel, tone = StatusTone.Accent)
            }
        }
        if (toolCount > 0) {
            item {
                StatusPill(text = "$toolCount 个工具", tone = StatusTone.Accent)
            }
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
    QuietListRow(
        title = preset.name.previewPromptText(72),
        description = preset.summaryText(),
        icon = Icons.Filled.AutoAwesome,
        onClick = onClick,
        trailing = {
            WorkbenchIconButton(
                icon = Icons.Filled.Delete,
                label = "删除提示词 ${preset.name}",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
