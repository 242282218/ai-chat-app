package com.aichat.workbench.feature.home

import com.aichat.workbench.feature.chat.toImageGenerationInstruction
import com.aichat.workbench.feature.chat.toLocalJsInstruction
import com.aichat.workbench.feature.chat.toWebSearchInstruction

internal enum class HomeTaskTemplateKind {
    WebSearch,
    LocalJs,
    ImageGeneration,
}

internal data class HomeTaskTemplate(
    val kind: HomeTaskTemplateKind,
    val title: String,
    val description: String,
    val toolLabel: String,
    val draft: String,
)

internal fun homeTaskTemplates(): List<HomeTaskTemplate> =
    listOf(
        HomeTaskTemplate(
            kind = HomeTaskTemplateKind.WebSearch,
            title = "搜索新闻",
            description = "带来源链接总结最新消息",
            toolLabel = "web_search_local",
            draft = "今天 AI 新闻".toWebSearchInstruction(),
        ),
        HomeTaskTemplate(
            kind = HomeTaskTemplateKind.LocalJs,
            title = "运行短代码",
            description = "用本地 JS 工具执行纯计算",
            toolLabel = "local_js",
            draft = DEFAULT_LOCAL_JS_SNIPPET.toLocalJsInstruction(),
        ),
        HomeTaskTemplate(
            kind = HomeTaskTemplateKind.ImageGeneration,
            title = "生成图片",
            description = "通过图片工具生成并回到会话",
            toolLabel = "image_generation",
            draft = "一张移动端 AI 任务工作台界面，清晰展示聊天、工具状态和图片结果".toImageGenerationInstruction(),
        ),
    )

private val DEFAULT_LOCAL_JS_SNIPPET: String =
    """
        const values = [3, 5, 8, 13];
        const total = values.reduce((sum, value) => sum + value, 0);
        console.log(`total=${'$'}{total}`);
    """.trimIndent()
