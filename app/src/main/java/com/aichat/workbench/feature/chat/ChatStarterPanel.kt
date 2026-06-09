package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchPanel

@Composable
internal fun EmptyConversationPanel(
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
) {
    WorkbenchPanel(
        title = if (hasEnabledProvider) "开始新的会话" else "先连接模型",
        description = if (hasEnabledProvider) {
            "直接输入消息，也可以添加图片一起发送。"
        } else {
            "添加模型连接后，请求会从本机直接发送到你的接口地址。"
        },
        icon = Icons.AutoMirrored.Filled.Chat,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        trailing = {
            StatusPill(
                text = if (hasEnabledProvider) "可发送" else "待配置",
                tone = if (hasEnabledProvider) StatusTone.Success else StatusTone.Warning,
            )
        },
    ) {
        if (!hasEnabledProvider) {
            Button(
                onClick = onOpenProviders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "配置模型连接")
            }
        } else {
            QuickCapabilityRow()
        }
    }
}

@Composable
private fun QuickCapabilityRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(text = "文字聊天", tone = StatusTone.Success)
        }
        item {
            StatusPill(text = "图片输入", tone = StatusTone.Warning)
        }
        item {
            StatusPill(text = "图片生成", tone = StatusTone.Accent)
        }
    }
}
