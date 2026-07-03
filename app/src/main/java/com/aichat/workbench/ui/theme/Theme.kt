package com.aichat.workbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWorkbenchSemanticColors provides if (darkTheme) {
            DarkWorkbenchSemanticColors
        } else {
            LightWorkbenchSemanticColors
        },
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) WorkbenchDarkColorScheme else WorkbenchLightColorScheme,
            typography = WorkbenchTypography,
            shapes = WorkbenchShapes,
            content = content,
        )
    }
}
