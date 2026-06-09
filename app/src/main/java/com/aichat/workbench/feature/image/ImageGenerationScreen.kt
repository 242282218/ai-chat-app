package com.aichat.workbench.feature.image

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onSendToChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: ImageGenerationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClearHistory by remember { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "图片生成",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        WorkbenchIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "返回",
                            onClick = onBack,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ImageGenerationForm(
                    state = state,
                    onOpenProviders = onOpenProviders,
                    onSendToChat = onSendToChat,
                    controlsExpanded = controlsExpanded,
                    onToggleControls = { controlsExpanded = !controlsExpanded },
                    viewModel = viewModel,
                )
            }
            item {
                ImageLibraryHeader(
                    state = state,
                    onClearHistory = { confirmClearHistory = true },
                )
            }
            if (state.generations.isEmpty()) {
                item {
                    EmptyImageLibraryState()
                }
            } else {
                items(state.generations, key = { it.id.value }) { generation ->
                    ImageGenerationRow(
                        generation = generation,
                        onReusePrompt = { viewModel.reusePrompt(generation.prompt) },
                        onRegenerate = { viewModel.regenerate(generation) },
                        onSave = { generation.originalPath?.let { saveGeneratedImage(context, generation.id.value, it) } },
                        onShare = { generation.originalPath?.let { shareGeneratedImage(context, it) } },
                        onSendToChat = { onSendToChat(generation.toChatReferenceDraft()) },
                    )
                }
            }
        }
    }

    if (confirmClearHistory) {
        WorkbenchConfirmDialog(
            title = "清空图片历史？",
            message = "这会删除 ${state.generations.size} 条本地图片生成记录及其文件。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

@Composable
private fun EmptyImageLibraryState(
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
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "还没有图片",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "生成后的结果会在这里按时间保存。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageLibraryHeader(
    state: ImageGenerationUiState,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedCount = state.generations.count { it.status == ImageGenerationStatus.Failed }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietSectionHeader(
            title = "作品库",
            description = "最近结果优先展示；可复用提示词、重新生成、保存或分享。",
            trailing = {
                WorkbenchIconButton(
                    icon = Icons.Filled.ClearAll,
                    label = "清空图片历史",
                    onClick = onClearHistory,
                    enabled = state.generations.isNotEmpty() && !state.isGenerating,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(text = state.imageLibrarySummaryLabel(), tone = StatusTone.Neutral)
            if (failedCount > 0) {
                StatusPill(text = "$failedCount 个失败", tone = StatusTone.Critical)
            }
            if (state.isGenerating) {
                StatusPill(text = "生成中", tone = StatusTone.Accent)
            }
        }
    }
}

@Composable
private fun ImageGenerationForm(
    state: ImageGenerationUiState,
    onOpenProviders: () -> Unit,
    onSendToChat: (String) -> Unit,
    controlsExpanded: Boolean,
    onToggleControls: () -> Unit,
    viewModel: ImageGenerationViewModel,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    WorkbenchPanel(
        title = "图像创作台",
        description = "先描述画面，模型和尺寸按需展开。",
        icon = Icons.Filled.Image,
        trailing = {
            StatusPill(
                text = state.imageGenerationReadiness().label,
                tone = state.imageGenerationReadiness().tone,
            )
        },
    ) {
        if (state.providers.none { it.enabled }) {
            InlineNotice(
                text = "生成图片前请先配置支持图片生成的模型服务。",
                icon = Icons.Filled.Image,
                tone = StatusTone.Warning,
            ) {
                TextButton(onClick = onOpenProviders) {
                    Text(text = "配置")
                }
            }
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            label = { Text(text = "提示词 *") },
            placeholder = { Text(text = "描述主体、风格、构图和约束") },
        )
        ImageGenerationReadiness(state)
        CompactGenerationControlsSummary(
            state = state,
            expanded = controlsExpanded,
            onToggle = onToggleControls,
        )
        if (controlsExpanded) {
            ImageProviderSelector(
                state = state,
                onSelectProvider = viewModel::selectProvider,
            )
            ImageModelPicker(
                state = state,
                onSelectModel = viewModel::updateModel,
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::updateModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "模型") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.size,
                onValueChange = viewModel::updateSize,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "尺寸") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.quality,
                onValueChange = viewModel::updateQuality,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "质量") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.count,
                onValueChange = viewModel::updateCount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "数量") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        if (state.selectedModelUnsupported) {
            InlineNotice(
                text = "所选模型未声明支持图片生成。",
                icon = Icons.Filled.Image,
                tone = StatusTone.Warning,
            )
        }
        Button(
            onClick = {
                if (state.isGenerating) {
                    viewModel.stopGeneration()
                } else {
                    viewModel.generate()
                }
            },
            enabled = state.isGenerating || state.canGenerateImages(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.isGenerating) Icons.Filled.Stop else Icons.Filled.Image,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (state.isGenerating) "停止生成" else "生成图片")
        }
        OutlinedButton(
            onClick = viewModel::testConnection,
            enabled = state.selectedProvider != null && !state.isGenerating && !state.isTestingConnection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (state.isTestingConnection) "测试中" else "测试模型连接")
        }
        state.connectionTestMessage?.let { message ->
            InlineNotice(
                text = message,
                icon = Icons.Filled.Check,
                tone = when (state.connectionTestOk) {
                    true -> StatusTone.Success
                    false -> StatusTone.Critical
                    null -> StatusTone.Accent
                },
            ) {
                state.connectionTestDiagnostic?.let { diagnostic ->
                    WorkbenchIconButton(
                        icon = Icons.Filled.ContentCopy,
                        label = "复制测试诊断",
                        onClick = { clipboard.setText(AnnotatedString(diagnostic)) },
                    )
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "带入聊天",
                        onClick = { onSendToChat(diagnostic.toConnectionTestChatDraft()) },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        state.error?.let {
            InlineNotice(
                text = it,
                icon = Icons.Filled.Image,
                tone = StatusTone.Critical,
            )
        }
    }
}

internal fun String.toConnectionTestChatDraft(): String =
    """
        请根据下面的图片模型连接测试诊断，判断配置是否可用于图片生成，并给出下一步处理建议。
        只能基于诊断字段分析，不要要求我粘贴 API Key，也不要输出或推测 API Key 明文。

        ```text
        ${trim()}
        ```
    """.trimIndent()

private fun imageTopBarSubtitle(state: ImageGenerationUiState): String {
    val provider = state.selectedProvider?.name ?: "需要图片模型"
    val model = state.model.ifBlank { "未设置模型" }
    val status = if (state.isGenerating) "生成中" else state.imageLibrarySummaryLabel()
    return "$status · $provider / $model"
}

@Composable
private fun ImageGenerationReadiness(state: ImageGenerationUiState) {
    val readiness = state.imageGenerationReadiness()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            text = readiness.label,
            tone = readiness.tone,
        )
        Text(
            text = readiness.description,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactGenerationControlsSummary(
    state: ImageGenerationUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "生成参数",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = imageControlSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WorkbenchIconButton(
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                label = if (expanded) "收起图片参数" else "展开图片参数",
                onClick = onToggle,
            )
        }
        ImageParameterStatusRow(state)
    }
}

@Composable
private fun ImageParameterStatusRow(state: ImageGenerationUiState) {
    val showCountStatus = state.imageCountTone() != StatusTone.Success
    val showModelStatus = state.imageModelTone() != StatusTone.Success
    if (!showCountStatus && !showModelStatus) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCountStatus) {
            item {
                StatusPill(text = state.imageCountLabel(), tone = state.imageCountTone())
            }
        }
        if (showModelStatus) {
            item {
                StatusPill(text = state.imageModelLabel(), tone = state.imageModelTone())
            }
        }
    }
}

