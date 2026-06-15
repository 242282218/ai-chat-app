package com.aichat.workbench.feature.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.EmptyStatePanel
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.workbenchTextFieldColors
import com.aichat.workbench.ui.component.StatusTone
import org.koin.androidx.compose.koinViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (ConversationId) -> Unit,
    onNewChat: (String) -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "对话",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
                actions = {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Add,
                        label = "新对话",
                        onClick = { onNewChat("") },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNewChat(draft) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        text = "发消息开始新对话...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = workbenchTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            onNewChat(draft)
                            draft = ""
                        }
                    },
                ),
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onNewChat(draft)
                                draft = ""
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "发送",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            if (!state.hasAvailableChatProvider) {
                InlineNotice(
                    text = "还没有可用的聊天模型连接",
                    icon = Icons.Outlined.Tune,
                    tone = StatusTone.Warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    androidx.compose.material3.TextButton(onClick = onOpenProviders) {
                        Text(text = "配置")
                    }
                }
            }

            if (state.recentConversations.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Outlined.AutoAwesome,
                    title = if (state.hasAvailableChatProvider) "开始对话" else "连接模型",
                    description = if (state.hasAvailableChatProvider) {
                        "在上方输入框输入消息\n或点击右上角开始新对话"
                    } else {
                        "添加模型连接后\n就可以开始聊天和生成图片"
                    },
                    actionLabel = if (state.hasAvailableChatProvider) null else "配置模型连接",
                    onAction = if (state.hasAvailableChatProvider) null else onOpenProviders,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        state.recentConversations,
                        key = { it.id.value },
                    ) { conversation ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                pendingDeleteId = conversation.id.value
                                dismissState.reset()
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                // Use new SwipeDeleteBackground component
                                com.aichat.workbench.feature.conversations.SwipeDeleteBackground(
                                    dismissProgress = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0f
                                )
                            },
                            content = {
                                // Use new ConversationCard component
                                com.aichat.workbench.feature.conversations.ConversationCard(
                                    conversation = conversation,
                                    onClick = { onConversationClick(conversation.id) },
                                    modifier = Modifier.animateItem()
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val conversation = state.recentConversations.firstOrNull { it.id.value == id }
        WorkbenchConfirmDialog(
            title = "删除对话？",
            message = "删除「${conversation?.title ?: "对话"}」后无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDeleteId = null
                viewModel.deleteConversation(ConversationId(id))
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

internal fun ConversationPreview.lastMessagePreview(): String? {
    val content = lastMessageContent?.take(60)?.replace("\n", " ")?.trim()
    if (content.isNullOrBlank()) return null
    val role = lastMessageRole?.trim()
    val prefix = when {
        role.equals(MessageRole.User.name, ignoreCase = true) -> "你: "
        role.equals(MessageRole.Assistant.name, ignoreCase = true) -> "AI: "
        else -> ""
    }
    return "$prefix$content"
}

private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(instant, now)
    val minutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("MM/dd")
                .withLocale(Locale.CHINA)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
    }
}
