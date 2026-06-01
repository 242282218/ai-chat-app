package com.aichat.workbench.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichat.workbench.domain.model.ConversationId
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
                onConversationClick = { conversationId -> navController.navigateToConversation(conversationId) },
            )
        }

        composable(
            route = CHAT_CONVERSATION_ROUTE,
            arguments = listOf(navArgument(CHAT_CONVERSATION_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            ChatScreen(
                initialConversationId = backStackEntry.arguments
                    ?.getString(CHAT_CONVERSATION_ID_ARG)
                    ?.let(::ConversationId),
                onBack = { navController.popBackStack() },
                onOpenProviders = { navController.navigateSingleTop(AppDestination.Providers) },
            )
        }
        composable(AppDestination.Chat.route) {
            ChatScreen(
                initialConversationId = null,
                onBack = { navController.popBackStack() },
                onOpenProviders = { navController.navigateSingleTop(AppDestination.Providers) },
            )
        }

        AppDestination.topLevel.filterNot { it == AppDestination.Chat }.forEach { destination ->
            composable(destination.route) {
                when (destination) {
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
                    AppDestination.Chat,
                    AppDestination.Home,
                    -> Unit
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

private fun NavController.navigateToConversation(conversationId: ConversationId) {
    navigate("${AppDestination.Chat.route}/${Uri.encode(conversationId.value)}") {
        launchSingleTop = true
    }
}

private const val CHAT_CONVERSATION_ID_ARG = "conversationId"
private const val CHAT_CONVERSATION_ROUTE = "chat/{$CHAT_CONVERSATION_ID_ARG}"