@Composable
private fun ImageModelPicker(
    state: ImageGenerationUiState,
    onSelectModel: (String) -> Unit,
) {
    val models = state.availableImageModels()
    if (models.isEmpty()) {
        Text(
            text = "当前连接还没有同步图片模型，可手动填写模型名。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetadataRow(
            label = "图片模型",
            value = "${state.selectedProvider?.name.orEmpty()} · ${models.size} 个",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models, key = { it.id }) { model ->
                FilterChip(
                    selected = model.id == state.model.trim(),
                    onClick = { onSelectModel(model.id) },
                    label = {
                        Text(
                            text = model.displayName.ifBlank { model.id },
                            modifier = Modifier.widthIn(max = 180.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (model.id == state.model.trim()) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageProviderSelector(
    state: ImageGenerationUiState,
    onSelectProvider: (String) -> Unit,
) {
    if (state.providers.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "模型服务",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.providers, key = { it.id.value }) { provider ->
                AssistChip(
                    onClick = { onSelectProvider(provider.id.value) },
                    label = {
                        Text(
                            text = provider.name,
                            modifier = Modifier.widthIn(max = 180.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (state.selectedProviderId == provider.id.value) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    enabled = provider.enabled,
                )
            }
        }
    }
}

private fun imageControlSummary(state: ImageGenerationUiState): String {
    val provider = state.selectedProvider?.name ?: "未选择模型服务"
    val model = state.model.ifBlank { "未设置模型" }
    val size = state.size.ifBlank { "默认尺寸" }
    val quality = state.quality.ifBlank { "默认质量" }
    val count = state.count.ifBlank { "?" }
    return "$provider · $model · $size · $quality · $count 张"
}

@Composable
private fun ImageGenerationRow(
    generation: ImageGeneration,
    onReusePrompt: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSendToChat: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                generation.thumbnailPath?.let { path ->
                    LocalThumbnail(path = path)
                } ?: MissingThumbnail()
                StatusPill(
                    text = generation.status.displayLabel(),
                    tone = generation.statusTone(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = generation.prompt.preview(120),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = generation.metadataLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                generation.errorSummary?.let {
                    Text(
                        text = "$it\n参数已保留，可复用后修改并重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            LazyRow(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    OutlinedButton(onClick = onReusePrompt) {
                        Text(text = "复用提示词")
                    }
                }
                item {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Replay,
                        label = "重新生成",
                        onClick = onRegenerate,
                        enabled = generation.canRegenerate(),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "发到聊天",
                        onClick = onSendToChat,
                    )
                }
                item {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Share,
                        label = "分享图片",
                        onClick = onShare,
                        enabled = generation.status == ImageGenerationStatus.Completed &&
                            generation.originalPath != null,
                    )
                }
                item {
                    WorkbenchIconButton(
                        icon = Icons.Filled.SaveAlt,
                        label = "保存图片",
                        onClick = onSave,
                        enabled = generation.status == ImageGenerationStatus.Completed &&
                            generation.originalPath != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingThumbnail() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "预览不可用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ImageGeneration.statusTone(): StatusTone =
    when (status) {
        ImageGenerationStatus.Completed -> StatusTone.Success
        ImageGenerationStatus.Failed -> StatusTone.Critical
        ImageGenerationStatus.Cancelled -> StatusTone.Warning
        ImageGenerationStatus.Pending -> StatusTone.Accent
    }

private fun ImageGeneration.canRegenerate(): Boolean =
    status == ImageGenerationStatus.Completed || status == ImageGenerationStatus.Failed

private fun ImageGenerationStatus.displayLabel(): String =
    when (this) {
        ImageGenerationStatus.Pending -> "等待中"
        ImageGenerationStatus.Completed -> "完成"
        ImageGenerationStatus.Failed -> "失败"
        ImageGenerationStatus.Cancelled -> "已取消"
    }

private fun ImageGeneration.metadataLabel(): String {
    val model = model.orEmpty().ifBlank { "无模型" }
    val size = size.orEmpty().ifBlank { "默认尺寸" }
    val quality = quality.orEmpty().ifBlank { "默认质量" }
    return "$model · $size · $quality"
}

private fun ImageGenerationUiState.imageLibrarySummaryLabel(): String {
    if (generations.isEmpty()) return "暂无作品"
    val completedCount = generations.count { it.status == ImageGenerationStatus.Completed }
    return "${generations.size} 个作品 · $completedCount 个完成"
}

@Composable
private fun LocalThumbnail(path: String) {
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bitmap == null) {
        Text(
            text = "缩略图不可用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "生成图片预览",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun String.preview(maxLength: Int): String {
    val normalized = trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 3)}..."
    }
}
