package com.aichat.workbench.ui.animation

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha

/**
 * Pre-defined transitions for common UI patterns
 */
object WorkbenchTransitions {

    /**
     * Message item enter animation (fade + slide from bottom)
     */
    fun messageItemEnter() = fadeIn(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Medium,
            easing = WorkbenchMotion.Easing.emphasizedDecelerate
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Medium,
            easing = WorkbenchMotion.Easing.emphasizedDecelerate
        ),
        initialOffsetY = { it / 4 } // Slide from 25% below
    )

    /**
     * Message item exit animation (fade + slide up)
     */
    fun messageItemExit() = fadeOut(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Quick,
            easing = WorkbenchMotion.Easing.emphasizedAccelerate
        )
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Quick,
            easing = WorkbenchMotion.Easing.emphasizedAccelerate
        ),
        targetOffsetY = { -it / 4 } // Slide up 25%
    )

    /**
     * Bottom sheet enter animation
     */
    fun bottomSheetEnter() = slideInVertically(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Medium,
            easing = WorkbenchMotion.Easing.emphasizedDecelerate
        ),
        initialOffsetY = { it } // Start from bottom
    ) + fadeIn(
        animationSpec = tween(durationMillis = WorkbenchMotion.Duration.Quick)
    )

    /**
     * Bottom sheet exit animation
     */
    fun bottomSheetExit() = slideOutVertically(
        animationSpec = tween(
            durationMillis = WorkbenchMotion.Duration.Quick,
            easing = WorkbenchMotion.Easing.emphasizedAccelerate
        ),
        targetOffsetY = { it } // Exit to bottom
    ) + fadeOut(
        animationSpec = tween(durationMillis = WorkbenchMotion.Duration.Quick)
    )
}

/**
 * Breathing animation modifier (pulsing alpha effect)
 */
fun Modifier.breathingAnimation(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = WorkbenchMotion.Easing.standard
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    this.alpha(alpha)
}
