package com.aichat.workbench.data.backup

import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.mapper.messagePartsFromJson
import com.aichat.workbench.data.mapper.modelCapabilityFromJson
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
import com.aichat.workbench.data.mapper.toToolCallsJson
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
import com.aichat.workbench.domain.model.persistableProviderHeaders
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

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

            backupJson.encodeToString(
                BackupJson(
                    version = 1,
                    exportedAt = clock.instant().toString(),
                    providers = providers.map { it.toBackupJson() },
                    prompts = prompts.map { it.toBackupJson() },
                    modelPreferences = modelPreferences.map { it.toBackupJson() },
                    conversations = conversations.toBackupJson(),
                ),
            )
        }

    suspend fun importJson(value: String): BackupImportSummary =
        withContext(Dispatchers.IO) {
            val root = decodeAndValidateBackup(value)

            root.providers.forEach { provider ->
                providerRepository.saveProvider(provider.toProvider(), plaintextApiKey = null)
            }
            root.prompts.forEach { prompt ->
                database.promptPresetDao().upsertPromptPreset(prompt.toPrompt().toEntity())
            }
            root.modelPreferences.forEach { preference ->
                database.modelPreferenceDao().upsertModelPreference(preference.toModelPreference().toEntity())
            }
            root.conversations.forEach { conversationJson ->
                val conversation = conversationJson.toConversation()
                conversationRepository.saveConversation(conversation)
                conversationJson.messages.forEach { messageJson ->
                    conversationRepository.saveMessage(messageJson.toMessage(conversation.id))
                }
            }

            root.toImportSummary()
        }

    suspend fun previewImportJson(value: String): BackupImportSummary =
        withContext(Dispatchers.IO) {
            decodeAndValidateBackup(value).toImportSummary()
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

    private fun ProviderConfig.toBackupJson(): ProviderBackupJson =
        ProviderBackupJson(
            id = id.value,
            name = name,
            type = type.value,
            baseUrl = baseUrl,
            headers = providerJsonElement(headers.persistableProviderHeaders().toJsonObjectString()),
            models = providerJsonElement(models.toModelConfigsJson()),
            defaultModel = defaultModel,
            enabled = enabled,
        )

    private fun PromptPreset.toBackupJson(): PromptBackupJson =
        PromptBackupJson(
            id = id.value,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            defaultModel = defaultModel,
            defaultToolNames = providerJsonElement(defaultToolNames.toJsonArrayString()),
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
        )

    private fun ModelPreference.toBackupJson(): ModelPreferenceBackupJson =
        ModelPreferenceBackupJson(
            id = id.value,
            providerId = providerId.value,
            model = model,
            isFavorite = isFavorite,
            isDefault = isDefault,
            capability = capability?.let { providerJsonElement(it.toJson()) },
            updatedAt = updatedAt.toString(),
        )

    private suspend fun List<Conversation>.toBackupJson(): List<ConversationBackupJson> =
        map { conversation ->
            ConversationBackupJson(
                id = conversation.id.value,
                title = conversation.title,
                createdAt = conversation.createdAt.toString(),
                updatedAt = conversation.updatedAt.toString(),
                defaultProviderId = conversation.defaultProviderId?.value,
                defaultModel = conversation.defaultModel,
                modelParameters = providerJsonElement(conversation.modelParameters.toJson()),
                systemPrompt = conversation.systemPrompt,
                archivedAt = conversation.archivedAt?.toString(),
                messages = conversationRepository.getMessages(conversation.id).map { it.toBackupJson() },
            )
        }

    private fun Message.toBackupJson(): MessageBackupJson =
        MessageBackupJson(
            id = id.value,
            role = role.name,
            content = content,
            contentParts = providerJsonElement(contentParts.toJson()),
            providerId = providerId?.value,
            model = model,
            status = status.name,
            errorSummary = errorSummary,
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            toolCallId = toolCallId?.value,
            parentMessageId = parentMessageId?.value,
            toolCalls = providerJsonElement(toolCalls.toToolCallsJson()),
            toolResult = toolResult,
        )

    private fun ProviderBackupJson.toProvider(): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = name,
            type = ProviderType.fromStorage(type),
            baseUrl = baseUrl,
            apiKeyRef = null,
            headers = stringMapFromJson(headers.jsonStringOrBlank()),
            models = modelConfigsFromJson(models.jsonStringOrBlank()),
            defaultModel = defaultModel,
            enabled = enabled,
        )

    private fun PromptBackupJson.toPrompt(): PromptPreset =
        PromptPreset(
            id = PromptPresetId(id),
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            defaultModel = defaultModel,
            defaultToolNames = stringListFromJson(defaultToolNames.jsonStringOrBlank()),
            createdAt = createdAt.toInstantOrNow(),
            updatedAt = updatedAt.toInstantOrNow(),
        )

    private fun ModelPreferenceBackupJson.toModelPreference(): ModelPreference =
        ModelPreference(
            id = ModelPreferenceId(id),
            providerId = ProviderId(providerId),
            model = model,
            isFavorite = isFavorite,
            isDefault = isDefault,
            capability = capability?.let { modelCapabilityFromJson(it.jsonStringOrBlank()) },
            updatedAt = updatedAt.toInstantOrNow(),
        )

    private fun ConversationBackupJson.toConversation(): Conversation =
        Conversation(
            id = ConversationId(id),
            title = title,
            createdAt = createdAt.toInstantOrNow(),
            updatedAt = updatedAt.toInstantOrNow(),
            defaultProviderId = defaultProviderId?.let(::ProviderId),
            defaultModel = defaultModel,
            modelParameters = modelParametersFromJson(modelParameters.jsonStringOrBlank()),
            systemPrompt = systemPrompt,
            isTemporary = false,
            isSensitive = false,
            archivedAt = archivedAt?.let(Instant::parse),
        )

    private fun MessageBackupJson.toMessage(conversationId: ConversationId): Message =
        Message(
            id = MessageId(id),
            conversationId = conversationId,
            role = MessageRole.valueOf(role),
            content = content,
            contentParts = messagePartsFromJson(contentParts.jsonStringOrBlank()),
            providerId = providerId?.let(::ProviderId),
            model = model,
            status = MessageStatus.valueOf(status),
            errorSummary = errorSummary,
            createdAt = createdAt.toInstantOrNow(),
            updatedAt = updatedAt.toInstantOrNow(),
            toolCallId = toolCallId?.let(::ToolCallId),
            parentMessageId = parentMessageId?.let(::MessageId),
            toolCalls = com.aichat.workbench.data.mapper.toolCallsFromJson(toolCalls.jsonStringOrBlank()),
            toolResult = toolResult,
        )

    private fun String?.toInstantOrNow(): Instant =
        this?.let(Instant::parse) ?: clock.instant()

    private fun providerJsonElement(value: String): JsonElement =
        backupJson.parseToJsonElement(value)

    private fun JsonElement?.jsonStringOrBlank(): String =
        if (this == null || this is JsonNull) "" else toString()

    private fun decodeAndValidateBackup(value: String): BackupJson {
        val sizeBytes = value.toByteArray(Charsets.UTF_8).size
        require(sizeBytes <= MAX_BACKUP_JSON_BYTES) {
            "导入 JSON 超过 ${MAX_BACKUP_JSON_BYTES / 1024 / 1024} MB 限制。"
        }
        val root = backupJson.decodeFromString<BackupJson>(value)
        root.validateForImport()
        return root
    }

    private fun BackupJson.validateForImport() {
        require(version == BACKUP_SCHEMA_VERSION) {
            "不支持的备份版本：$version。"
        }
        require(providers.size <= MAX_BACKUP_PROVIDERS) {
            "导入模型连接数量超过 $MAX_BACKUP_PROVIDERS 个限制。"
        }
        require(prompts.size <= MAX_BACKUP_PROMPTS) {
            "导入提示词数量超过 $MAX_BACKUP_PROMPTS 个限制。"
        }
        require(modelPreferences.size <= MAX_BACKUP_MODEL_PREFERENCES) {
            "导入模型偏好数量超过 $MAX_BACKUP_MODEL_PREFERENCES 个限制。"
        }
        require(conversations.size <= MAX_BACKUP_CONVERSATIONS) {
            "导入会话数量超过 $MAX_BACKUP_CONVERSATIONS 个限制。"
        }
        val messageCount = conversations.sumOf { it.messages.size }
        require(messageCount <= MAX_BACKUP_MESSAGES) {
            "导入消息数量超过 $MAX_BACKUP_MESSAGES 条限制。"
        }
        validateBackupPayloads()
    }

    private fun BackupJson.validateBackupPayloads() {
        providers.forEach { it.toProvider() }
        prompts.forEach { it.toPrompt() }
        modelPreferences.forEach { it.toModelPreference() }
        conversations.forEach { conversationJson ->
            val conversation = conversationJson.toConversation()
            conversationJson.messages.forEach { it.toMessage(conversation.id) }
        }
    }

    private fun BackupJson.toImportSummary(): BackupImportSummary =
        BackupImportSummary(
            providers = providers.size,
            prompts = prompts.size,
            modelPreferences = modelPreferences.size,
            conversations = conversations.size,
            messages = conversations.sumOf { it.messages.size },
        )
}

