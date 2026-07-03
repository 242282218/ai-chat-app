package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.brand.WorkbenchArtworkKind
import com.aichat.workbench.ui.brand.WorkbenchBrandArtwork
import com.aichat.workbench.ui.component.EmptyStatePanel

@Composable
internal fun EmptyConversationPanel(
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
) {
    EmptyStatePanel(
        icon = Icons.Outlined.AutoAwesome,
        title = if (hasEnabledProvider) "开始新对话" else "连接模型",
        description = if (hasEnabledProvider) {
            "输入问题，或添加图片一起发送。"
        } else {
            "添加模型连接后，请求会从本机发送到你的接口。"
        },
        actionLabel = if (hasEnabledProvider) null else "配置模型连接",
        actionIcon = Icons.Outlined.Tune,
        onAction = if (hasEnabledProvider) null else onOpenProviders,
        artwork = {
            WorkbenchBrandArtwork(kind = WorkbenchArtworkKind.ChatSpark)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
    )
}
