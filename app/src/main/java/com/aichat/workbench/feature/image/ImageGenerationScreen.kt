package com.aichat.workbench.feature.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onSendToChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageGenerationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClearHistory by remember { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "图片生成",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ImageGenerationForm(
                    state = state,
                    onOpenProviders = onOpenProviders,
                    onSendToChat = onSendToChat,
                    controlsExpanded = controlsExpanded,
                    onToggleControls = { controlsExpanded = !controlsExpanded },
                    viewModel = viewModel,
                )
            }
            item {
                ImageLibraryHeader(
                    state = state,
                    onClearHistory = { confirmClearHistory = true },
                )
            }
            if (state.generations.isEmpty()) {
                item {
                    EmptyImageState()
                }
            } else {
                items(state.generations, key = { it.id.value }) { generation ->
                    ImageGenerationRow(
                        generation = generation,
                        onReusePrompt = { viewModel.reusePrompt(generation.prompt) },
                        onRegenerate = { viewModel.regenerate(generation) },
                        onSave = { generation.originalPath?.let { saveGeneratedImage(context, generation.id.value, it) } },
                        onShare = { generation.originalPath?.let { shareGeneratedImage(context, it) } },
                        onSendToChat = { onSendToChat(generation.toChatReferenceDraft()) },
                    )
                }
            }
        }
    }

    if (confirmClearHistory) {
        WorkbenchConfirmDialog(
            title = "清空图片历史？",
            message = "这会删除 ${state.generations.size} 条本地图片生成记录及其文件，删除后无法恢复。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

@Composable
private fun EmptyImageState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = "暂无图片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "在上方输入提示词生成图片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
