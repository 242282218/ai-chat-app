package com.aichat.workbench.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFFB45309),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE4E7EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB6FF),
    secondary = Color(0xFF6ED2C4),
    tertiary = Color(0xFFFFC477),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF2B3138),
)

@Composable
fun AiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) {
            DarkColors
        } else {
            LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
