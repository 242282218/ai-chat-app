package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ModelParameters

internal enum class ModelParameterDraftKind {
    Temperature,
    TopP,
    MaxTokens,
}

internal data class ModelParameterDraftStatus(
    val label: String,
    val isValid: Boolean,
)

internal fun ChatUiState.hasValidModelParameterDrafts(): Boolean =
    modelParameterDraftStatus(temperatureDraft, ModelParameterDraftKind.Temperature).isValid &&
        modelParameterDraftStatus(topPDraft, ModelParameterDraftKind.TopP).isValid &&
        modelParameterDraftStatus(maxTokensDraft, ModelParameterDraftKind.MaxTokens).isValid

internal fun ChatUiState.validatedModelParameters(): ModelParameters {
    val temperature = temperatureDraft.toNullableDouble("temperature")
    val topP = topPDraft.toNullableDouble("top_p")
    val maxTokens = maxTokensDraft.toNullableInt("max_tokens")

    require(temperature == null || temperature in 0.0..2.0) {
        "temperature 必须在 0 到 2 之间。"
    }
    require(topP == null || topP in 0.0..1.0) {
        "top_p 必须在 0 到 1 之间。"
    }
    require(maxTokens == null || maxTokens > 0) {
        "max_tokens 必须大于 0。"
    }
    return ModelParameters(
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
    )
}

internal fun modelParameterDraftStatus(
    value: String,
    kind: ModelParameterDraftKind,
): ModelParameterDraftStatus {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return ModelParameterDraftStatus(kind.emptyLabel, isValid = true)
    }

    return when (kind) {
        ModelParameterDraftKind.Temperature -> decimalStatus(
            label = "Temp",
            value = trimmed,
            validRange = 0.0..2.0,
            rangeLabel = "Temp 0-2",
        )
        ModelParameterDraftKind.TopP -> decimalStatus(
            label = "Top P",
            value = trimmed,
            validRange = 0.0..1.0,
            rangeLabel = "Top P 0-1",
        )
        ModelParameterDraftKind.MaxTokens -> intStatus(
            label = "Max",
            value = trimmed,
        )
    }
}

private val ModelParameterDraftKind.emptyLabel: String
    get() = when (this) {
        ModelParameterDraftKind.Temperature -> "Temp -"
        ModelParameterDraftKind.TopP -> "Top P -"
        ModelParameterDraftKind.MaxTokens -> "Max -"
    }

private fun decimalStatus(
    label: String,
    value: String,
    validRange: ClosedFloatingPointRange<Double>,
    rangeLabel: String,
): ModelParameterDraftStatus {
    val number = value.toDoubleOrNull()
        ?: return ModelParameterDraftStatus("$label 需为数字", isValid = false)
    if (number !in validRange) {
        return ModelParameterDraftStatus(rangeLabel, isValid = false)
    }
    return ModelParameterDraftStatus("$label $value", isValid = true)
}

private fun intStatus(
    label: String,
    value: String,
): ModelParameterDraftStatus {
    val number = value.toIntOrNull()
        ?: return ModelParameterDraftStatus("$label 需为整数", isValid = false)
    if (number <= 0) {
        return ModelParameterDraftStatus("$label > 0", isValid = false)
    }
    return ModelParameterDraftStatus("$label $value", isValid = true)
}

private fun String.toNullableDouble(name: String): Double? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    return trimmed.toDoubleOrNull() ?: error("$name 必须是数字。")
}

private fun String.toNullableInt(name: String): Int? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    return trimmed.toIntOrNull() ?: error("$name 必须是整数。")
}
