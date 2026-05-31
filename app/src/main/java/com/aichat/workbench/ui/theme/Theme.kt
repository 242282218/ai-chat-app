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
    primary = Color(0xFF2454D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF071A4C),
    secondary = Color(0xFF006A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDECE3),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF9A4F00),
    tertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF171A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFE6E8EF),
    onSurfaceVariant = Color(0xFF515763),
    outline = Color(0xFF747986),
    outlineVariant = Color(0xFFC7CBD5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C7FF),
    onPrimary = Color(0xFF08266F),
    primaryContainer = Color(0xFF183E9F),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF8BD7CC),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFA7F4E7),
    tertiary = Color(0xFFFFC98B),
    tertiaryContainer = Color(0xFF743A00),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF181B21),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF323743),
    onSurfaceVariant = Color(0xFFC7CBD5),
    outline = Color(0xFF9196A3),
    outlineVariant = Color(0xFF424854),
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
