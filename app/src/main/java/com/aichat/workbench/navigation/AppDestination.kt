package com.aichat.workbench.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat as FilledChat
import androidx.compose.material.icons.automirrored.outlined.Chat as OutlinedChat
import androidx.compose.material.icons.filled.Image as FilledImage
import androidx.compose.material.icons.filled.Tune as FilledTune
import androidx.compose.material.icons.outlined.Image as OutlinedImage
import androidx.compose.material.icons.outlined.Tune as OutlinedTune
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
    val selectedIcon: ImageVector,
)

val bottomTabItems = listOf(
    BottomTabItem(
        AppDestination.Conversations,
        "对话",
        Icons.AutoMirrored.Outlined.OutlinedChat,
        Icons.AutoMirrored.Filled.FilledChat,
    ),
    BottomTabItem(
        AppDestination.ImageGen,
        "图片",
        Icons.Outlined.OutlinedImage,
        Icons.Filled.FilledImage,
    ),
    BottomTabItem(
        AppDestination.Settings,
        "模型",
        Icons.Outlined.OutlinedTune,
        Icons.Filled.FilledTune,
    ),
)
