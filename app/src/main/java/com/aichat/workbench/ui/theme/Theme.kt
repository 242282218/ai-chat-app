package com.aichat.workbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Neutral0 = Color(0xFF000000)
val Neutral50 = Color(0xFF0A0A0A)
val Neutral100 = Color(0xFF111111)
val Neutral150 = Color(0xFF1A1A1A)
val Neutral200 = Color(0xFF222222)
val Neutral300 = Color(0xFF2A2A2A)
val Neutral400 = Color(0xFF383838)

val TextPrimary = Color(0xFFF8F8F8)
val TextSecondary = Color(0xFFA0A0A0)
val TextDisabled = Color(0xFF555555)

val Accent = Color(0xFF6366F1)
val AccentVariant = Color(0xFF8B5CF6)
val AccentContainer = Color(0xFF1E1B3A)

val SemanticSuccess = Color(0xFF34D399)
val SemanticWarning = Color(0xFFFBBF24)
val SemanticError = Color(0xFFF87171)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEEDFD),
    onPrimaryContainer = Color(0xFF1A1066),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceContainer = Color(0xFFEBEBED),
    outline = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFD4D4D4),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFFEEEE),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AccentContainer,
    onPrimaryContainer = AccentVariant,
    secondary = AccentVariant,
    onSecondary = Color(0xFFFFFFFF),
    background = Neutral50,
    onBackground = TextPrimary,
    surface = Neutral100,
    onSurface = TextPrimary,
    surfaceVariant = Neutral150,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Neutral150,
    surfaceContainerHigh = Neutral200,
    outline = Neutral300,
    outlineVariant = Neutral400,
    error = SemanticError,
    errorContainer = Color(0xFF3B1515),
    onError = Color(0xFFFFFFFF),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
