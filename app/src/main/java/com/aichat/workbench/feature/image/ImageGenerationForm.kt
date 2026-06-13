package com.aichat.workbench.feature.image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun ImageGenerationForm(
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
            colors = workbenchTextFieldColors(),
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
            if (state.availableImageModels().isEmpty()) {
                OutlinedTextField(
                    value = state.model,
                    onValueChange = viewModel::updateModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "模型") },
                    placeholder = { Text(text = "输入图片模型名称") },
                    singleLine = true,
                    colors = workbenchTextFieldColors(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "尺寸", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageSizePresets, key = { it }) { preset ->
                        FilterChip(
                            selected = state.size == preset,
                            onClick = { viewModel.updateSize(preset) },
                            label = { Text(text = preset) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.size,
                    onValueChange = viewModel::updateSize,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "自定义尺寸，如 1024x1024") },
                    singleLine = true,
                    colors = workbenchTextFieldColors(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "质量", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageQualityPresets, key = { it }) { preset ->
                        FilterChip(
                            selected = state.quality == preset,
                            onClick = { viewModel.updateQuality(preset) },
                            label = { Text(text = preset) },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "数量", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageCountPresets, key = { it }) { preset ->
                        FilterChip(
                            selected = state.count == preset,
                            onClick = { viewModel.updateCount(preset) },
                            label = { Text(text = "${preset}张") },
                        )
                    }
                }
            }
        }
        if (state.selectedModelUnsupported) {
            InlineNotice(
                text = "所选模型未声明支持图片生成，可能导致请求失败。请切换支持图片的模型或手动测试连接。",
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
                text = imageGenerationErrorNoticeText(it),
                icon = Icons.Filled.Image,
                tone = StatusTone.Critical,
            )
        }
    }
}

@Composable
private fun ImageGenerationReadiness(state: ImageGenerationUiState) {
    val readiness = state.imageGenerationReadiness()
    Text(
        text = readiness.description,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
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
                FilterChip(
                    selected = state.selectedProviderId == provider.id.value,
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





private val imageSizePresets = listOf("1024x1024", "1024x1792", "1792x1024")
private val imageQualityPresets = listOf("auto", "low", "medium", "high")
private val imageCountPresets = listOf("1", "2", "3", "4")

internal fun imageGenerationErrorNoticeText(error: String): String {
    val summary = error.trim()
    if (summary.startsWith("图片生成失败")) return summary
    return "图片生成失败：$summary"
}
