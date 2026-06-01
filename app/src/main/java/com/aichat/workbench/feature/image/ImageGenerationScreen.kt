package com.aichat.workbench.feature.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.io.File
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageGenerationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
                actions = {
                    WorkbenchIconButton(
                        icon = Icons.Filled.ClearAll,
                        label = "清空历史",
                        onClick = { confirmClearHistory = true },
                        enabled = state.generations.isNotEmpty() && !state.isGenerating,
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
                SectionHeader(
                    title = "Gallery",
                    description = "最近结果优先展示；可复用 Prompt、重新生成、保存或分享。",
                )
            }
            if (state.generations.isEmpty()) {
                item {
                    WorkbenchPanel(
                        title = "暂无图片历史",
                        description = "先写 Prompt 并生成图片。失败后会保留输入，便于修改后重试。",
                        icon = Icons.Filled.Image,
                    ) {
                        StatusPill(text = "本地缩略图", tone = StatusTone.Success)
                        StatusPill(text = "等待 Prompt", tone = StatusTone.Neutral)
                    }
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
private fun ImageGenerationForm(
    state: ImageGenerationUiState,
    onOpenProviders: () -> Unit,
    controlsExpanded: Boolean,
    onToggleControls: () -> Unit,
    viewModel: ImageGenerationViewModel,
) {
    WorkbenchPanel(
        title = "Image Composer",
        description = "Prompt 是主路径；Provider、Model、尺寸、质量和数量放在紧凑控制区。",
        icon = Icons.Filled.Image,
        trailing = {
            StatusPill(
                text = if (state.isGenerating) "生成中" else "就绪",
                tone = if (state.isGenerating) StatusTone.Accent else StatusTone.Success,
            )
        },
    ) {
        if (state.providers.none { it.enabled }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "没有启用的 Provider",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "生成图片前请先配置 Provider。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onOpenProviders,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "配置 Provider")
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.providers, key = { it.id.value }) { provider ->
                    AssistChip(
                        onClick = { viewModel.selectProvider(provider.id.value) },
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
        ImageFormSummary(state)

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            label = { Text(text = "Prompt") },
        )
        CompactGenerationControlsSummary(
            state = state,
            expanded = controlsExpanded,
            onToggle = onToggleControls,
        )
        if (controlsExpanded) {
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::updateModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Model") },
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
            ImageFormFeedback(
                label = "Model 支持",
                message = "所选 Model 未声明支持图片生成。",
                tone = StatusTone.Warning,
            )
        }
        ImageGenerationReadiness(state)
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
            ImageFormFeedback(
                label = "生成错误",
                message = it,
                tone = StatusTone.Critical,
            )
        }
    }
}

@Composable
private fun ImageGenerationReadiness(state: ImageGenerationUiState) {
    val readiness = imageGenerationReadiness(state)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(text = readiness.label, tone = readiness.tone)
        Text(
            text = readiness.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            description = "Provider 正在生成图片，可停止；已创建的记录会标记为已取消。",
        )
        state.selectedProvider == null -> ImageReadiness(
            label = "需要 Provider",
            tone = StatusTone.Warning,
            description = "先配置支持图片生成的 Provider，再发起请求。",
        )
        state.prompt.isBlank() -> ImageReadiness(
            label = "需要 Prompt",
            tone = StatusTone.Warning,
            description = "描述主体、风格和约束后再生成；失败后输入会保留。",
        )
        state.model.isBlank() -> ImageReadiness(
            label = "需要 Model",
            tone = StatusTone.Warning,
            description = "展开 Compact Controls，填写或选择图片生成模型。",
        )
        imageCount == null || imageCount !in 1..4 -> ImageReadiness(
            label = "数量无效",
            tone = StatusTone.Critical,
            description = "展开 Compact Controls，将数量设为 1 到 4。",
        )
        state.selectedModelUnsupported -> ImageReadiness(
            label = "Model 不支持",
            tone = StatusTone.Critical,
            description = "当前 Model 未声明支持图片生成，请切换模型或 Provider。",
        )
        else -> ImageReadiness(
            label = "就绪",
            tone = StatusTone.Success,
            description = "生成后会写入本地 Gallery，可复用 Prompt、保存或分享。",
        )
    }
}

@Composable
private fun CompactGenerationControlsSummary(
    state: ImageGenerationUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Compact Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${state.model.ifBlank { "未设置 Model" }} · ${state.size.ifBlank { "默认尺寸" }} · ${state.quality.ifBlank { "默认质量" }} · ${state.count.ifBlank { "?" }} 张",
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = state.size.ifBlank { "默认尺寸" }, tone = StatusTone.Neutral)
            }
            item {
                StatusPill(text = state.quality.ifBlank { "默认质量" }, tone = StatusTone.Neutral)
            }
            item {
                StatusPill(text = state.imageCountLabel(), tone = state.imageCountTone())
            }
            item {
                StatusPill(text = state.imageModelLabel(), tone = state.imageModelTone())
            }
        }
    }
}

