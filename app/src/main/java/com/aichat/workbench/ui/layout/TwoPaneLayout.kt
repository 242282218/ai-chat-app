package com.aichat.workbench.ui.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Two-pane layout for tablet and foldable devices.
 * Part of Phase 5: Responsive Layout Implementation
 *
 * Automatically switches between:
 * - Single pane (phone): Shows list OR detail
 * - Two pane (tablet): Shows list AND detail side-by-side
 */

/**
 * Adaptive two-pane layout
 */
@Composable
fun TwoPaneLayout(
    windowSizeClass: WindowWidthSizeClass,
    showDetail: Boolean,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        // Compact: Single pane (phone)
        windowSizeClass == WindowWidthSizeClass.Compact -> {
            if (showDetail) {
                detailPane()
            } else {
                listPane()
            }
        }

        // Medium/Expanded: Two pane (tablet)
        else -> {
            Row(modifier = modifier.fillMaxSize()) {
                // List pane
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(0.35f)
                ) {
                    listPane()
                }

                // Vertical divider
                androidx.compose.material3.VerticalDivider()

                // Detail pane
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(0.65f)
                ) {
                    detailPane()
                }
            }
        }
    }
}

/**
 * Adaptive conversation list-detail layout
 * Specifically designed for the chat app
 */
@Composable
fun AdaptiveConversationLayout(
    windowSizeClass: WindowWidthSizeClass,
    selectedConversationId: String?,
    conversationListScreen: @Composable () -> Unit,
    chatScreen: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TwoPaneLayout(
        windowSizeClass = windowSizeClass,
        showDetail = selectedConversationId != null,
        listPane = conversationListScreen,
        detailPane = chatScreen,
        modifier = modifier
    )
}

/**
 * Calculate optimal list/detail ratio for current window size
 */
@Composable
fun rememberListDetailRatio(windowSizeClass: WindowWidthSizeClass): Pair<Float, Float> {
    return when (windowSizeClass) {
        WindowWidthSizeClass.Compact -> 1f to 0f // Full screen
        WindowWidthSizeClass.Medium -> 0.35f to 0.65f // 35/65 split
        WindowWidthSizeClass.Expanded -> 0.30f to 0.70f // 30/70 split
        else -> 0.35f to 0.65f
    }
}
