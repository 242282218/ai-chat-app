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
    primary = Color(0xFF2F6F5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EFE5),
    onPrimaryContainer = Color(0xFF08231B),
    secondary = Color(0xFF3E5F8A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF0B1C33),
    tertiary = Color(0xFFA85F35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC7),
    onTertiaryContainer = Color(0xFF361301),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F5EE),
    onBackground = Color(0xFF151D18),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF151D18),
    surfaceVariant = Color(0xFFECE6DA),
    onSurfaceVariant = Color(0xFF665F55),
    outline = Color(0xFFCFC5B7),
    outlineVariant = Color(0xFFE5DCCF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3DCC8),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF15513F),
    onPrimaryContainer = Color(0xFFD6EFE5),
    secondary = Color(0xFFAFC7EC),
    onSecondary = Color(0xFF09213D),
    secondaryContainer = Color(0xFF27476F),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF5A2105),
    tertiaryContainer = Color(0xFF7A3A16),
    onTertiaryContainer = Color(0xFFFFDCC7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14120F),
    onBackground = Color(0xFFEAE5DB),
    surface = Color(0xFF1B1814),
    onSurface = Color(0xFFEAE5DB),
    surfaceVariant = Color(0xFF4A443B),
    onSurfaceVariant = Color(0xFFD0C6B8),
    outline = Color(0xFF998F82),
    outlineVariant = Color(0xFF4A443B),
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
