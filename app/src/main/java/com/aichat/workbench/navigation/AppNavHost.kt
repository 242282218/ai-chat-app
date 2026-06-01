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
                onStartChat = { draft, temporary -> navController.navigateToNewChat(draft, temporary) },
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
        composable(
            route = CHAT_NEW_ROUTE,
            arguments = listOf(
                navArgument(CHAT_DRAFT_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(CHAT_TEMPORARY_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            ChatScreen(
                initialConversationId = null,
                initialDraft = backStackEntry.arguments?.getString(CHAT_DRAFT_ARG).orEmpty(),
                initialTemporary = backStackEntry.arguments?.getBoolean(CHAT_TEMPORARY_ARG) == true,
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

private fun NavController.navigateToNewChat(draft: String, temporary: Boolean) {
    val encodedDraft = Uri.encode(draft)
    navigate("${AppDestination.Chat.route}?$CHAT_DRAFT_ARG=$encodedDraft&$CHAT_TEMPORARY_ARG=$temporary") {
        launchSingleTop = true
    }
}

private const val CHAT_CONVERSATION_ID_ARG = "conversationId"
private const val CHAT_DRAFT_ARG = "draft"
private const val CHAT_TEMPORARY_ARG = "temporary"
private const val CHAT_CONVERSATION_ROUTE = "chat/{$CHAT_CONVERSATION_ID_ARG}"
private const val CHAT_NEW_ROUTE = "chat?$CHAT_DRAFT_ARG={$CHAT_DRAFT_ARG}&$CHAT_TEMPORARY_ARG={$CHAT_TEMPORARY_ARG}"
