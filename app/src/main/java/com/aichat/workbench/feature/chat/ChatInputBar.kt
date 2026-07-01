package com.aichat.workbench.feature.chat

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.statusColors
import com.aichat.workbench.ui.component.decodeInlineImageBitmap
import com.aichat.workbench.ui.component.workbenchTextFieldColors
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
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 0.5.dp,
            )
            val status = inputStatus(
                isGenerating = isGenerating,
                isEditing = isEditing,
                hasImageDrafts = imageDrafts.isNotEmpty(),
                canSend = canSend,
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

                if (imageDrafts.isNotEmpty()) {
                    ImageDraftRow(
                        images = imageDrafts,
                        onRemoveImage = onRemoveImage,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        label = {
                            Text(text = if (isEditing) "编辑消息" else "消息")
                        },
                        placeholder = {
                            Text(
                                text = if (isEditing) "修改消息..." else "发消息...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        minLines = 1,
                        maxLines = 5,
                        enabled = !isGenerating,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = workbenchTextFieldColors(),
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
                    SendButton(
                        isGenerating = isGenerating,
                        enabled = isGenerating || canSubmitMessage(input, canSend, imageDrafts),
                        onClick = if (isGenerating) onStop else onSend,
                    )
                }
            }
        }
    }
}

@Composable
private fun SendButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
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
            contentDescription = if (isGenerating) "停止生成" else "发送消息",
            modifier = Modifier.size(20.dp),
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = status.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = status.toneColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            isEditing -> {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            !canSend && !isGenerating -> {
                TextButton(
                    onClick = onOpenProviders,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
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
    contentDescription: String = "已选择图片",
) {
    val bitmap = remember(image.uri) { decodeInlineImageBitmap(image.uri) }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "$contentDescription 加载失败",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
                    contentDescription = "待发送图片 ${index + 1}",
                )
                WorkbenchIconButton(
                    icon = Icons.Filled.Close,
                    label = "移除第 ${index + 1} 张图片",
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
