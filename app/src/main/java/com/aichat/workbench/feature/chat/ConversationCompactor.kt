package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.provider.api.ChatProvider
import java.time.Clock

class ConversationCompactor(
    conversationRepository: ConversationRepository,
    clock: Clock,
    tokenEstimator: ContextTokenEstimator = ContextTokenEstimator(),
) : ConversationContextProvider {
    private val contextBuilder = ConversationContextBuilder(
        conversationRepository = conversationRepository,
        clock = clock,
        tokenEstimator = tokenEstimator,
    )

    override suspend fun build(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        messages: List<Message>,
        chatProvider: ChatProvider,
    ): ConversationContext =
        contextBuilder.build(
            conversation = conversation,
            provider = provider,
            apiKey = apiKey,
            model = model,
            messages = messages,
            chatProvider = chatProvider,
        )

}
