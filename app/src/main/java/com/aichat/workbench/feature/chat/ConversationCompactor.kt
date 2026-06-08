package com.aichat.workbench.feature.chat

import com.aichat.workbench.agent.runtime.TaskContextBuilder
import com.aichat.workbench.agent.runtime.TaskContextProvider
import com.aichat.workbench.agent.runtime.TaskConversationContext
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.EmptyMemoryRepository
import com.aichat.workbench.domain.repository.MemoryRepository
import com.aichat.workbench.provider.api.ChatProvider
import java.time.Clock

typealias ConversationContext = TaskConversationContext

class ConversationCompactor(
    conversationRepository: ConversationRepository,
    clock: Clock,
    memoryRepository: MemoryRepository = EmptyMemoryRepository,
    tokenEstimator: ContextTokenEstimator = ContextTokenEstimator(),
) : TaskContextProvider {
    private val contextBuilder = TaskContextBuilder(
        conversationRepository = conversationRepository,
        clock = clock,
        memoryRepository = memoryRepository,
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

    suspend fun compactIfNeeded(
        conversation: Conversation,
        provider: ProviderConfig,
        apiKey: String?,
        model: String,
        messages: List<Message>,
        chatProvider: ChatProvider,
    ): ConversationContext =
        build(
            conversation = conversation,
            provider = provider,
            apiKey = apiKey,
            model = model,
            messages = messages,
            chatProvider = chatProvider,
        )
}
