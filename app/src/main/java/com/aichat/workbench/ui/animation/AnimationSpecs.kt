package com.aichat.workbench.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Unified animation specifications based on Material Design Duration tokens
 * and iOS Human Interface Guidelines for fluid interactions.
 */
object WorkbenchMotion {

    /**
     * Duration tokens (in milliseconds)
     */
    object Duration {
        const val Quick = 150        // Fast feedback, micro-interactions
        const val Medium = 300       // Standard transitions, messages
        const val Slow = 500         // Complex scene transitions
    }

    /**
     * Easing curves (iOS-style for smooth, natural motion)
     */
    object Easing {
        // Emphasized motion for important UI changes
        val emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        // Decelerate curve for entering elements
        val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

        // Accelerate curve for exiting elements
        val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

        // Standard motion for common transitions
        val standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    }

    /**
     * Pre-defined animation specs for common use cases
     */
    object Specs {
        // Message enter animation (smooth slide in)
        val messageEnter = tween<Float>(
            durationMillis = Duration.Medium,
            easing = Easing.emphasizedDecelerate
        )

        // Button press feedback (bouncy spring)
        val buttonPress = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )

        // Quick fade for small UI changes
        val quickFade = tween<Float>(
            durationMillis = Duration.Quick,
            easing = Easing.standard
        )

        // Smooth expansion/collapse
        val expand = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
}
