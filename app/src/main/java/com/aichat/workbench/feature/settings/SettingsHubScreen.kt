package com.aichat.workbench.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.domain.model.ThemeMode
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.theme.Neutral300
import com.aichat.workbench.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onOpenProviders: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenData: () -> Unit,
    viewModel: SettingsHubViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("模型")
            QuietListRow(
                icon = Icons.Outlined.Hub,
                title = "模型与提供商",
                description = "API Key、接口地址、对话与图片模型",
                onClick = onOpenProviders,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = Neutral300,
                thickness = 0.5.dp,
            )
            QuietListRow(
                icon = Icons.Outlined.Psychology,
                title = "提示词预设",
                description = "系统提示词模板",
                onClick = onOpenPrompts,
            )
            SectionLabel("外观")
            ThemeMode.entries.forEach { mode ->
                QuietListRow(
                    icon = Icons.Outlined.Palette,
                    title = mode.displayLabel(),
                    description = mode.description(),
                    onClick = { viewModel.setThemeMode(mode) },
                    trailing = {
                        RadioButton(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                    },
                )
                if (mode != ThemeMode.entries.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = Neutral300,
                        thickness = 0.5.dp,
                    )
                }
            }
            SectionLabel("存储")
            QuietListRow(
                icon = Icons.Outlined.Storage,
                title = "数据管理",
                description = "导出 / 清除对话历史",
                onClick = onOpenData,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
    )
}

private fun ThemeMode.displayLabel(): String =
    when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色模式"
        ThemeMode.Dark -> "深色模式"
    }

private fun ThemeMode.description(): String =
    when (this) {
        ThemeMode.System -> "根据 Android 系统外观自动切换"
        ThemeMode.Light -> "始终使用明亮工作台配色"
        ThemeMode.Dark -> "始终使用暗色工作台配色"
    }
