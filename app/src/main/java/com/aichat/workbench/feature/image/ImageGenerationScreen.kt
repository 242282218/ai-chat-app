package com.aichat.workbench.feature.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
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
                title = { Text(text = "图片") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ImageGenerationForm(
                    state = state,
                    onOpenProviders = onOpenProviders,
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
                        onRegenerate = { viewModel.regenerate(generation.prompt) },
                        onSave = { generation.originalPath?.let { saveImage(context, generation.id.value, it) } },
                        onShare = { generation.originalPath?.let { shareImage(context, it) } },
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
    controlsExpanded: Boolean,
    onToggleControls: () -> Unit,
    viewModel: ImageGenerationViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietSectionHeader(
            title = "图像创作台",
            description = "先描述画面，细节参数按需展开。",
            trailing = {
                if (state.isGenerating) {
                    StatusPill(text = "生成中", tone = StatusTone.Accent)
                }
            },
        )
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
        state.error?.let {
            InlineNotice(
                text = it,
                icon = Icons.Filled.Image,
                tone = StatusTone.Critical,
            )
        }
    }
}

@Composable
private fun ImageGenerationReadiness(state: ImageGenerationUiState) {
    val readiness = imageGenerationReadiness(state)
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

private data class ImageReadiness(
    val label: String,
    val tone: StatusTone,
    val description: String,
)

private fun imageGenerationReadiness(state: ImageGenerationUiState): ImageReadiness {
    val imageCount = state.imageCountOrNull()
    return when {
        state.isGenerating -> ImageReadiness(
            label = "生成中",
            tone = StatusTone.Accent,
            description = "模型服务正在生成图片，可停止；已创建的记录会标记为已取消。",
        )
        state.selectedProvider == null -> ImageReadiness(
            label = "需要模型服务",
            tone = StatusTone.Warning,
            description = "先配置支持图片生成的模型服务，再发起请求。",
        )
        state.prompt.isBlank() -> ImageReadiness(
            label = "需要提示词",
            tone = StatusTone.Warning,
            description = "描述主体、风格和约束后再生成；失败后输入会保留。",
        )
        state.model.isBlank() -> ImageReadiness(
            label = "需要模型",
            tone = StatusTone.Warning,
            description = "展开生成参数，填写或选择图片生成模型。",
        )
        imageCount == null || imageCount !in 1..4 -> ImageReadiness(
            label = "数量无效",
            tone = StatusTone.Critical,
            description = "展开生成参数，将数量设为 1 到 4。",
        )
        state.selectedModelUnsupported -> ImageReadiness(
            label = "模型不支持",
            tone = StatusTone.Critical,
            description = "当前模型未声明支持图片生成，请切换模型或模型服务。",
        )
        else -> ImageReadiness(
            label = "就绪",
            tone = StatusTone.Success,
            description = "生成后会写入本地作品库，可复用提示词、保存或分享。",
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
                        text = "$it\n提示词已保留，可复用后修改并重试。",
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
                        enabled = generation.status == ImageGenerationStatus.Completed,
                        tint = MaterialTheme.colorScheme.primary,
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

private fun ImageGenerationUiState.canGenerateImages(): Boolean {
    val imageCount = imageCountOrNull()
    return !isGenerating &&
        selectedProvider != null &&
        prompt.isNotBlank() &&
        model.isNotBlank() &&
        imageCount != null &&
        imageCount in 1..4 &&
        !selectedModelUnsupported
}

private fun ImageGenerationUiState.imageLibrarySummaryLabel(): String {
    if (generations.isEmpty()) return "暂无作品"
    val completedCount = generations.count { it.status == ImageGenerationStatus.Completed }
    return "${generations.size} 个作品 · $completedCount 个完成"
}

private fun ImageGenerationUiState.imageCountOrNull(): Int? =
    count.trim().toIntOrNull()

private fun ImageGenerationUiState.imageCountLabel(): String {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> "需要数量"
        parsedCount == null -> "数量无效"
        parsedCount in 1..4 -> "${parsedCount} 张图片"
        else -> "数量 1-4"
    }
}

private fun ImageGenerationUiState.imageCountTone(): StatusTone {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> StatusTone.Warning
        parsedCount != null && parsedCount in 1..4 -> StatusTone.Success
        else -> StatusTone.Critical
    }
}

private fun ImageGenerationUiState.imageModelLabel(): String =
    when {
        model.isBlank() -> "需要模型"
        selectedModelUnsupported -> "模型不支持"
        else -> "模型就绪"
    }

private fun ImageGenerationUiState.imageModelTone(): StatusTone =
    when {
        model.isBlank() -> StatusTone.Warning
        selectedModelUnsupported -> StatusTone.Critical
        else -> StatusTone.Success
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

private fun saveImage(context: Context, id: String, path: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        shareImage(context, path)
        return
    }
    val file = File(path)
    if (!file.exists()) return
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$id.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Chat")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return
    context.contentResolver.openOutputStream(uri)?.use { output ->
        file.inputStream().use { input -> input.copyTo(output) }
    }
}

private fun shareImage(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "分享图片"))
}

private fun String.preview(maxLength: Int): String {
    val normalized = trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 3)}..."
    }
}
