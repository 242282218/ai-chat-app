package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.ui.component.StatusTone

internal data class ImageReadiness(
    val label: String,
    val tone: StatusTone,
    val description: String,
)

internal fun ImageGenerationUiState.canGenerateImages(): Boolean =
    imageGenerationReadiness().tone == StatusTone.Success

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

private fun ImageGenerationUiState.selectedProviderMissingApiKey(): Boolean {
    val provider = selectedProvider ?: return false
    return provider.requiresApiKey() && provider.apiKeyRef == null
}
