package com.aichat.workbench.data.backup

import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.mapper.messagePartsFromJson
import com.aichat.workbench.data.mapper.modelConfigsFromJson
import com.aichat.workbench.data.mapper.modelParametersFromJson
import com.aichat.workbench.data.mapper.stringListFromJson
import com.aichat.workbench.data.mapper.stringMapFromJson
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.data.mapper.toJson
import com.aichat.workbench.data.mapper.toJsonArrayString
import com.aichat.workbench.data.mapper.toJsonObjectString
import com.aichat.workbench.data.mapper.toModelConfigsJson
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ModelPreferenceId
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupImportSummary(
    val providers: Int,
    val prompts: Int,
    val modelPreferences: Int,
    val conversations: Int,
    val messages: Int,
)

class AppBackupService(
    private val database: AiChatDatabase,
    private val providerRepository: ProviderConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
) {
    suspend fun exportJson(includeChats: Boolean): String =
        withContext(Dispatchers.IO) {
            val providers = providerRepository.observeProviders().first()
            val prompts = database.promptPresetDao().observePromptPresets().first().map { it.toDomain() }
            val modelPreferences = database.modelPreferenceDao().getAllModelPreferences().map { it.toDomain() }
            val conversations = if (includeChats) {
                conversationRepository.observeConversations(includeArchived = true)
                    .first()
                    .filter { !it.isTemporary && !it.isSensitive }
            } else {
                emptyList()
            }

            JSONObject()
                .put("version", 1)
                .put("exportedAt", clock.instant().toString())
                .put("providers", providers.toProviderJson())
                .put("prompts", prompts.toPromptJson())
                .put("modelPreferences", modelPreferences.toModelPreferenceJson())
                .put("conversations", conversations.toConversationJson())
                .toString(2)
        }

    suspend fun importJson(value: String): BackupImportSummary =
        withContext(Dispatchers.IO) {
            val root = JSONObject(value)
            val providers = root.optJSONArray("providers").orEmpty()
            val prompts = root.optJSONArray("prompts").orEmpty()
            val modelPreferences = root.optJSONArray("modelPreferences").orEmpty()
            val conversations = root.optJSONArray("conversations").orEmpty()
            var messageCount = 0

            for (index in 0 until providers.length()) {
                providerRepository.saveProvider(providers.getJSONObject(index).toProvider(), plaintextApiKey = null)
            }
            for (index in 0 until prompts.length()) {
                database.promptPresetDao().upsertPromptPreset(prompts.getJSONObject(index).toPrompt().toEntity())
            }
            for (index in 0 until modelPreferences.length()) {
                database.modelPreferenceDao().upsertModelPreference(modelPreferences.getJSONObject(index).toModelPreference().toEntity())
            }
            for (index in 0 until conversations.length()) {
                val conversationJson = conversations.getJSONObject(index)
                val conversation = conversationJson.toConversation()
                conversationRepository.saveConversation(conversation)
                val messages = conversationJson.optJSONArray("messages").orEmpty()
                for (messageIndex in 0 until messages.length()) {
                    conversationRepository.saveMessage(messages.getJSONObject(messageIndex).toMessage(conversation.id))
                    messageCount += 1
                }
            }

            BackupImportSummary(
                providers = providers.length(),
                prompts = prompts.length(),
                modelPreferences = modelPreferences.length(),
                conversations = conversations.length(),
                messages = messageCount,
            )
        }

    suspend fun clearChatHistory() {
        withContext(Dispatchers.IO) {
            database.toolInvocationDao().deleteAllToolInvocations()
            database.conversationDao().deleteAllConversations()
        }
    }

    suspend fun clearProvidersAndApiKeys() {
        val providers = providerRepository.observeProviders().first()
        providers.forEach { provider ->
            providerRepository.deleteProvider(provider.id)
        }
    }

    suspend fun clearPromptsModelsAndImages() {
        withContext(Dispatchers.IO) {
            database.promptPresetDao().deleteAllPromptPresets()
            database.modelPreferenceDao().deleteAllModelPreferences()
            database.imageGenerationDao().deleteAllImageGenerations()
            imageStorage.deleteAllImages()
        }
    }

    suspend fun clearAllData() {
        clearProvidersAndApiKeys()
        clearPromptsModelsAndImages()
        clearChatHistory()
    }

    private fun List<ProviderConfig>.toProviderJson(): JSONArray =
        JSONArray().also { array ->
            forEach { provider ->
                array.put(
                    JSONObject()
                        .put("id", provider.id.value)
                        .put("name", provider.name)
                        .put("type", provider.type.name)
                        .put("baseUrl", provider.baseUrl)
                        .put("headers", JSONObject(provider.headers.toJsonObjectString()))
                        .put("models", JSONArray(provider.models.toModelConfigsJson()))
                        .putNullable("defaultModel", provider.defaultModel)
                        .put("enabled", provider.enabled),
                )
            }
        }

    private fun List<PromptPreset>.toPromptJson(): JSONArray =
        JSONArray().also { array ->
            forEach { prompt ->
                array.put(
                    JSONObject()
                        .put("id", prompt.id.value)
                        .put("name", prompt.name)
                        .putNullable("description", prompt.description)
                        .put("systemPrompt", prompt.systemPrompt)
                        .putNullable("defaultModel", prompt.defaultModel)
                        .put("defaultToolNames", JSONArray(prompt.defaultToolNames.toJsonArrayString()))
                        .put("createdAt", prompt.createdAt.toString())
                        .put("updatedAt", prompt.updatedAt.toString()),
                )
            }
        }

    private fun List<ModelPreference>.toModelPreferenceJson(): JSONArray =
        JSONArray().also { array ->
            forEach { preference ->
                array.put(
                    JSONObject()
                        .put("id", preference.id.value)
                        .put("providerId", preference.providerId.value)
                        .put("model", preference.model)
                        .put("isFavorite", preference.isFavorite)
                        .put("isDefault", preference.isDefault)
                        .putNullable("capability", preference.capability?.let { JSONObject(it.toJson()) })
                        .put("updatedAt", preference.updatedAt.toString()),
                )
            }
        }

    private suspend fun List<Conversation>.toConversationJson(): JSONArray {
        val array = JSONArray()
        forEach { conversation ->
            array.put(
                JSONObject()
                    .put("id", conversation.id.value)
                    .put("title", conversation.title)
                    .put("createdAt", conversation.createdAt.toString())
                    .put("updatedAt", conversation.updatedAt.toString())
                    .putNullable("defaultProviderId", conversation.defaultProviderId?.value)
                    .putNullable("defaultModel", conversation.defaultModel)
                    .put("modelParameters", JSONObject(conversation.modelParameters.toJson()))
                    .putNullable("systemPrompt", conversation.systemPrompt)
                    .put("archivedAt", conversation.archivedAt?.toString())
                    .put("messages", conversationRepository.getMessages(conversation.id).toMessageJson()),
            )
        }
        return array
    }

    private fun List<Message>.toMessageJson(): JSONArray =
        JSONArray().also { array ->
            forEach { message ->
                array.put(
                    JSONObject()
                        .put("id", message.id.value)
                        .put("role", message.role.name)
                        .put("content", message.content)
                        .put("contentParts", JSONArray(message.contentParts.toJson()))
                        .putNullable("providerId", message.providerId?.value)
                        .putNullable("model", message.model)
                        .put("status", message.status.name)
                        .putNullable("errorSummary", message.errorSummary)
                        .put("createdAt", message.createdAt.toString())
                        .put("updatedAt", message.updatedAt.toString())
                        .putNullable("toolCallId", message.toolCallId?.value)
                        .putNullable("parentMessageId", message.parentMessageId?.value),
                )
            }
        }

    private fun JSONObject.toProvider(): ProviderConfig =
        ProviderConfig(
            id = ProviderId(getString("id")),
            name = getString("name"),
            type = ProviderType.valueOf(getString("type")),
            baseUrl = getString("baseUrl"),
            apiKeyRef = null,
            headers = stringMapFromJson(optJSONObject("headers")?.toString().orEmpty()),
            models = modelConfigsFromJson(optJSONArray("models")?.toString().orEmpty()),
            defaultModel = optNullableString("defaultModel"),
            enabled = optBoolean("enabled", true),
        )

    private fun JSONObject.toPrompt(): PromptPreset =
        PromptPreset(
            id = PromptPresetId(getString("id")),
            name = getString("name"),
            description = optNullableString("description"),
            systemPrompt = getString("systemPrompt"),
            defaultModel = optNullableString("defaultModel"),
            defaultToolNames = stringListFromJson(optJSONArray("defaultToolNames")?.toString().orEmpty()),
            createdAt = optInstant("createdAt"),
            updatedAt = optInstant("updatedAt"),
        )

    private fun JSONObject.toModelPreference(): ModelPreference =
        ModelPreference(
            id = ModelPreferenceId(getString("id")),
            providerId = ProviderId(getString("providerId")),
            model = getString("model"),
            isFavorite = optBoolean("isFavorite"),
            isDefault = optBoolean("isDefault"),
            capability = optJSONObject("capability")?.let {
                com.aichat.workbench.data.mapper.modelCapabilityFromJson(it.toString())
            },
            updatedAt = optInstant("updatedAt"),
        )

    private fun JSONObject.toConversation(): Conversation =
        Conversation(
            id = ConversationId(getString("id")),
            title = getString("title"),
            createdAt = optInstant("createdAt"),
            updatedAt = optInstant("updatedAt"),
            defaultProviderId = optNullableString("defaultProviderId")?.let(::ProviderId),
            defaultModel = optNullableString("defaultModel"),
            modelParameters = modelParametersFromJson(optJSONObject("modelParameters")?.toString().orEmpty()),
            systemPrompt = optNullableString("systemPrompt"),
            isTemporary = false,
            isSensitive = false,
            archivedAt = optNullableString("archivedAt")?.let(Instant::parse),
        )

    private fun JSONObject.toMessage(conversationId: ConversationId): Message =
        Message(
            id = MessageId(getString("id")),
            conversationId = conversationId,
            role = MessageRole.valueOf(getString("role")),
            content = getString("content"),
            contentParts = messagePartsFromJson(optJSONArray("contentParts")?.toString().orEmpty()),
            providerId = optNullableString("providerId")?.let(::ProviderId),
            model = optNullableString("model"),
            status = MessageStatus.valueOf(getString("status")),
            errorSummary = optNullableString("errorSummary"),
            createdAt = optInstant("createdAt"),
            updatedAt = optInstant("updatedAt"),
            toolCallId = optNullableString("toolCallId")?.let(::ToolCallId),
            parentMessageId = optNullableString("parentMessageId")?.let(::MessageId),
        )

    private fun JSONObject.optInstant(name: String): Instant =
        optNullableString(name)?.let(Instant::parse) ?: clock.instant()

    private fun JSONArray?.orEmpty(): JSONArray =
        this ?: JSONArray()

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
        put(name, value ?: JSONObject.NULL)
        return this
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
