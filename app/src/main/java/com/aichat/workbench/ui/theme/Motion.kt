package com.aichat.workbench.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Motion configuration for consistent animations across the app.
 * Part of Phase 4: Visual Design Upgrade
 */
object WorkbenchMotion {
    /**
     * Spring configurations for different animation types
     */
    object Springs {
        // Bouncy spring for playful interactions (buttons, cards)
        val bouncy = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )

        // Smooth spring for subtle transitions
        val smooth = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

        // Low bounce for content expansion
        val lowBounce = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
}
