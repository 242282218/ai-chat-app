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
    primary = Color(0xFF2E6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEEDC),
    onPrimaryContainer = Color(0xFF062019),
    secondary = Color(0xFF5E7068),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7DF),
    onSecondaryContainer = Color(0xFF17211C),
    tertiary = Color(0xFF8A6A2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5DFA8),
    onTertiaryContainer = Color(0xFF2D2000),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8F3),
    onBackground = Color(0xFF151D18),
    surface = Color(0xFFFCFCF7),
    onSurface = Color(0xFF151D18),
    surfaceVariant = Color(0xFFE7ECE3),
    onSurfaceVariant = Color(0xFF5F6A62),
    outline = Color(0xFFC9D1C8),
    outlineVariant = Color(0xFFDDE4DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED8BE),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF14513F),
    onPrimaryContainer = Color(0xFFBDEEDC),
    secondary = Color(0xFFBBCBC1),
    onSecondary = Color(0xFF26332D),
    secondaryContainer = Color(0xFF3F4C45),
    onSecondaryContainer = Color(0xFFD7E7DD),
    tertiary = Color(0xFFE0C27C),
    onTertiary = Color(0xFF3B2F00),
    tertiaryContainer = Color(0xFF554600),
    onTertiaryContainer = Color(0xFFFDDFA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE1E7E0),
    surface = Color(0xFF171C18),
    onSurface = Color(0xFFE1E7E0),
    surfaceVariant = Color(0xFF3F4942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF3F4942),
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
        shapes = AppShapes,
        content = content,
    )
}
