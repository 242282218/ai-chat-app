package com.aichat.workbench.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WorkbenchSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val imageAccent: Color,
    val onImageAccent: Color,
    val imageAccentContainer: Color,
    val creativeAccent: Color,
    val privacyAccent: Color,
)

internal val LightWorkbenchSemanticColors = WorkbenchSemanticColors(
    success = Color(0xFF15803D),
    onSuccess = Color.White,
    successContainer = Color(0xFFDCFCE7),
    warning = WorkbenchBrandColors.Amber700,
    onWarning = Color.White,
    warningContainer = Color(0xFFFFF7ED),
    imageAccent = WorkbenchBrandColors.Cyan600,
    onImageAccent = Color.White,
    imageAccentContainer = Color(0xFFE0F2FE),
    creativeAccent = WorkbenchBrandColors.Violet600,
    privacyAccent = Color(0xFF475569),
)

internal val DarkWorkbenchSemanticColors = WorkbenchSemanticColors(
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF052E16),
    warning = WorkbenchBrandColors.Amber400,
    onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF451A03),
    imageAccent = WorkbenchBrandColors.Cyan300,
    onImageAccent = Color(0xFF083344),
    imageAccentContainer = Color(0xFF083344),
    creativeAccent = Color(0xFFA78BFA),
    privacyAccent = Color(0xFF94A3B8),
)

internal val LocalWorkbenchSemanticColors = staticCompositionLocalOf {
    LightWorkbenchSemanticColors
}

val MaterialTheme.workbenchColors: WorkbenchSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWorkbenchSemanticColors.current
