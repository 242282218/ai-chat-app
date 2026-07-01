package com.aichat.workbench.ui.feedback

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.statusColors

/**
 * Custom styled Snackbar for consistent feedback.
 * Part of Phase 4: Error Feedback Improvement
 */

/**
 * Workbench styled Snackbar with rounded corners and proper elevation
 */
@Composable
fun WorkbenchSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    level: FeedbackLevel = FeedbackLevel.INFO,
) {
    val containerColor = snackbarContainerColor(level)
    val contentColor = snackbarContentColor(level)
    val actionColor = snackbarActionColor(level)
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = containerColor,
        contentColor = contentColor,
        actionColor = actionColor,
    )
}

/**
 * Snackbar with action button
 */
@Composable
fun WorkbenchSnackbarWithAction(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    level: FeedbackLevel = FeedbackLevel.INFO,
) {
    val containerColor = snackbarContainerColor(level)
    val contentColor = snackbarContentColor(level)
    val actionColor = snackbarActionColor(level)
    Snackbar(
        modifier = modifier,
        action = {
            val actionLabel = snackbarData.visuals.actionLabel
            if (actionLabel != null) {
                TextButton(
                    onClick = { snackbarData.performAction() },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        },
        dismissAction = if (snackbarData.visuals.withDismissAction) {
            {
                TextButton(
                    onClick = { snackbarData.dismiss() },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text("关闭")
                }
            }
        } else null,
        shape = MaterialTheme.shapes.large,
        containerColor = containerColor,
        contentColor = contentColor,
        actionContentColor = actionColor,
        dismissActionContentColor = actionColor,
    ) {
        Text(snackbarData.visuals.message)
    }
}

@Composable
private fun snackbarContainerColor(level: FeedbackLevel): Color =
    when (level) {
        FeedbackLevel.SUCCESS -> statusColors(StatusTone.Success).container
        FeedbackLevel.WARNING -> statusColors(StatusTone.Warning).container
        FeedbackLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        FeedbackLevel.INFO -> SnackbarDefaults.color
    }

@Composable
private fun snackbarContentColor(level: FeedbackLevel): Color =
    when (level) {
        FeedbackLevel.SUCCESS -> statusColors(StatusTone.Success).content
        FeedbackLevel.WARNING -> statusColors(StatusTone.Warning).content
        FeedbackLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        FeedbackLevel.INFO -> SnackbarDefaults.contentColor
    }

@Composable
private fun snackbarActionColor(level: FeedbackLevel): Color =
    when (level) {
        FeedbackLevel.SUCCESS -> statusColors(StatusTone.Success).content
        FeedbackLevel.WARNING -> statusColors(StatusTone.Warning).content
        FeedbackLevel.ERROR -> MaterialTheme.colorScheme.error
        FeedbackLevel.INFO -> SnackbarDefaults.actionColor
    }
