package com.aichat.workbench.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.aichat.workbench.feature.provider.ProviderSettingsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val draftHandoffRepository = remember { DraftHandoffRepository() }
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
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it / 3 },
                    animationSpec = tween(300),
                ) + fadeIn(animationSpec = tween(200))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(150))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(300),
                ) + fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 3 },
                    animationSpec = tween(300),
                ) + fadeOut(animationSpec = tween(150))
            },
        ) {
            composable(AppDestination.Conversations.route) {
                ConversationsScreen(
                    onNewChat = { draft ->
                        navController.navigateToNewChat(draft, draftHandoffRepository)
                    },
                    onConversationClick = navController::navigateToConversation,
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                )
            }
            composable(AppDestination.ImageGen.route) {
                ImageGenerationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                    onSendToChat = { draft ->
                        navController.navigateToNewChat(draft, draftHandoffRepository)
                    },
                )
            }
            composable(AppDestination.Settings.route) {
                ProviderSettingsScreen(
                    onBack = { navController.popBackStack() },
                    showBack = false,
                )
            }

            composable(
                route = CHAT_CONVERSATION_ROUTE,
                arguments = listOf(navArgument(CHAT_CONVERSATION_ID_ARG) { type = NavType.StringType }),
            ) { entry ->
                ChatRoute(
                    initialConversationId = entry.arguments
                        ?.getString(CHAT_CONVERSATION_ID_ARG)
                        ?.let(::ConversationId),
                    startNewConversation = false,
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
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
                    navArgument(CHAT_DRAFT_REF_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ChatRoute(
                    initialConversationId = null,
                    initialDraft = draftHandoffRepository
                        .take(entry.arguments?.getString(CHAT_DRAFT_REF_ARG))
                        ?: entry.arguments?.getString(CHAT_DRAFT_ARG).orEmpty(),
                    startNewConversation = true,
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                )
            }
            composable(AppDestination.ProviderSettings.route) {
                ProviderSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ChatRoute(
    initialConversationId: ConversationId?,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    initialDraft: String = "",
    startNewConversation: Boolean = false,
) {
    ChatScreen(
        initialConversationId = initialConversationId,
        initialDraft = initialDraft,
        startNewConversation = startNewConversation,
        onBack = onBack,
        onOpenProviders = onOpenProviders,
    )
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

private fun NavController.navigateToNewChat(
    draft: String,
    draftHandoffRepository: DraftHandoffRepository,
) {
    val draftRef = draft
        .takeIf { it.length > MAX_ROUTE_DRAFT_LENGTH }
        ?.let(draftHandoffRepository::put)
    val routeDraft = draft.takeIf { draftRef == null }.orEmpty()
    navigate(
        "${AppDestination.Chat.route}?$CHAT_DRAFT_ARG=${Uri.encode(routeDraft)}&$CHAT_DRAFT_REF_ARG=${Uri.encode(draftRef.orEmpty())}",
    )
}

private const val CHAT_CONVERSATION_ID_ARG = "conversationId"
private const val CHAT_DRAFT_ARG = "draft"
private const val CHAT_DRAFT_REF_ARG = "draftRef"
private const val MAX_ROUTE_DRAFT_LENGTH = 1024
private const val CHAT_CONVERSATION_ROUTE = "chat/{$CHAT_CONVERSATION_ID_ARG}"
private const val CHAT_NEW_ROUTE = "chat?$CHAT_DRAFT_ARG={$CHAT_DRAFT_ARG}&$CHAT_DRAFT_REF_ARG={$CHAT_DRAFT_REF_ARG}"
