package com.aichat.workbench.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton

/**
 * Chat input area with error panel and input bar.
 * Part of Phase 3: ChatScreen.kt file splitting
 */
@Composable
fun ChatInputArea(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    onConfirmSendImages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Error panel
        state.error?.let { errorMessage ->
            ChatErrorPanel(
                message = errorMessage,
                onOpenProviders = onOpenProviders,
                onRetry = state.messages.lastOrNull { message ->
                    message.role == MessageRole.Assistant &&
                        message.status == MessageStatus.Failed
                }?.let { failedMessage ->
                    { viewModel.retryMessage(failedMessage.id) }
                },
            )
        }

        // Input bar
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri -> uri?.let(onImagePicked) }
        )

        InputBar(
            input = state.input,
            imageDrafts = state.imageDrafts,
            isGenerating = state.isGenerating,
            isEditing = state.editingMessageId != null,
            canSend = state.providers.any { it.enabled },
            onOpenProviders = onOpenProviders,
            onInputChange = viewModel::updateInput,
            onPickImage = remember(imagePickerLauncher) { { imagePickerLauncher.launch("image/*") } },
            onRemoveImage = viewModel::removeImageDraft,
            onSend = remember(state.imageDrafts) {
                {
                    if (shouldConfirmImageSend(state.imageDrafts)) {
                        onConfirmSendImages()
                    } else {
                        viewModel.sendMessage()
                    }
                }
            },
            onStop = viewModel::stopGeneration,
            onCancelEdit = viewModel::cancelEdit,
        )
    }
}

/**
 * Error panel showing generation failure
 */
@Composable
private fun ChatErrorPanel(
    message: String,
    onOpenProviders: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val clipboard = LocalClipboardManager.current
    InlineNotice(
        text = "回复生成失败，内容未完成。$message",
        icon = Icons.Filled.Info,
        tone = StatusTone.Critical,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        WorkbenchIconButton(
            icon = Icons.Filled.ContentCopy,
            label = "复制错误",
            onClick = {
                clipboard.setText(AnnotatedString(message))
            },
        )
        if (onRetry != null) {
            androidx.compose.material3.TextButton(onClick = onRetry) {
                androidx.compose.material3.Text(text = "重试")
            }
        } else {
            androidx.compose.material3.TextButton(onClick = onOpenProviders) {
                androidx.compose.material3.Text(text = "配置")
            }
        }
    }
}
