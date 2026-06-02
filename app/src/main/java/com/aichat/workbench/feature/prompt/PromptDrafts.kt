package com.aichat.workbench.feature.prompt

import com.aichat.workbench.domain.model.PromptPreset

internal data class PromptActionStatus(
    val label: String,
    val isReady: Boolean,
)

internal fun promptSaveStatus(
    name: String,
    systemPrompt: String,
): PromptActionStatus =
    when {
        name.isBlank() -> PromptActionStatus("需要名称", isReady = false)
        systemPrompt.isBlank() -> PromptActionStatus("需要系统指令", isReady = false)
        else -> PromptActionStatus("可保存", isReady = true)
    }

internal fun parsePromptToolNames(value: String): List<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

internal fun promptDefaultsLabel(defaultModel: String, defaultTools: String): String {
    val hasModel = defaultModel.isNotBlank()
    val toolCount = parsePromptToolNames(defaultTools).size
    return when {
        hasModel && toolCount > 0 -> "模型 + 工具"
        hasModel -> "模型"
        toolCount > 0 -> "$toolCount 个工具"
        else -> "未绑定"
    }
}

internal fun PromptPreset.summaryText(): String {
    val descriptionText = if (description.isNullOrBlank()) "无描述" else "已描述"
    val modelText = defaultModel?.takeIf { it.isNotBlank() } ?: "无默认模型"
    val toolsText = when (val toolCount = defaultToolNames.size) {
        0 -> "无默认工具"
        else -> "${toolCount} 个工具"
    }
    return listOf(descriptionText, modelText, toolsText).joinToString(" · ")
}

internal fun List<PromptPreset>.filterByQuery(query: String): List<PromptPreset> {
    val needle = query.trim().lowercase()
    if (needle.isBlank()) return this
    return filter { preset ->
        listOf(
            preset.name,
            preset.description.orEmpty(),
            preset.systemPrompt,
            preset.defaultModel.orEmpty(),
            preset.defaultToolNames.joinToString(" "),
        ).any { value -> value.lowercase().contains(needle) }
    }
}

internal fun String.previewPromptText(maxLength: Int): String {
    val normalized = trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 3)}..."
    }
}
