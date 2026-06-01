package com.aichat.workbench.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFFC96442),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1D7CB),
    onPrimaryContainer = Color(0xFF4C1F12),
    secondary = Color(0xFF3D8C5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7EBDD),
    onSecondaryContainer = Color(0xFF173B25),
    tertiary = Color(0xFFC4873C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4DFC0),
    onTertiaryContainer = Color(0xFF4A2B08),
    error = Color(0xFFC43C3C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D5D5),
    onErrorContainer = Color(0xFF561313),
    background = Color(0xFFF5F4ED),
    onBackground = Color(0xFF141413),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFEEECE2),
    onSurfaceVariant = Color(0xFF5E5D59),
    outline = Color(0xFFD4D0C3),
    outlineVariant = Color(0xFFE3E0D5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4735A),
    onPrimary = Color(0xFF1B0E09),
    primaryContainer = Color(0xFF3A2019),
    onPrimaryContainer = Color(0xFFF2C9BD),
    secondary = Color(0xFF69B583),
    onSecondary = Color(0xFF082113),
    secondaryContainer = Color(0xFF183A24),
    onSecondaryContainer = Color(0xFFD9F0DF),
    tertiary = Color(0xFFD5A15A),
    onTertiary = Color(0xFF241505),
    tertiaryContainer = Color(0xFF3E2B12),
    onTertiaryContainer = Color(0xFFF2D5A9),
    error = Color(0xFFE06F6F),
    onError = Color(0xFF2B0707),
    errorContainer = Color(0xFF4B1717),
    onErrorContainer = Color(0xFFFFD5D5),
    background = Color(0xFF08090A),
    onBackground = Color(0xFFF0F0F2),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFF0F0F2),
    surfaceVariant = Color(0xFF0D0D0F),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

@Composable
fun AiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        shapes = AppShapes,
        content = content,
    )
}
