package com.aichat.workbench.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aichat.workbench.ui.theme.workbenchColors

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
    val semantic = MaterialTheme.workbenchColors
    return when (tone) {
        StatusTone.Neutral -> StatusColors(
            container = scheme.surfaceVariant.copy(alpha = 0.3f),
            content = scheme.onSurfaceVariant,
            border = scheme.outlineVariant,
        )
        StatusTone.Accent -> StatusColors(
            container = semantic.imageAccent.copy(alpha = 0.10f),
            content = semantic.imageAccent,
            border = semantic.imageAccent.copy(alpha = 0.18f),
        )
        StatusTone.Success -> StatusColors(
            container = semantic.success.copy(alpha = 0.10f),
            content = semantic.success,
            border = semantic.success.copy(alpha = 0.18f),
        )
        StatusTone.Warning -> StatusColors(
            container = semantic.warning.copy(alpha = 0.12f),
            content = semantic.warning,
            border = semantic.warning.copy(alpha = 0.22f),
        )
        StatusTone.Critical -> StatusColors(
            container = scheme.error.copy(alpha = 0.08f),
            content = scheme.error,
            border = scheme.error.copy(alpha = 0.15f),
        )
    }
}
