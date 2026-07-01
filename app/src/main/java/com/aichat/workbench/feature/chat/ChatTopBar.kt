package com.aichat.workbench.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.WorkbenchIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    state: ChatUiState,
    onBack: (() -> Unit)?,
    onOpenDrawer: (() -> Unit)?,
    onOpenControls: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
    onSelectProvider: (String) -> Unit,
) {
    val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
    var showModelMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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
            when {
                onOpenDrawer != null -> {
                    WorkbenchIconButton(
                        icon = Icons.Filled.Menu,
                        label = "打开会话",
                        onClick = onOpenDrawer,
                    )
                }
                onBack != null -> {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onBack,
                    )
                }
            }
        },
        actions = {
            WorkbenchIconButton(
                icon = Icons.Filled.Search,
                label = if (state.isSearchActive) "关闭搜索" else "搜索消息",
                onClick = onToggleSearch,
            )
            // Quick model switch dropdown
            val enabledProviders = state.providers.filter { it.enabled }
            if (enabledProviders.size > 1) {
                WorkbenchIconButton(
                    icon = Icons.Filled.Tune,
                    label = "切换模型",
                    onClick = { showModelMenu = true },
                )
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false },
                ) {
                    enabledProviders.forEach { provider ->
                        val isSelected = state.selectedProviderId == provider.id.value
                        DropdownMenuItem(
                            modifier = Modifier.semantics {
                                selected = isSelected
                            },
                            text = {
                                Text(
                                    text = provider.name,
                                    modifier = Modifier.widthIn(max = 240.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                onSelectProvider(provider.id.value)
                                showModelMenu = false
                            },
                            trailingIcon = {
                                if (isSelected) {
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
                onClick = { showMoreMenu = true },
            )
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = "图片生成") },
                    onClick = {
                        showMoreMenu = false
                        onOpenImageGeneration()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "设置") },
                    onClick = {
                        showMoreMenu = false
                        onOpenSettings()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "对话设置") },
                    onClick = {
                        showMoreMenu = false
                        onOpenControls()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                        )
                    },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
