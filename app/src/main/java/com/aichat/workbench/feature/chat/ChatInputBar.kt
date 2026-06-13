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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import com.aichat.workbench.ui.component.statusColors
import com.aichat.workbench.ui.component.decodeInlineImageBitmap
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Status indicator
            val status = inputStatus(
                isGenerating = isGenerating,
                isEditing = isEditing,
                hasImageDrafts = imageDrafts.isNotEmpty(),
                canSend = canSend,
            )
            if (status != null) {
                InputStatusBar(
                    status = status,
                    isEditing = isEditing,
                    canSend = canSend,
                    isGenerating = isGenerating,
                    onOpenProviders = onOpenProviders,
                    onCancelEdit = onCancelEdit,
                )
            }

            // Image drafts preview
            if (imageDrafts.isNotEmpty()) {
                ImageDraftRow(
                    images = imageDrafts,
                    onRemoveImage = onRemoveImage,
                )
            }

            // Main input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                WorkbenchIconButton(
                    icon = Icons.Filled.Image,
                    label = "添加图片",
                    onClick = onPickImage,
                    enabled = !isGenerating,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isEditing) "修改消息..." else "输入消息...",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    minLines = 1,
                    maxLines = 5,
                    enabled = !isGenerating,
                    shape = MaterialTheme.shapes.medium,
                    colors = workbenchTextFieldColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    ),
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
                    modifier = Modifier.size(48.dp),
                    colors = if (isGenerating) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    },
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "停止" else "发送",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputStatusBar(
    status: InputStatus,
    isEditing: Boolean,
    canSend: Boolean,
    isGenerating: Boolean,
    onOpenProviders: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = status.toneColor(),
        )
        Spacer(modifier = Modifier.weight(1f))
        when {
            isEditing -> {
                TextButton(onClick = onCancelEdit) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            !canSend && !isGenerating -> {
                TextButton(onClick = onOpenProviders) {
                    Text(
                        text = "配置模型",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputStatus.toneColor() = statusColors(tone).content

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
                contentDescription = "图片加载失败",
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
                    modifier = Modifier.size(64.dp),
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
            label = "生成中...",
            tone = StatusTone.Accent,
        )
        isEditing -> InputStatus(
            label = "编辑消息中",
            tone = StatusTone.Warning,
        )
        hasImageDrafts && canSend -> InputStatus(
            label = "图片将作为多模态内容发送",
            tone = StatusTone.Warning,
        )
        !canSend -> InputStatus(
            label = "需要先配置模型连接",
            tone = StatusTone.Critical,
        )
        else -> null
    }
