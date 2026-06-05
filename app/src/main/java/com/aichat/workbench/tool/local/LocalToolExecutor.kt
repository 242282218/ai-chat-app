package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.SearchConfig
import java.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class LocalToolRequest(
    val conversationId: ConversationId,
    val toolCall: ToolCall,
)

data class LocalToolExecution(
    val output: ToolOutput,
    val contentParts: List<MessagePart> = emptyList(),
)

interface LocalTool {
    val descriptor: ToolDescriptor
    suspend fun execute(request: LocalToolRequest): LocalToolExecution
}

class LocalToolExecutor(
    tools: List<LocalTool>,
) {
    private val toolsByName = tools.associateBy { it.descriptor.name }.also { toolsByName ->
        require(toolsByName.size == tools.size) { "本地工具名称重复。" }
    }

    val descriptors: List<ToolDescriptor> = tools.map { it.descriptor }

    fun canExecute(name: String): Boolean =
        toolsByName.containsKey(name.canonicalToolName())

    suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): LocalToolExecution {
        val tool = toolsByName[toolCall.name.canonicalToolName()]
            ?: throw LocalToolUnavailableException("工具尚未实现：${toolCall.name}")
        return tool.execute(LocalToolRequest(conversationId, toolCall))
    }
}

class InvalidLocalToolArgumentsException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class LocalToolUnavailableException(message: String) : RuntimeException(message)

inline fun <reified T> decodeLocalToolArguments(arguments: String): T =
    try {
        localToolJson.decodeFromString(arguments)
    } catch (error: SerializationException) {
        throw InvalidLocalToolArgumentsException("工具参数 JSON 无效。", error)
    }

val localToolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun defaultLocalTools(
    clock: Clock = Clock.systemUTC(),
    scriptRunner: LocalScriptRunner = UnsupportedLocalScriptRunner(),
    fileReader: AuthorizedFileReader = UnsupportedAuthorizedFileReader(),
    providerRepository: ProviderConfigRepository? = null,
    providerConnectionRunner: ProviderConnectionTestRunner = UnsupportedProviderConnectionTestRunner(),
    searchConfigProvider: (suspend () -> SearchConfig)? = null,
    searchClient: LocalSearchClient? = null,
): List<LocalTool> =
    buildList {
        add(
            TimeTool(clock),
        )
        add(
            TextTransformTool(),
        )
        add(
            CodeDiffPreviewTool(),
        )
        add(
            LocalJsTool(scriptRunner),
        )
        add(
            FileReadTool(fileReader),
        )
        if (searchConfigProvider != null && searchClient != null) {
            add(
                LocalWebSearchTool(
                    searchConfigProvider = searchConfigProvider,
                    searchClient = searchClient,
                ),
            )
        }
        providerRepository?.let {
            add(
                ProviderConnectionTestTool(
                    providerRepository = it,
                    runner = providerConnectionRunner,
                ),
            )
        }
    }
