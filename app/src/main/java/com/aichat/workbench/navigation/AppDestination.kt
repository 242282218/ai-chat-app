package com.aichat.workbench.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    data object Home : AppDestination("home", "Home", "Main workspace")
    data object Chat : AppDestination("chat", "Chat", "Start or continue a conversation")
    data object Providers : AppDestination("providers", "Providers", "Configure model providers")
    data object Prompts : AppDestination("prompts", "Prompts", "Manage local prompt presets")
    data object Images : AppDestination("images", "Images", "Generate and review images")
    data object Tools : AppDestination("tools", "Tools", "Configure optional gateway tools")
    data object Settings : AppDestination("settings", "Settings", "Manage app data and privacy")

    companion object {
        val topLevel = listOf(Chat, Providers, Prompts, Images, Tools, Settings)
    }
}