@Composable
private fun ImageFormSummary(state: ImageGenerationUiState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.selectedProvider != null) "Provider 就绪" else "需要 Provider",
                tone = if (state.selectedProvider != null) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.prompt.isBlank()) "需要 Prompt" else "Prompt 就绪",
                tone = if (state.prompt.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = if (state.isGenerating) "Provider 正在生成图片" else "等待生成",
                tone = if (state.isGenerating) StatusTone.Accent else StatusTone.Neutral,
            )
        }
    }
}

@Composable
private fun ImageFormFeedback(
    label: String,
    message: String,
    tone: StatusTone,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(text = label, tone = tone)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageGenerationRow(
    generation: ImageGeneration,
    onReusePrompt: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    WorkbenchPanel(
        title = generation.prompt.preview(72),
        description = imageGenerationMetadata(generation),
        icon = Icons.Filled.Image,
        trailing = {
            StatusPill(
                text = generation.status.displayLabel(),
                tone = generation.statusTone(),
            )
        },
    ) {
        generation.thumbnailPath?.let { path ->
            LocalThumbnail(path = path)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(text = generation.model.orEmpty().ifBlank { "无 Model" }, tone = StatusTone.Neutral)
            }
            item {
                StatusPill(text = generation.size.orEmpty().ifBlank { "默认尺寸" }, tone = StatusTone.Neutral)
            }
            item {
                StatusPill(text = generation.quality.orEmpty().ifBlank { "默认质量" }, tone = StatusTone.Neutral)
            }
        }
        MetadataRow(label = "Prompt", value = generation.prompt.preview(140))
        generation.errorSummary?.let {
            Text(
                text = "$it\nPrompt 已保留，可复用后修改并重试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = onReusePrompt) {
                    Text(text = "复用 Prompt")
                }
            }
            item {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = generation.status == ImageGenerationStatus.Completed,
                ) {
                    Icon(imageVector = Icons.Filled.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "重新生成")
                }
            }
            item {
                OutlinedButton(
                    onClick = onShare,
                    enabled = generation.status == ImageGenerationStatus.Completed &&
                        generation.originalPath != null,
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "分享")
                }
            }
            item {
                OutlinedButton(
                    onClick = onSave,
                    enabled = generation.status == ImageGenerationStatus.Completed &&
                        generation.originalPath != null,
                ) {
                    Icon(imageVector = Icons.Filled.SaveAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "保存")
                }
            }
        }
    }
}

private fun imageGenerationMetadata(generation: ImageGeneration): String =
    listOfNotNull(
        generation.model,
        generation.size,
        generation.quality,
    ).joinToString(" / ").ifBlank { "无 Model 元数据" }

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
        model.isBlank() -> "需要 Model"
        selectedModelUnsupported -> "Model 不支持"
        else -> "Model 就绪"
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
