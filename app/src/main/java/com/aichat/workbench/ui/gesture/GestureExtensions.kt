package com.aichat.workbench.ui.gesture

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Gesture extensions for common interactions with haptic feedback.
 */

/**
 * Long press with haptic feedback
 */
fun Modifier.longPressWithFeedback(
    onLongPress: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current

    this.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            }
        )
    }
}

/**
 * Double tap with haptic feedback
 */
fun Modifier.doubleTapWithFeedback(
    onDoubleTap: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current

    this.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDoubleTap()
            }
        )
    }
}

/**
 * Combined tap gestures with haptic feedback
 */
fun Modifier.tapGesturesWithFeedback(
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
): Modifier = composed {
    val haptic = LocalHapticFeedback.current

    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = onTap?.let { { it() } },
            onLongPress = onLongPress?.let {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
            onDoubleTap = onDoubleTap?.let {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    it()
                }
            }
        )
    }
}
