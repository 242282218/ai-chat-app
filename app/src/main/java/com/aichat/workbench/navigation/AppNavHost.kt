package com.aichat.workbench.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aichat.workbench.feature.chat.ChatScreen
import com.aichat.workbench.feature.home.HomeScreen
import com.aichat.workbench.feature.image.ImageGenerationScreen
import com.aichat.workbench.feature.prompt.PromptPresetScreen
import com.aichat.workbench.feature.provider.ProviderSettingsScreen
import com.aichat.workbench.feature.settings.DataSettingsScreen
import com.aichat.workbench.feature.tools.ToolsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                destinations = AppDestination.topLevel,
                onDestinationClick = { destination -> navController.navigateSingleTop(destination) },
            )
        }

        AppDestination.topLevel.forEach { destination ->
            composable(destination.route) {
                when (destination) {
                    AppDestination.Chat -> {
                        ChatScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProviders = { navController.navigateSingleTop(AppDestination.Providers) },
                        )
                    }
                    AppDestination.Providers -> {
                        ProviderSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    AppDestination.Prompts -> {
                        PromptPresetScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    AppDestination.Images -> {
                        ImageGenerationScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProviders = { navController.navigateSingleTop(AppDestination.Providers) },
                        )
                    }
                    AppDestination.Tools -> {
                        ToolsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    AppDestination.Settings -> {
                        DataSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    AppDestination.Home -> Unit
                }
            }
        }
    }
}

private fun NavController.navigateSingleTop(destination: AppDestination) {
    navigate(destination.route) {
        launchSingleTop = true
    }
}
