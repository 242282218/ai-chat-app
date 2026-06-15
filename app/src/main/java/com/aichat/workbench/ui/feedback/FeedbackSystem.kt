package com.aichat.workbench.ui.feedback

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Unified feedback system for displaying messages to users.
 * Part of Phase 4: Error Feedback Improvement
 *
 * Replaces InlineNotice with Snackbar for better UX.
 */

/**
 * Feedback level determines the display style and duration
 */
enum class FeedbackLevel {
    INFO,       // Snackbar, short duration
    SUCCESS,    // Snackbar with success color, short duration
    WARNING,    // Snackbar, long duration
    ERROR       // Snackbar with error color, indefinite duration
}

/**
 * Feedback message data
 */
data class FeedbackMessage(
    val text: String,
    val level: FeedbackLevel = FeedbackLevel.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * Feedback manager for showing messages
 */
class FeedbackManager(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    /**
     * Show a feedback message
     */
    fun show(message: FeedbackMessage) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = when (message.level) {
                    FeedbackLevel.INFO -> SnackbarDuration.Short
                    FeedbackLevel.SUCCESS -> SnackbarDuration.Short
                    FeedbackLevel.WARNING -> SnackbarDuration.Long
                    FeedbackLevel.ERROR -> SnackbarDuration.Indefinite
                },
                withDismissAction = message.level == FeedbackLevel.ERROR
            )

            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }

    /**
     * Show info message
     */
    fun showInfo(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        show(FeedbackMessage(text, FeedbackLevel.INFO, actionLabel, onAction))
    }

    /**
     * Show success message
     */
    fun showSuccess(text: String) {
        show(FeedbackMessage(text, FeedbackLevel.SUCCESS))
    }

    /**
     * Show warning message
     */
    fun showWarning(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        show(FeedbackMessage(text, FeedbackLevel.WARNING, actionLabel, onAction))
    }

    /**
     * Show error message
     */
    fun showError(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        show(FeedbackMessage(text, FeedbackLevel.ERROR, actionLabel, onAction))
    }
}

/**
 * Remember a FeedbackManager instance
 */
@Composable
fun rememberFeedbackManager(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
): FeedbackManager {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        FeedbackManager(snackbarHostState, scope)
    }
}
