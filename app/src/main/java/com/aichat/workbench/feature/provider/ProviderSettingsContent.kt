package com.aichat.workbench.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.provider.defaultImageModel
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.ui.brand.WorkbenchArtworkKind
import com.aichat.workbench.ui.brand.WorkbenchBrandArtwork
import com.aichat.workbench.ui.component.EmptyStatePanel
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton

@Composable
internal fun EmptyProviderState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyStatePanel(
        icon = Icons.Filled.Tune,
        title = "添加模型连接",
        description = "支持 OpenAI 和兼容接口，请求会从本机发送到你的接口。",
        actionLabel = "添加模型连接",
        actionIcon = Icons.Filled.Add,
        onAction = onCreate,
        artwork = {
            WorkbenchBrandArtwork(kind = WorkbenchArtworkKind.ProviderControls)
        },
        tone = StatusTone.Warning,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

internal fun providerTopBarSubtitle(providers: List<ProviderConfig>): String {
    val stats = providers.providerHealthStats()
    return when {
        stats.totalCount == 0 -> "需要添加模型连接"
        stats.enabledChatCount == 0 -> "${stats.totalCount} 个连接 · 没有可用聊天模型"
        else -> "${stats.enabledChatCount} 个可用 · ${stats.totalCount} 个连接"
    }
}

@Composable
internal fun ProviderHealthHeader(
    providers: List<ProviderConfig>,
    modelRolePreferences: List<ModelRolePreference>,
) {
    val stats = providers.providerHealthStats()
    val defaultChatModel = providers.defaultRoleModelLabel(modelRolePreferences, ModelRole.Chat)
    val defaultImageModel = providers.defaultRoleModelLabel(modelRolePreferences, ModelRole.Image)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuietSectionHeader(
            title = "连接状态",
            description = if (providers.isEmpty()) {
                "还没有模型连接"
            } else {
                "检查连接和模型配置"
            },
            trailing = {
                StatusPill(
                    text = if (stats.enabledChatCount > 0) "${stats.enabledChatCount} 可用" else "需要配置",
                    tone = if (stats.enabledChatCount > 0) StatusTone.Success else StatusTone.Warning,
                )
            },
        )
        if (providers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    StatusPill(text = "${stats.totalCount} 个连接", tone = StatusTone.Neutral)
                }
                item {
                    StatusPill(
                        text = "对话: $defaultChatModel",
                        tone = if (defaultChatModel == "未设置") StatusTone.Warning else StatusTone.Success,
                    )
                }
                item {
                    StatusPill(
                        text = "图片: $defaultImageModel",
                        tone = if (defaultImageModel == "未设置") StatusTone.Warning else StatusTone.Accent,
                    )
                }
                if (stats.imageCapableCount > 0) {
                    item {
                        StatusPill(text = "${stats.imageCapableCount} 图片连接", tone = StatusTone.Accent)
                    }
                }
                if (stats.httpCount > 0) {
                    item {
                        StatusPill(text = "${stats.httpCount} HTTP", tone = StatusTone.Warning)
                    }
                }
            }
        }
        MetadataRow(
            label = "隐私",
            value = "请求从本机直接发送到配置的接口地址；API Key 不进入备份。",
        )
        if (stats.httpCount > 0 || stats.customHeaderCount > 0) {
            InlineNotice(
                text = "HTTP 接口或自定义请求头可能改变请求安全边界。建议使用 HTTPS 并仅在受信网络下使用 HTTP。",
                icon = Icons.Filled.Lock,
                tone = StatusTone.Warning,
            )
        }
    }
}

private fun List<ProviderConfig>.defaultRoleModelLabel(
    preferences: List<ModelRolePreference>,
    role: ModelRole,
): String {
    val provider = when (role) {
        ModelRole.Chat -> firstOrNull { it.enabled }
        ModelRole.Image -> firstOrNull { it.enabled && it.supportsImageGeneration() }
    } ?: return "未设置"
    return provider.roleModel(preferences, role)
        ?: when (role) {
            ModelRole.Chat -> provider.defaultModel
            ModelRole.Image -> provider.defaultImageModel()
        }
        ?.ifBlank { null }
        ?: "未设置"
}

@Composable
internal fun ProviderRow(
    provider: ProviderConfig,
    modelRolePreferences: List<ModelRolePreference>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    QuietListRow(
        title = provider.name,
        description = provider.connectionSummary(modelRolePreferences),
        icon = if (provider.enabled) Icons.Filled.CheckCircle else Icons.Filled.Lock,
        onClick = onClick,
        onClickLabel = "编辑模型连接 ${provider.name}",
        enabled = true,
        contentEnabled = provider.enabled,
        trailing = {
            StatusPill(
                text = if (provider.enabled) "启用" else "禁用",
                tone = if (provider.enabled) StatusTone.Success else StatusTone.Neutral,
            )
            WorkbenchIconButton(
                icon = Icons.Filled.Delete,
                label = "删除模型连接 ${provider.name}",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.72f),
            )
        },
    )
}

private fun ProviderConfig.connectionSummary(modelRolePreferences: List<ModelRolePreference>): String {
    val roleSummary = listOfNotNull(
        roleModel(modelRolePreferences, ModelRole.Chat)?.let { "对话 $it" },
        roleModel(modelRolePreferences, ModelRole.Image)?.let { "图片 $it" },
    )
    return if (roleSummary.isEmpty()) {
        connectionSummary()
    } else {
        "${connectionSummary()} · ${roleSummary.joinToString(" · ")}"
    }
}

private fun ProviderConfig.roleModel(
    preferences: List<ModelRolePreference>,
    role: ModelRole,
): String? =
    rolePreferenceModel(preferences, role)
