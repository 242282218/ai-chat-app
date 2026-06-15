package com.aichat.workbench.ui.feedback

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aichat.workbench.ui.theme.WorkbenchSpacing

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
    level: FeedbackLevel = FeedbackLevel.INFO
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = when (level) {
            FeedbackLevel.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            FeedbackLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
            FeedbackLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            FeedbackLevel.INFO -> SnackbarDefaults.color
        },
        contentColor = when (level) {
            FeedbackLevel.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            FeedbackLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            FeedbackLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            FeedbackLevel.INFO -> SnackbarDefaults.contentColor
        },
        actionColor = when (level) {
            FeedbackLevel.SUCCESS -> MaterialTheme.colorScheme.primary
            FeedbackLevel.ERROR -> MaterialTheme.colorScheme.error
            FeedbackLevel.WARNING -> MaterialTheme.colorScheme.tertiary
            FeedbackLevel.INFO -> SnackbarDefaults.actionColor
        }
    )
}

/**
 * Snackbar with action button
 */
@Composable
fun WorkbenchSnackbarWithAction(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    level: FeedbackLevel = FeedbackLevel.INFO
) {
    Snackbar(
        modifier = modifier,
        action = {
            snackbarData.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    onClick = { snackbarData.performAction() }
                ) {
                    androidx.compose.material3.Text(actionLabel)
                }
            }
        },
        dismissAction = if (snackbarData.visuals.withDismissAction) {
            {
                TextButton(
                    onClick = { snackbarData.dismiss() }
                ) {
                    androidx.compose.material3.Text("关闭")
                }
            }
        } else null,
        shape = MaterialTheme.shapes.large,
        containerColor = when (level) {
            FeedbackLevel.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            FeedbackLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
            FeedbackLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            FeedbackLevel.INFO -> SnackbarDefaults.color
        },
        contentColor = when (level) {
            FeedbackLevel.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            FeedbackLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            FeedbackLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            FeedbackLevel.INFO -> SnackbarDefaults.contentColor
        }
    ) {
        androidx.compose.material3.Text(snackbarData.visuals.message)
    }
}
