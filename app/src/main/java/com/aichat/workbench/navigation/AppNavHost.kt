package com.aichat.workbench.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.feature.chat.ChatScreen
import com.aichat.workbench.feature.image.ImageGenerationScreen
import com.aichat.workbench.feature.provider.ProviderSettingsScreen
import com.aichat.workbench.feature.settings.SettingsScreen
import org.koin.compose.koinInject

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val draftHandoffRepository = koinInject<DraftHandoffRepository>()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Chat.route,
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
        composable(AppDestination.Chat.route) {
            ChatRoute(
                initialConversationId = null,
                startNewConversation = true,
                onBack = null,
                onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                onOpenSettings = { navController.navigateSingleTop(AppDestination.Settings) },
                onOpenImageGeneration = { navController.navigateSingleTop(AppDestination.ImageGen) },
                showConversationDrawer = true,
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
                onOpenSettings = { navController.navigateSingleTop(AppDestination.Settings) },
                onOpenImageGeneration = { navController.navigateSingleTop(AppDestination.ImageGen) },
                showConversationDrawer = true,
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
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProviderSettings = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                onOpenImageGeneration = { navController.navigateSingleTop(AppDestination.ImageGen) },
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
                onBack = null,
                onOpenProviders = { navController.navigateSingleTop(AppDestination.ProviderSettings) },
                onOpenSettings = { navController.navigateSingleTop(AppDestination.Settings) },
                onOpenImageGeneration = { navController.navigateSingleTop(AppDestination.ImageGen) },
                showConversationDrawer = true,
            )
        }

        composable(AppDestination.ProviderSettings.route) {
            ProviderSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun ChatRoute(
    initialConversationId: ConversationId?,
    onBack: (() -> Unit)?,
    onOpenProviders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImageGeneration: () -> Unit,
    initialDraft: String = "",
    startNewConversation: Boolean = false,
    showConversationDrawer: Boolean = false,
) {
    ChatScreen(
        initialConversationId = initialConversationId,
        initialDraft = initialDraft,
        startNewConversation = startNewConversation,
        showConversationDrawer = showConversationDrawer,
        onBack = onBack,
        onOpenProviders = onOpenProviders,
        onOpenSettings = onOpenSettings,
        onOpenImageGeneration = onOpenImageGeneration,
    )
}

private fun NavController.navigateSingleTop(destination: AppDestination) {
    navigate(destination.route) {
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
        "$CHAT_NEW_BASE_ROUTE?$CHAT_DRAFT_ARG=${Uri.encode(routeDraft)}&$CHAT_DRAFT_REF_ARG=${Uri.encode(draftRef.orEmpty())}",
    )
}

private const val CHAT_CONVERSATION_ID_ARG = "conversationId"
private const val CHAT_DRAFT_ARG = "draft"
private const val CHAT_DRAFT_REF_ARG = "draftRef"
private const val MAX_ROUTE_DRAFT_LENGTH = 1024
private const val CHAT_CONVERSATION_ROUTE = "chat/{$CHAT_CONVERSATION_ID_ARG}"
private const val CHAT_NEW_BASE_ROUTE = "chat_new"
private const val CHAT_NEW_ROUTE = "$CHAT_NEW_BASE_ROUTE?$CHAT_DRAFT_ARG={$CHAT_DRAFT_ARG}&$CHAT_DRAFT_REF_ARG={$CHAT_DRAFT_REF_ARG}"
