package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationManagerTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun selectedOrNewConversationCreatesConversationFromDraft() = runTest {
        val repository = ConversationManagerRepository(clock)
        val manager = ConversationManager(repository, clock)
        val state = ChatUiState(
            selectedProviderId = "provider-1",
            draft = DraftState(
                systemPrompt = "Be direct",
                model = "gpt-test",
                temperature = "0.4",
                temporary = true,
                sensitive = true,
            ),
        )

        val conversation = manager.selectedOrNewConversation(state, "  Explain\nthis   code ")

        assertEquals("Explain this code", conversation.title)
        assertEquals(ProviderId("provider-1"), conversation.defaultProviderId)
        assertEquals("gpt-test", conversation.defaultModel)
        assertEquals(ModelParameters(temperature = 0.4), conversation.modelParameters)
        assertEquals("Be direct", conversation.systemPrompt)
        assertTrue(conversation.isTemporary)
        assertTrue(conversation.isSensitive)
    }

    @Test
    fun withSelectedConversationCopiesConversationSettingsIntoDraft() {
        val provider = provider("provider-1", defaultModel = "fallback-model")
        val conversation = conversation(
            defaultProviderId = provider.id,
            defaultModel = "conversation-model",
            parameters = ModelParameters(temperature = 0.5, topP = 0.8, maxTokens = 256),
        )
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)

        val state = manager.withSelectedConversation(
            state = ChatUiState(providers = listOf(provider), selectedProviderId = provider.id.value),
            conversations = listOf(conversation),
            conversation = conversation,
        )

        assertEquals(conversation.id, state.selectedConversationId)
        assertEquals("Existing", state.titleDraft)
        assertEquals("conversation-model", state.modelDraft)
        assertEquals("0.5", state.temperatureDraft)
        assertEquals("0.8", state.topPDraft)
        assertEquals("256", state.maxTokensDraft)
    }

    @Test
    fun withSelectedConversationSelectsConversationDefaultProvider() {
        val selected = provider("selected", ProviderType.OpenAI, defaultModel = "selected-model")
        val conversationProvider = provider("conversation", ProviderType.OpenAICompatible, defaultModel = "conversation-model")
        val conversation = conversation(
            defaultProviderId = conversationProvider.id,
            defaultModel = null,
        )
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)

        val state = manager.withSelectedConversation(
            state = ChatUiState(
                providers = listOf(selected, conversationProvider),
                selectedProviderId = selected.id.value,
            ),
            conversations = listOf(conversation),
            conversation = conversation,
        )

        assertEquals(conversationProvider.id.value, state.selectedProviderId)
        assertEquals("conversation-model", state.modelDraft)
    }

    @Test
    fun providerForPrefersRetryProviderOverSelectedProvider() {
        val selected = provider("selected", ProviderType.OpenAI)
        val retry = provider("retry", ProviderType.OpenAICompatible)
        val retryMessage = message(providerId = retry.id, model = "retry-model")
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)

        val result = manager.providerFor(
            current = ChatUiState(providers = listOf(selected, retry), selectedProviderId = selected.id.value),
            conversation = conversation(defaultProviderId = selected.id),
            retryFailedMessage = retryMessage,
        )

        assertEquals(retry, result)
    }

    @Test
    fun modelForIgnoresConversationModelWhenProviderFallsBack() {
        val provider = provider("openai", ProviderType.OpenAI, defaultModel = "gpt-default")
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)
        val conversation = conversation(
            defaultProviderId = ProviderId("anthropic"),
            defaultModel = "claude-model",
        )
        val state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-draft"),
        )

        val result = manager.modelFor(
            current = state,
            provider = provider,
            conversation = conversation,
            retryFailedMessage = null,
        )

        assertEquals("gpt-draft", result)
    }

    @Test
    fun modelForIgnoresRetryModelWhenProviderFallsBack() {
        val provider = provider("openai", ProviderType.OpenAI, defaultModel = "gpt-default")
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)
        val retryMessage = message(providerId = ProviderId("anthropic"), model = "claude-model")

        val result = manager.modelFor(
            current = ChatUiState(providers = listOf(provider), selectedProviderId = provider.id.value),
            provider = provider,
            conversation = conversation(defaultProviderId = provider.id),
            retryFailedMessage = retryMessage,
        )

        assertEquals("gpt-default", result)
    }

    private fun provider(
        id: String,
        type: ProviderType = ProviderType.OpenAI,
        defaultModel: String? = "$id-model",
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun conversation(
        defaultProviderId: ProviderId?,
        defaultModel: String? = null,
        parameters: ModelParameters = ModelParameters(),
    ): Conversation =
        Conversation(
            id = ConversationId("conversation-${defaultProviderId?.value ?: "none"}"),
            title = "Existing",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = defaultProviderId,
            defaultModel = defaultModel,
            modelParameters = parameters,
            systemPrompt = null,
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
        )

    private fun message(providerId: ProviderId?, model: String?): Message =
        Message(
            id = com.aichat.workbench.domain.model.MessageId("failed"),
            conversationId = ConversationId("conversation"),
            role = com.aichat.workbench.domain.model.MessageRole.Assistant,
            content = "",
            contentParts = emptyList(),
            providerId = providerId,
            model = model,
            status = com.aichat.workbench.domain.model.MessageStatus.Failed,
            errorSummary = "failed",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = null,
        )
}

private class ConversationManagerRepository(
    private val clock: Clock,
) : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<ConversationId, MutableStateFlow<List<Message>>>()

    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = clock.instant()) else it
        }
    }

    override suspend fun archiveConversation(id: ConversationId) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value

    override suspend fun saveMessage(message: Message) {
        val flow = messages.getOrPut(message.conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
}
