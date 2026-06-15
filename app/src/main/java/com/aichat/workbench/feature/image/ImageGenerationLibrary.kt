package com.aichat.workbench.feature.image

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImageLibraryHeader(
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
internal fun ImageGenerationRow(
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
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = generation.prompt.preview(120),
                    style = MaterialTheme.typography.bodyMedium,
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
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    OutlinedButton(
                        onClick = onReusePrompt,
                        shape = MaterialTheme.shapes.medium,
                    ) {
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
    status == ImageGenerationStatus.Completed || status == ImageGenerationStatus.Failed || status == ImageGenerationStatus.Cancelled

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
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            } finally {
                isLoading = false
            }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    if (bitmap == null) {
        MissingThumbnail()
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Image(
            bitmap = bitmap!!,
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
