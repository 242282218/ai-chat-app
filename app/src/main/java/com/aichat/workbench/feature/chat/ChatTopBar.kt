package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.WorkbenchIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onOpenControls: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectProvider: (String) -> Unit,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    var showModelMenu by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = selectedConversation?.title ?: "新对话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = chatSubtitle(state)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        },
        navigationIcon = {
            WorkbenchIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "返回",
                onClick = onBack,
            )
        },
        actions = {
            WorkbenchIconButton(
                icon = Icons.Filled.Search,
                label = "搜索消息",
                onClick = onToggleSearch,
            )
            // Quick model switch dropdown
            val enabledProviders = state.providers.filter { it.enabled }
            if (enabledProviders.size > 1) {
                WorkbenchIconButton(
                    icon = Icons.Filled.MoreVert,
                    label = "切换模型",
                    onClick = { showModelMenu = true },
                )
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false },
                ) {
                    enabledProviders.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(text = provider.name) },
                            onClick = {
                                onSelectProvider(provider.id.value)
                                showModelMenu = false
                            },
                            trailingIcon = {
                                if (state.selectedProviderId == provider.id.value) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            WorkbenchIconButton(
                icon = Icons.Filled.MoreVert,
                label = "更多",
                onClick = onOpenControls,
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
