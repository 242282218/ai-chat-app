package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.ui.brand.WorkbenchBrandMark
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatSessionDrawer(
    state: ChatUiState,
    onNewConversation: () -> Unit,
    onConversationSelected: (ConversationId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            DrawerHeader()
            NavigationDrawerItem(
                label = { Text(text = "新对话") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null) },
                selected = state.selectedConversationId == null,
                onClick = onNewConversation,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ConversationList(
                conversations = state.conversations,
                selectedConversationId = state.selectedConversationId,
                onConversationSelected = onConversationSelected,
                modifier = Modifier.weight(1f),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text(text = "图片生成") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.Image, contentDescription = null) },
                selected = false,
                onClick = onOpenImageGeneration,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text(text = "设置") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkbenchBrandMark(modifier = Modifier.size(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "AI 工作台",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "本地会话历史",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    selectedConversationId: ConversationId?,
    onConversationSelected: (ConversationId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "还没有会话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(conversations, key = { it.id.value }) { conversation ->
            ConversationDrawerItem(
                conversation = conversation,
                selected = conversation.id == selectedConversationId,
                onClick = { onConversationSelected(conversation.id) },
            )
        }
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatDrawerTime(conversation),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        icon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

private fun formatDrawerTime(conversation: Conversation): String =
    drawerTimeFormatter.format(conversation.updatedAt)

private val drawerTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
