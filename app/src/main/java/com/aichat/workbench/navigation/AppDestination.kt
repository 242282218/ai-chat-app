package com.aichat.workbench.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
) {
    data object Conversations : AppDestination("conversations")
    data object ImageGen : AppDestination("image_gen")
    data object ToolsHub : AppDestination("tools_hub")
    data object SettingsHub : AppDestination("settings_hub")

    data object Chat : AppDestination("chat")
    data object ProviderSettings : AppDestination("provider_settings")
    data object PromptPresets : AppDestination("prompt_presets")
    data object DataSettings : AppDestination("data_settings")

    companion object {
        val bottomTabs = listOf(Conversations, ImageGen, ToolsHub, SettingsHub)
    }
}

data class BottomTabItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

val bottomTabItems = listOf(
    BottomTabItem(AppDestination.Conversations, "对话", Icons.Outlined.Chat),
    BottomTabItem(AppDestination.ToolsHub, "工具", Icons.Outlined.Widgets),
    BottomTabItem(AppDestination.ImageGen, "图片", Icons.Outlined.Image),
    BottomTabItem(AppDestination.SettingsHub, "设置", Icons.Outlined.Settings),
)