private const val BACKUP_SCHEMA_VERSION = 1
private const val MAX_BACKUP_JSON_BYTES = 16 * 1024 * 1024
private const val MAX_BACKUP_PROVIDERS = 50
private const val MAX_BACKUP_PROMPTS = 500
private const val MAX_BACKUP_MODEL_PREFERENCES = 1_000
private const val MAX_BACKUP_CONVERSATIONS = 2_000
private const val MAX_BACKUP_MESSAGES = 20_000

private val backupJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
private data class BackupJson(
    val version: Int = 1,
    val exportedAt: String? = null,
    val providers: List<ProviderBackupJson> = emptyList(),
    val prompts: List<PromptBackupJson> = emptyList(),
    val modelPreferences: List<ModelPreferenceBackupJson> = emptyList(),
    val conversations: List<ConversationBackupJson> = emptyList(),
)

@Serializable
private data class ProviderBackupJson(
    val id: String,
    val name: String,
    val type: String,
    val baseUrl: String,
    val headers: JsonElement? = null,
    val models: JsonElement? = null,
    val defaultModel: String? = null,
    val enabled: Boolean = true,
)

@Serializable
private data class PromptBackupJson(
    val id: String,
    val name: String,
    val description: String? = null,
    val systemPrompt: String,
    val defaultModel: String? = null,
    val defaultToolNames: JsonElement? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
private data class ModelPreferenceBackupJson(
    val id: String,
    val providerId: String,
    val model: String,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val capability: JsonElement? = null,
    val updatedAt: String? = null,
)

@Serializable
private data class ConversationBackupJson(
    val id: String,
    val title: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val defaultProviderId: String? = null,
    val defaultModel: String? = null,
    val modelParameters: JsonElement? = null,
    val systemPrompt: String? = null,
    val archivedAt: String? = null,
    val messages: List<MessageBackupJson> = emptyList(),
)

@Serializable
private data class MessageBackupJson(
    val id: String,
    val role: String,
    val content: String,
    val contentParts: JsonElement? = null,
    val providerId: String? = null,
    val model: String? = null,
    val status: String,
    val errorSummary: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val toolCallId: String? = null,
    val parentMessageId: String? = null,
    val toolCalls: JsonElement? = null,
    val toolResult: String? = null,
)
