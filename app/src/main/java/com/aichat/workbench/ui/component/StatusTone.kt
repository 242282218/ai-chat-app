package com.aichat.workbench.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
            container = scheme.secondary.copy(alpha = 0.08f),
            content = scheme.secondary,
            border = scheme.secondary.copy(alpha = 0.12f),
        )
        StatusTone.Warning -> StatusColors(
            container = scheme.tertiary.copy(alpha = 0.10f),
            content = scheme.tertiary,
            border = scheme.tertiary.copy(alpha = 0.15f),
        )
        StatusTone.Critical -> StatusColors(
            container = scheme.error.copy(alpha = 0.08f),
            content = scheme.error,
            border = scheme.error.copy(alpha = 0.15f),
        )
    }
}
