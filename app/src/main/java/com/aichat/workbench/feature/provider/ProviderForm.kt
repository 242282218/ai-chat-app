package com.aichat.workbench.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun ProviderForm(
    editing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    type: ProviderType,
    providerTypes: List<ProviderType>,
    onTypeChange: (ProviderType) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    imageModel: String,
    onImageModelChange: (String) -> Unit,
    models: List<ModelConfig>,
    onSelectModel: (String) -> Unit,
    onSelectImageModel: (String) -> Unit,
    apiKey: String,
    hasStoredKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit,
    formKey: String,
    message: String?,
    canSave: Boolean,
    canTest: Boolean,
    onSave: () -> Unit,
    onRefreshModels: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showApiKey by remember { mutableStateOf(false) }
    val hasAdvancedDraft = headers.isNotBlank() || allowHttp
    var advancedExpanded by rememberSaveable(formKey) { mutableStateOf(hasAdvancedDraft) }

    WorkbenchPanel(
        title = if (editing) "编辑模型连接" else "新建模型连接",
        description = "使用自己的 API Key，请求直接发送到配置的接口地址。",
        icon = Icons.Filled.Tune,
        modifier = modifier,
        trailing = {
            if (!enabled) {
                StatusPill(text = "已禁用", tone = StatusTone.Neutral)
            }
        },
    ) {
        ProviderFormSummary(
            name = name,
            type = type,
            baseUrl = baseUrl,
            model = model,
            imageModel = imageModel,
            apiKey = apiKey,
            hasStoredKey = hasStoredKey,
            headers = headers,
            allowHttp = allowHttp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providerTypes, key = { it.value }) { providerType ->
                FilterChip(
                    selected = type == providerType,
                    onClick = { onTypeChange(providerType) },
                    label = { Text(text = providerType.providerTypeLabel()) },
                )
            }
        }

        MetadataRow(
            label = "服务类型",
            value = type.providerTypeLabel(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "名称 *") },
            singleLine = true,
            colors = workbenchTextFieldColors(),
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "接口地址 *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            colors = workbenchTextFieldColors(),
        )
        ProviderEndpointPreviewRows(
            preview = providerEndpointPreview(
                type = type,
                baseUrl = baseUrl,
                allowHttp = allowHttp,
            ),
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "对话模型") },
            supportingText = { Text(text = "默认聊天模型。") },
            singleLine = true,
            colors = workbenchTextFieldColors(),
        )
        ProviderModelPicker(
            models = models.availableChatModels(),
            selectedModel = model,
            canRefresh = canTest,
            onSelectModel = onSelectModel,
            onRefreshModels = onRefreshModels,
        )
        OutlinedTextField(
            value = imageModel,
            onValueChange = onImageModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "图片模型") },
            supportingText = { Text(text = "单独用于图片生成，不覆盖聊天默认模型。") },
            singleLine = true,
            colors = workbenchTextFieldColors(),
        )
        ProviderImageModelPicker(
            models = models,
            selectedImageModel = imageModel,
            onSelectImageModel = onSelectImageModel,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "API Key") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = workbenchTextFieldColors(),
            visualTransformation = if (showApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                WorkbenchIconButton(
                    icon = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (showApiKey) "隐藏 API Key" else "显示 API Key",
                    onClick = { showApiKey = !showApiKey },
                )
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已启用",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = enabled, onCheckedChange = null)
        }

        ProviderAdvancedFields(
            expanded = advancedExpanded,
            headers = headers,
            allowHttp = allowHttp,
            onToggleExpanded = { advancedExpanded = !advancedExpanded },
            onHeadersChange = onHeadersChange,
            onAllowHttpChange = onAllowHttpChange,
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
                onClick = onTest,
                enabled = canTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "测试")
            }
        }

        message?.let {
            ProviderFormFeedback(message = it)
        }
    }
}

