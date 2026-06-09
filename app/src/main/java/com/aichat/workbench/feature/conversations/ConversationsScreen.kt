package com.aichat.workbench.feature.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.ui.component.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (ConversationId) -> Unit,
    onNewChat: (String) -> Unit,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "对话",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { onNewChat("") }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新对话")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        text = "发消息开始对话...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onNewChat(draft)
                                draft = ""
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送")
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            if (state.recentConversations.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "暂无对话",
                    message = "在上方输入框发消息开始新对话",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn {
                    items(state.recentConversations, key = { it.id.value }) { conversation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConversationClick(conversation.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = conversation.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}
