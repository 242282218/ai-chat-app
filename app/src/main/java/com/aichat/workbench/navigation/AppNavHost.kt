package com.aichat.workbench.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.feature.chat.ChatScreen
import com.aichat.workbench.feature.conversations.ConversationsScreen
import com.aichat.workbench.feature.image.ImageGenerationScreen
import com.aichat.workbench.feature.prompt.PromptPresetScreen
import com.aichat.workbench.feature.provider.ProviderSettingsScreen
import com.aichat.workbench.feature.settings.DataSettingsScreen
import com.aichat.workbench.feature.settings.SettingsHubScreen
import com.aichat.workbench.feature.tools.ToolsHubScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = AppDestination.bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                AppBottomBar(currentRoute) { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Conversations.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Conversations.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { slideInHorizontally { -it / 5 } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() },
        ) {
            composable(AppDestination.Conversations.route) {
                ConversationsScreen(
                    onConversationClick = navController::navigateToConversation,
                    onNewChat = { draft -> navController.navigateToNewChat(draft, temporary = false) },
                )
            }
            composable(AppDestination.ImageGen.route) {
                ImageGenerationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                    onSendToChat = { draft -> navController.navigateToNewChat(draft, temporary = false) },
                    showBackButton = false,
                )
            }
            composable(AppDestination.ToolsHub.route) {
                ToolsHubScreen(
                    onSendToChat = { draft -> navController.navigateToNewChat(draft, temporary = false) },
                )
            }
            composable(AppDestination.SettingsHub.route) {
                SettingsHubScreen(
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                    onOpenPrompts = { navController.navigateSingleTop(AppDestination.PromptPresets) },
                    onOpenData = { navController.navigateSingleTop(AppDestination.DataSettings) },
                )
            }

            composable(
                route = CHAT_CONVERSATION_ROUTE,
                arguments = listOf(navArgument(CHAT_CONVERSATION_ID_ARG) { type = NavType.StringType }),
            ) { entry ->
                ChatScreen(
                    initialConversationId = entry.arguments
                        ?.getString(CHAT_CONVERSATION_ID_ARG)
                        ?.let(::ConversationId),
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                    onOpenTools = { navController.navigateSingleTop(AppDestination.ToolsHub) },
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
            ) { entry ->
                ChatScreen(
                    initialConversationId = null,
                    initialDraft = entry.arguments?.getString(CHAT_DRAFT_ARG).orEmpty(),
                    initialTemporary = entry.arguments?.getBoolean(CHAT_TEMPORARY_ARG) == true,
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                    onOpenTools = { navController.navigateSingleTop(AppDestination.ToolsHub) },
                )
            }
            composable(AppDestination.ProviderSettings.route) {
                ProviderSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.PromptPresets.route) {
                PromptPresetScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.DataSettings.route) {
                DataSettingsScreen(onBack = { navController.popBackStack() })
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
    navigate(
        "${AppDestination.Chat.route}?$CHAT_DRAFT_ARG=${Uri.encode(draft)}&$CHAT_TEMPORARY_ARG=$temporary",
    ) {
        launchSingleTop = true
    }
}

private const val CHAT_CONVERSATION_ID_ARG = "conversationId"
private const val CHAT_DRAFT_ARG = "draft"
private const val CHAT_TEMPORARY_ARG = "temporary"
private const val CHAT_CONVERSATION_ROUTE = "chat/{$CHAT_CONVERSATION_ID_ARG}"
private const val CHAT_NEW_ROUTE = "chat?$CHAT_DRAFT_ARG={$CHAT_DRAFT_ARG}&$CHAT_TEMPORARY_ARG={$CHAT_TEMPORARY_ARG}"
