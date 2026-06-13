package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.ui.component.StatusTone

internal data class ImageReadiness(
    val label: String,
    val tone: StatusTone,
    val description: String,
)

internal fun ImageGenerationUiState.canGenerateImages(): Boolean =
    imageGenerationReadiness().tone == StatusTone.Success

internal fun ImageGenerationUiState.availableImageModels(): List<ModelConfig> =
    selectedProvider?.models.orEmpty().filter { it.supportsImageGeneration() }

internal fun ImageGenerationUiState.imageGenerationReadiness(): ImageReadiness {
    val imageCount = imageCountOrNull()
    return when {
        isGenerating -> ImageReadiness(
            label = "生成中",
            tone = StatusTone.Accent,
            description = "模型服务正在生成图片，可停止；已创建的记录会标记为已取消。",
        )
        selectedProvider == null -> ImageReadiness(
            label = "需要模型服务",
            tone = StatusTone.Warning,
            description = "先配置支持图片生成的模型服务，再发起请求。",
        )
        selectedProviderMissingApiKey() -> ImageReadiness(
            label = "需要 API Key",
            tone = StatusTone.Warning,
            description = "当前模型服务需要已保存的 API Key；请先在模型连接中补全密钥。",
        )
        prompt.isBlank() -> ImageReadiness(
            label = "需要提示词",
            tone = StatusTone.Warning,
            description = "描述主体、风格和约束后再生成；失败后输入会保留。",
        )
        model.isBlank() -> ImageReadiness(
            label = "需要模型",
            tone = StatusTone.Warning,
            description = "展开生成参数，填写或选择图片生成模型。",
        )
        imageCount == null || imageCount !in 1..4 -> ImageReadiness(
            label = "数量无效",
            tone = StatusTone.Critical,
            description = "展开生成参数，将数量设为 1 到 4。",
        )
        selectedModelUnsupported -> ImageReadiness(
            label = "模型不支持",
            tone = StatusTone.Critical,
            description = "当前模型未声明支持图片生成，请切换模型或模型服务。",
        )
        else -> ImageReadiness(
            label = "就绪",
            tone = StatusTone.Success,
            description = "生成后会写入本地作品库，可复用提示词、保存或分享。",
        )
    }
}

internal fun ImageGenerationUiState.imageCountOrNull(): Int? =
    count.trim().toIntOrNull()

internal fun ImageGenerationUiState.imageCountLabel(): String {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> "需要数量"
        parsedCount == null -> "数量无效"
        parsedCount in 1..4 -> "${parsedCount} 张图片"
        else -> "数量 1-4"
    }
}

internal fun ImageGenerationUiState.imageCountTone(): StatusTone {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> StatusTone.Warning
        parsedCount != null && parsedCount in 1..4 -> StatusTone.Success
        else -> StatusTone.Critical
    }
}

internal fun ImageGenerationUiState.imageModelLabel(): String =
    when {
        model.isBlank() -> "需要模型"
        selectedModelUnsupported -> "模型不支持"
        else -> "模型就绪"
    }

internal fun ImageGenerationUiState.imageModelTone(): StatusTone =
    when {
        model.isBlank() -> StatusTone.Warning
        selectedModelUnsupported -> StatusTone.Critical
        else -> StatusTone.Success
    }

internal fun ImageGeneration.toChatReferenceDraft(): String =
    if (originalPath.isNullOrBlank()) {
        val requestJson = toImageGenerationRequestJson()
        """
            请分析这次图片生成记录，并准备一个可重新发起的图片生成请求。
            不要假设图片已生成；先说明失败原因、可调整参数和是否需要切换 Provider 或模型。
            如果需要重试，请优先复用下面的请求参数，并在必要时先修改 Provider、模型、数量、尺寸或质量。

            请求参数：$requestJson

            Provider：${providerId?.value.orEmpty().ifBlank { "未记录" }}
            图片提示词：${prompt.trim()}
            模型：${model.orEmpty().ifBlank { "未记录" }}
            尺寸：${size.orEmpty().ifBlank { "未记录" }}
            质量：${quality.orEmpty().ifBlank { "未记录" }}
            状态：${status.name.lowercase()}
            错误：${errorSummary.orEmpty().ifBlank { "未记录" }}
        """.trimIndent()
    } else {
        """
            请基于这张图片继续处理。不要自动上传本地文件；如需多模态分析，请先征得确认。

            Provider：${providerId?.value.orEmpty().ifBlank { "未记录" }}
            图片提示词：${prompt.trim()}
            模型：${model.orEmpty().ifBlank { "未记录" }}
            尺寸：${size.orEmpty().ifBlank { "未记录" }}
            质量：${quality.orEmpty().ifBlank { "未记录" }}
            状态：${status.name.lowercase()}
            本地图片路径：$originalPath
        """.trimIndent()
    }

internal fun String.toConnectionTestChatDraft(): String =
    """
        请根据下面的图片模型连接测试诊断，判断配置是否可用于图片生成，并给出下一步处理建议。
        只能基于诊断字段分析，不要要求我粘贴 API Key，也不要输出或推测 API Key 明文。

        ```text
        ${trim()}
        ```
    """.trimIndent()

private fun ImageGeneration.toImageGenerationRequestJson(): String =
    buildString {
        append("""{"prompt":${prompt.trim().jsonStringLiteral()}""")
        model?.trim()?.takeIf(String::isNotBlank)?.let { append(""","model":${it.jsonStringLiteral()}""") }
        size?.trim()?.takeIf(String::isNotBlank)?.let { append(""","size":${it.jsonStringLiteral()}""") }
        quality?.trim()?.takeIf(String::isNotBlank)?.let { append(""","quality":${it.jsonStringLiteral()}""") }
        append(""","count":${count.coerceIn(1, 4)}}""")
    }

private fun String.jsonStringLiteral(): String =
    buildString {
        append('"')
        this@jsonStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun ImageGenerationUiState.selectedProviderMissingApiKey(): Boolean {
    val provider = selectedProvider ?: return false
    if (!provider.requiresApiKey()) return false
    return providerApiKeyAvailable[provider.id.value] != true
}
