package com.aichat.workbench.navigation

sealed class AppDestination(
    val route: String,
) {
    data object Chat : AppDestination("chat")
    data object ImageGen : AppDestination("image_gen")
    data object Settings : AppDestination("settings")
    data object ProviderSettings : AppDestination("provider_settings")
}
