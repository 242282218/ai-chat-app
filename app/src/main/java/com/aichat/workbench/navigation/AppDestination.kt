package com.aichat.workbench.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
) {
    data object Conversations : AppDestination("conversations")
    data object ImageGen : AppDestination("image_gen")
    data object Settings : AppDestination("settings")

    data object Chat : AppDestination("chat")
    data object ProviderSettings : AppDestination("provider_settings")

    companion object {
        val bottomTabs = listOf(Conversations, ImageGen, Settings)
    }
}

data class BottomTabItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

val bottomTabItems = listOf(
    BottomTabItem(AppDestination.Conversations, "对话", Icons.AutoMirrored.Outlined.Chat),
    BottomTabItem(AppDestination.ImageGen, "图片", Icons.Outlined.Image),
    BottomTabItem(AppDestination.Settings, "模型", Icons.Outlined.Tune),
)
