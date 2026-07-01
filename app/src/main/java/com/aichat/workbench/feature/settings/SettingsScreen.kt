package com.aichat.workbench.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.WorkbenchIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = settingsItems(
        onOpenProviderSettings = onOpenProviderSettings,
        onOpenImageGeneration = onOpenImageGeneration,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "模型、图片生成和本地数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onBack,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(items) { item ->
                SettingsListItem(item)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SettingsListItem(item: SettingsItem) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = 8.dp),
        headlineContent = {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.enabled) contentColor else disabledColor,
            )
        },
        supportingContent = {
            Text(
                text = item.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (item.enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    disabledColor
                },
            )
        },
        leadingContent = {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabledColor,
            )
        },
        trailingContent = {
            if (item.enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private data class SettingsItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

private fun settingsItems(
    onOpenProviderSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
): List<SettingsItem> = listOf(
    SettingsItem(
        title = "模型服务",
        description = "管理 Provider、API Key、默认文本和图片模型",
        icon = Icons.Filled.Tune,
        onClick = onOpenProviderSettings,
    ),
    SettingsItem(
        title = "图片生成",
        description = "进入图片生成表单、历史和发送到聊天",
        icon = Icons.Filled.Image,
        onClick = onOpenImageGeneration,
    ),
    SettingsItem(
        title = "数据与隐私",
        description = "本地存储、历史清理和导出整理中",
        icon = Icons.Filled.Security,
        enabled = false,
    ),
    SettingsItem(
        title = "外观",
        description = "主题和显示密度整理中",
        icon = Icons.Filled.Palette,
        enabled = false,
    ),
    SettingsItem(
        title = "关于",
        description = "版本、协议和项目说明整理中",
        icon = Icons.Filled.Info,
        enabled = false,
    ),
)