@Composable
private fun ProviderModelPicker(
    models: List<ModelConfig>,
    selectedModel: String,
    canRefresh: Boolean,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (models.isEmpty()) "未同步模型" else "已同步 ${models.size} 个模型",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRefreshModels,
                enabled = canRefresh,
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "刷新模型")
            }
        }
        if (models.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == selectedModel.trim(),
                        onClick = { onSelectModel(item.id) },
                        label = {
                            Text(
                                text = item.displayName.ifBlank { item.id },
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (item.id == selectedModel.trim()) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderImageModelPicker(
    models: List<ModelConfig>,
    selectedImageModel: String,
    onSelectImageModel: (String) -> Unit,
) {
    val imageModels = models.filter { it.supportsImageGeneration() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (imageModels.isEmpty()) {
                "没有已识别的图片模型，可手动填写。"
            } else {
                "已识别 ${imageModels.size} 个图片模型"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (imageModels.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageModels, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == selectedImageModel.trim(),
                        onClick = { onSelectImageModel(item.id) },
                        label = {
                            Text(
                                text = item.displayName.ifBlank { item.id },
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (item.id == selectedImageModel.trim()) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderAdvancedFields(
    expanded: Boolean,
    headers: String,
    allowHttp: Boolean,
    onToggleExpanded: () -> Unit,
    onHeadersChange: (String) -> Unit,
    onAllowHttpChange: (Boolean) -> Unit,
) {
    val headerStatus = headers.headerStatus()
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
                text = if (expanded) "收起高级网络设置" else "高级网络设置",
                modifier = Modifier.weight(1f),
            )
            if (headers.isNotBlank() || allowHttp) {
                StatusPill(
                    text = advancedProviderLabel(headers, allowHttp, headerStatus),
                    tone = advancedProviderTone(headers, allowHttp, headerStatus),
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = headers,
                onValueChange = onHeadersChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                label = { Text(text = "请求头") },
                supportingText = { Text(text = providerHeaderPolicyText) },
                isError = headerStatus.tone == StatusTone.Critical,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = workbenchTextFieldColors(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = allowHttp,
                        role = Role.Checkbox,
                        onValueChange = onAllowHttpChange,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allowHttp, onCheckedChange = null)
                Text(text = "允许 HTTP")
            }
        }
    }
}

private fun advancedProviderLabel(
    headers: String,
    allowHttp: Boolean,
    headerStatus: HeaderStatus,
): String =
    when {
        headerStatus.tone == StatusTone.Critical -> headerStatus.label
        headers.isNotBlank() && allowHttp -> "有风险"
        allowHttp -> "HTTP"
        headers.isNotBlank() -> "请求头"
        else -> "默认"
    }

private fun advancedProviderTone(
    headers: String,
    allowHttp: Boolean,
    headerStatus: HeaderStatus,
): StatusTone =
    when {
        headerStatus.tone == StatusTone.Critical -> StatusTone.Critical
        allowHttp -> StatusTone.Warning
        headers.isNotBlank() -> StatusTone.Accent
        else -> StatusTone.Neutral
    }

@Composable
private fun ProviderFormSummary(
    name: String,
    type: ProviderType,
    baseUrl: String,
    model: String,
    imageModel: String,
    apiKey: String,
    hasStoredKey: Boolean,
    headers: String,
    allowHttp: Boolean,
) {
    val urlStatus = baseUrl.providerUrlStatus(allowHttp)
    val headerStatus = headers.headerStatus()
    val requiresApiKey = ProviderRegistry.builtInDescriptor(type)?.requiresApiKey ?: true
    val keyStatus = providerKeyStatus(apiKey, hasStoredKey, requiresApiKey)
    val capabilityTags = type.providerCapabilityTags()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatusPill(
                    text = if (name.isBlank()) "需要名称" else "已命名",
                    tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
                )
            }
            item {
                StatusPill(text = urlStatus.label, tone = urlStatus.tone)
            }
            if (model.isNotBlank()) {
                item {
                    StatusPill(text = "聊天 $model", tone = StatusTone.Success)
                }
            }
            if (imageModel.isNotBlank()) {
                item {
                    StatusPill(text = "图片 $imageModel", tone = StatusTone.Accent)
                }
            }
            item {
                StatusPill(
                    text = keyStatus.label,
                    tone = keyStatus.tone,
                )
            }
            if (headers.isNotBlank()) {
                item {
                    StatusPill(
                        text = headerStatus.label,
                        tone = headerStatus.tone,
                    )
                }
            }
        }

        if (capabilityTags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(capabilityTags, key = { it.label }) { tag ->
                    StatusPill(text = tag.label, tone = tag.tone)
                }
            }
        }
    }
}

@Composable
private fun ProviderEndpointPreviewRows(
    preview: ProviderEndpointPreview?,
) {
    if (preview == null) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetadataRow(
            label = "请求地址预览",
            value = preview.requestBaseUrl,
        )
        preview.modelDiscoveryBaseUrl?.let {
            MetadataRow(
                label = "模型发现地址",
                value = it,
            )
        }
        preview.imageGenerationUrl?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StatusPill(
                    text = "图片接口",
                    tone = StatusTone.Accent,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProviderFormFeedback(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
            text = providerMessageLabel(message),
            tone = providerMessageTone(message),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun providerMessageLabel(message: String): String =
    when {
        message == "已保存" -> "已保存"
        message == "测试中..." -> "测试中"
        providerMessageTone(message) == StatusTone.Critical -> "需要处理"
        else -> "连接"
    }

private fun providerMessageTone(message: String): StatusTone {
    if (message == "已保存") return StatusTone.Success
    if (message == "测试中..." || message == "刷新模型中...") return StatusTone.Accent
    if (message.contains("成功") || message.contains("可用")) return StatusTone.Success
    return StatusTone.Critical
}



