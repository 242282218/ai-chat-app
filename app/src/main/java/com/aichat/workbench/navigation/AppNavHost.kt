package com.aichat.workbench.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aichat.workbench.feature.chat.ChatScreen
import com.aichat.workbench.feature.home.HomeScreen
import com.aichat.workbench.feature.home.PlaceholderScreen
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
                onDestinationClick = { destination -> navController.navigate(destination.route) },
            )
        }

        AppDestination.topLevel.forEach { destination ->
            composable(destination.route) {
                when (destination) {
                    AppDestination.Chat -> {
                        ChatScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProviders = { navController.navigate(AppDestination.Providers.route) },
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
                            onOpenProviders = { navController.navigate(AppDestination.Providers.route) },
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
                    else -> {
                        PlaceholderScreen(
                            destination = destination,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
