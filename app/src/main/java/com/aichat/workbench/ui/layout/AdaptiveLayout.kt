package com.aichat.workbench.ui.layout

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adaptive layout system for responsive design.
 * Part of Phase 5: Responsive Layout & Internationalization
 *
 * Supports:
 * - Compact (< 600dp): Phone, single column
 * - Medium (600-840dp): Tablet, dual column
 * - Expanded (> 840dp): Large tablet/desktop, dual column with more space
 */

/**
 * Layout breakpoints based on Material Design WindowSizeClass
 */
object LayoutBreakpoints {
    const val COMPACT_MAX = 600 // dp
    const val MEDIUM_MAX = 840  // dp
}

/**
 * Determine if the current window size is compact (phone)
 */
fun WindowWidthSizeClass.isCompact(): Boolean {
    return this == WindowWidthSizeClass.Compact
}

/**
 * Determine if the current window size is medium or larger (tablet+)
 */
fun WindowWidthSizeClass.isMediumOrLarger(): Boolean {
    return this == WindowWidthSizeClass.Medium || this == WindowWidthSizeClass.Expanded
}

/**
 * Get the appropriate list/detail split ratio for the current window size
 */
fun WindowWidthSizeClass.getListDetailRatio(): Pair<Float, Float> {
    return when (this) {
        WindowWidthSizeClass.Compact -> 1f to 0f // Full screen list or detail
        WindowWidthSizeClass.Medium -> 0.3f to 0.7f // 30/70 split
        WindowWidthSizeClass.Expanded -> 0.25f to 0.75f // 25/75 split
        else -> 0.3f to 0.7f
    }
}

/**
 * Pass-through layout hook for future list-detail work.
 */
@Composable
fun AdaptiveChatLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    content()
}
