package com.aichat.workbench.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class StatusTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Critical,
}

internal data class StatusColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

@Composable
internal fun statusColors(tone: StatusTone): StatusColors {
    val scheme = MaterialTheme.colorScheme
    val success = readableSemanticColor(
        lightColor = Color(0xFF15803D),
        darkColor = Color(0xFF4ADE80),
        background = scheme.background,
    )
    val warning = readableSemanticColor(
        lightColor = Color(0xFFB45309),
        darkColor = Color(0xFFFBBF24),
        background = scheme.background,
    )
    return when (tone) {
        StatusTone.Neutral -> StatusColors(
            container = scheme.surfaceVariant.copy(alpha = 0.3f),
            content = scheme.onSurfaceVariant,
            border = scheme.outlineVariant,
        )
        StatusTone.Accent -> StatusColors(
            container = scheme.primary.copy(alpha = 0.08f),
            content = scheme.primary,
            border = scheme.primary.copy(alpha = 0.12f),
        )
        StatusTone.Success -> StatusColors(
            container = success.copy(alpha = 0.10f),
            content = success,
            border = success.copy(alpha = 0.18f),
        )
        StatusTone.Warning -> StatusColors(
            container = warning.copy(alpha = 0.12f),
            content = warning,
            border = warning.copy(alpha = 0.22f),
        )
        StatusTone.Critical -> StatusColors(
            container = scheme.error.copy(alpha = 0.08f),
            content = scheme.error,
            border = scheme.error.copy(alpha = 0.15f),
        )
    }
}

private fun readableSemanticColor(
    lightColor: Color,
    darkColor: Color,
    background: Color,
): Color = if (background.luminance() < 0.5f) darkColor else lightColor
