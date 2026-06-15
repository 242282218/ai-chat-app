package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId

/**
 * Message list component with auto-scroll and search navigation.
 * Part of Phase 3: ChatScreen.kt file splitting
 */
@Composable
fun ChatMessageList(
    messages: List<Message>,
    hasEnabledProvider: Boolean,
    onOpenProviders: () -> Unit,
    onEdit: (MessageId) -> Unit,
    onRetry: (MessageId) -> Unit,
    onDelete: (MessageId) -> Unit,
    highlightQuery: String = "",
    matchingMessageIds: List<MessageId> = emptyList(),
    currentMatchIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var isNearBottom by remember { mutableStateOf(true) }

    // Track if user is near bottom of list
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index >= listState.layoutInfo.totalItemsCount - 1
        }.collect { atBottom ->
            isNearBottom = atBottom
        }
    }

    // Auto-scroll to bottom when new messages arrive
    val lastMessage = messages.lastOrNull()
    val contentKey = lastMessage?.id?.value
    LaunchedEffect(contentKey) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Scroll to search match
    LaunchedEffect(currentMatchIndex, matchingMessageIds) {
        if (matchingMessageIds.isNotEmpty() && currentMatchIndex in matchingMessageIds.indices) {
            val targetId = matchingMessageIds[currentMatchIndex]
            val scrollIndex = messages.indexOfFirst { it.id == targetId }
            if (scrollIndex >= 0) {
                isNearBottom = false
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                EmptyConversationPanel(
                    hasEnabledProvider = hasEnabledProvider,
                    onOpenProviders = onOpenProviders,
                )
            }
        } else {
            items(
                messages,
                key = { it.id.value },
            ) { message ->
                // Use new MessageCard with single action callback (performance optimization)
                MessageItem(
                    message = message,
                    onEdit = { onEdit(message.id) },
                    onRetry = { onRetry(message.id) },
                    onDelete = { onDelete(message.id) },
                    highlightQuery = highlightQuery,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
