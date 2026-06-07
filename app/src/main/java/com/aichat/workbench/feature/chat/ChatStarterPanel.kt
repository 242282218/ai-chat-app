package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchPanel

internal data class ChatStarterPrompt(
    val label: String,
    val text: String,
    val icon: ImageVector,
    val action: ChatStarterAction = ChatStarterAction.PlainText,
)

internal enum class ChatStarterAction {
    PlainText,
    WebSearch,
    ImageGeneration,
    LocalJs,
    FileRead,
    TextTransform,
    CodeDiffPreview,
}

@Composable
internal fun EmptyConversationPanel(
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
    onUseStarterPrompt: (ChatStarterPrompt) -> Unit,
) {
    WorkbenchPanel(
        title = if (hasEnabledProvider) "开始新的会话" else "先连接模型",
        description = if (hasEnabledProvider) {
            "直接输入任务，或用一个起手式快速进入搜索、图片、文件和代码场景。"
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 2.dp),
            ) {
                items(chatStarterPrompts, key = { it.label }) { prompt ->
                    AssistChip(
                        onClick = { onUseStarterPrompt(prompt) },
                        leadingIcon = {
                            Icon(
                                imageVector = prompt.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(
                                text = prompt.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCapabilityRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(text = "搜新闻", tone = StatusTone.Warning)
        }
        item {
            StatusPill(text = "生图片", tone = StatusTone.Accent)
        }
        item {
            StatusPill(text = "跑 JS", tone = StatusTone.Critical)
        }
        item {
            StatusPill(text = "读文件", tone = StatusTone.Success)
        }
    }
}

private val chatStarterPrompts = listOf(
    ChatStarterPrompt(
        "搜索新闻",
        "今天 AI 行业新闻",
        Icons.AutoMirrored.Filled.OpenInNew,
        ChatStarterAction.WebSearch,
    ),
    ChatStarterPrompt(
        "生成图片",
        "一张移动端 AI 工作台界面概念图，清晰、专业、原生应用风格",
        Icons.Filled.Image,
        ChatStarterAction.ImageGeneration,
    ),
    ChatStarterPrompt(
        "运行 JS",
        """return JSON.stringify({ ok: true, now: new Date(0).toISOString() })""",
        Icons.Filled.Timer,
        ChatStarterAction.LocalJs,
    ),
    ChatStarterPrompt(
        "读取文件",
        "选择 Markdown、JSON 或代码文件后总结重点、风险和下一步行动。",
        Icons.Filled.Archive,
        ChatStarterAction.FileRead,
    ),
    ChatStarterPrompt(
        "格式化 JSON",
        """{"name":"mobile-workbench","tools":["text_transform","code_diff_preview"]}""",
        Icons.Filled.Edit,
        ChatStarterAction.TextTransform,
    ),
    ChatStarterPrompt(
        "预览 Diff",
        """
        fun answer() = "old"
        """.trimIndent(),
        Icons.Filled.Edit,
        ChatStarterAction.CodeDiffPreview,
    ),
    ChatStarterPrompt("总结材料", "请帮我总结下面这段材料，提炼关键结论、风险和下一步行动：", Icons.Filled.AutoAwesome),
    ChatStarterPrompt("拆解方案", "请把这个目标拆成可执行步骤，并说明每一步的验证标准：", Icons.Filled.Tune),
)
