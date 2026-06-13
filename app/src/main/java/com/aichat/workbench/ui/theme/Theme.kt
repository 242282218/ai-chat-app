package com.aichat.workbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Accent: Forest Green / Sage ---
// Distinctive from default blue-purple; conveys calm intelligence.
val Accent = Color(0xFF1B6B4A)
val AccentLight = Color(0xFF4ADE80)
val AccentMint = Color(0xFF7FDAAE)
val AccentContainer = Color(0xFFDCF5E7)
val AccentContainerDark = Color(0xFF005235)
val OnAccentContainer = Color(0xFF0A3D28)
val OnAccentContainerDark = Color(0xFFA0F5C5)

// --- Semantic status ---
val SemanticSuccess = Color(0xFF16A34A)
val SemanticWarning = Color(0xFFD97706)
val SemanticError = Color(0xFFDC2626)
val SemanticSuccessDark = Color(0xFF4ADE80)
val SemanticWarningDark = Color(0xFFFBBF24)
val SemanticErrorDark = Color(0xFFF87171)

// --- Light palette ---
private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDE9F5),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE8EBE5),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFF0F2ED),
    surfaceContainerLow = Color(0xFFF4F6F1),
    surfaceContainerHigh = Color(0xFFE8EBE5),
    outline = Color(0xFFC1C9BE),
    outlineVariant = Color(0xFFE0E5DA),
    error = SemanticError,
    errorContainer = Color(0xFFFFEDEA),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF3B0A0A),
    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFEFF1EC),
    inversePrimary = AccentMint,
    surfaceTint = Accent,
)

// --- Dark palette ---
private val DarkColors = darkColorScheme(
    primary = AccentMint,
    onPrimary = Color(0xFF003823),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = OnAccentContainerDark,
    secondary = Color(0xFFB6CCB8),
    onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3B),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFA3CDD9),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF1F4D57),
    onTertiaryContainer = Color(0xFFBDE9F5),
    background = Color(0xFF0E110F),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF141815),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF272C27),
    onSurfaceVariant = Color(0xFFBFC9B8),
    surfaceContainer = Color(0xFF1A1E1B),
    surfaceContainerLow = Color(0xFF161A17),
    surfaceContainerHigh = Color(0xFF222622),
    outline = Color(0xFF697365),
    outlineVariant = Color(0xFF5E6B5E),
    error = SemanticErrorDark,
    errorContainer = Color(0xFF4A1515),
    onError = Color(0xFF2D0606),
    onErrorContainer = Color(0xFFFFD4D0),
    inverseSurface = Color(0xFFE1E3DE),
    inverseOnSurface = Color(0xFF2E312E),
    inversePrimary = Accent,
    surfaceTint = AccentMint,
)

// --- Typography ---
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.sp,
    ),
)

// --- Shapes ---
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
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
