package com.aichat.workbench.feature.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.ui.component.decodeInlineImageBitmap
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import java.io.File

@Composable
internal fun InputBar(
    input: String,
    imageDrafts: List<MessagePart.Image>,
    isGenerating: Boolean,
    isEditing: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onInputChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputStatusRow(
                isGenerating = isGenerating,
                isEditing = isEditing,
                hasImageDrafts = imageDrafts.isNotEmpty(),
                canSend = canSend,
                onOpenProviders = onOpenProviders,
                onCancelEdit = onCancelEdit,
            )
            if (imageDrafts.isNotEmpty()) {
                ImageDraftRow(
                    images = imageDrafts,
                    onRemoveImage = onRemoveImage,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                WorkbenchIconButton(
                    icon = Icons.Filled.Image,
                    label = "添加图片",
                    onClick = onPickImage,
                    enabled = !isGenerating,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = if (isEditing) "编辑消息" else "消息") },
                    placeholder = { Text(text = if (isEditing) "修改消息" else "输入消息") },
                    minLines = 1,
                    maxLines = 5,
                    enabled = !isGenerating,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = if (input.contains('\n')) ImeAction.Default else ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && canSubmitMessage(input, canSend, imageDrafts)) {
                                onSend()
                            }
                        },
                    ),
                )
                FilledIconButton(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || canSubmitMessage(input, canSend, imageDrafts),
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "停止" else "发送",
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatImagePreview(
    image: MessagePart.Image,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(image.uri) { decodeInlineImageBitmap(image.uri) }
    if (bitmap == null) {
        Box(
            modifier = modifier.clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = "已选择图片",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small),
    )
}

internal fun String.toLocalImagePathOrNull(): String? {
    val normalized = trim()
    val path = when {
        normalized.startsWith("file://") -> Uri.parse(normalized).path
        normalized.startsWith("data:image") -> null
        normalized.startsWith("http://") || normalized.startsWith("https://") -> null
        else -> normalized
    } ?: return null
    return path.takeIf { File(it).isFile }
}

internal fun String.fileStem(): String =
    File(this).nameWithoutExtension.ifBlank { "generated-image" }

@Composable
private fun ImageDraftRow(
    images: List<MessagePart.Image>,
    onRemoveImage: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images.size, key = { index -> "${images[index].uri.take(32)}-$index" }) { index ->
            Box {
                ChatImagePreview(
                    image = images[index],
                    modifier = Modifier.size(72.dp),
                )
                WorkbenchIconButton(
                    icon = Icons.Filled.Close,
                    label = "移除图片",
                    onClick = { onRemoveImage(index) },
                    modifier = Modifier.align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputStatusRow(
    isGenerating: Boolean,
    isEditing: Boolean,
    hasImageDrafts: Boolean,
    canSend: Boolean,
    onOpenProviders: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val status = inputStatus(
        isGenerating = isGenerating,
        isEditing = isEditing,
        hasImageDrafts = hasImageDrafts,
        canSend = canSend,
    ) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodySmall,
            color = status.tone.contentColor(),
        )
        Spacer(modifier = Modifier.weight(1f))
        when {
            isEditing -> {
                TextButton(onClick = onCancelEdit) {
                    Text(text = "取消")
                }
            }
            !canSend && !isGenerating -> {
                TextButton(onClick = onOpenProviders) {
                    Text(text = "配置模型连接")
                }
            }
        }
    }
}

internal data class InputStatus(
    val label: String,
    val tone: StatusTone,
)

internal fun inputStatus(
    isGenerating: Boolean,
    isEditing: Boolean,
    hasImageDrafts: Boolean,
    canSend: Boolean,
): InputStatus? =
    when {
        isGenerating -> InputStatus(
            label = "生成中",
            tone = StatusTone.Accent,
        )
        isEditing -> InputStatus(
            label = "编辑中",
            tone = StatusTone.Warning,
        )
        hasImageDrafts && canSend -> InputStatus(
            label = "图片将作为多模态内容发送给模型",
            tone = StatusTone.Warning,
        )
        !canSend -> InputStatus(
            label = "需要模型连接",
            tone = StatusTone.Critical,
        )
        else -> null
    }
