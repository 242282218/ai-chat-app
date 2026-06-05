package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class LocalWebSearchTool(
    private val searchConfigProvider: suspend () -> SearchConfig,
    private val searchClient: LocalSearchClient,
) : LocalTool {
    override val descriptor: ToolDescriptor = LocalWebSearchToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<LocalWebSearchArguments>(request.toolCall.arguments)
        val query = args.query.trim()
        if (query.isBlank()) {
            throw InvalidLocalToolArgumentsException("搜索关键词不能为空。")
        }
        val settings = searchConfigProvider()
        if (!settings.enabled) {
            throw LocalToolUnavailableException("本地搜索未启用，请在工具页配置搜索 Provider。")
        }
        if (settings.apiKey.isBlank()) {
            throw LocalToolUnavailableException("搜索 API Key 未配置，请在工具页保存搜索 Provider Key。")
        }
        val maxResults = args.maxResults ?: settings.maxResults
        if (maxResults !in MIN_LOCAL_SEARCH_RESULTS..MAX_LOCAL_SEARCH_RESULTS) {
            throw InvalidLocalToolArgumentsException("maxResults 必须在 1 到 20 之间。")
        }
        val searchDepth = args.searchDepth?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: settings.searchDepth
        if (searchDepth !in VALID_LOCAL_SEARCH_DEPTHS) {
            throw InvalidLocalToolArgumentsException("searchDepth 仅支持 basic 或 advanced。")
        }
        val topic = args.topic?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: settings.topic
        if (topic !in VALID_LOCAL_SEARCH_TOPICS) {
            throw InvalidLocalToolArgumentsException("topic 仅支持 general、news 或 finance。")
        }

        val response = searchClient.search(
            query = query,
            config = settings.copy(
                maxResults = maxResults,
                searchDepth = searchDepth,
                topic = topic,
            ),
        )
        return LocalToolExecution(ToolOutput.Json(localToolJson.encodeToString(response.toOutput())))
    }
}

val LocalWebSearchToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "web_search_local",
    displayName = "本地网络搜索",
    description = "使用 App 端配置的搜索 Provider 获取带来源的结构化搜索结果，Gateway 只作可选兜底。",
    permissionLevel = ToolPermissionLevel.Network,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["query"],
          "properties": {
            "query": {
              "type": "string",
              "description": "搜索关键词。"
            },
            "maxResults": {
              "type": "integer",
              "minimum": 1,
              "maximum": 20
            },
            "searchDepth": {
              "type": "string",
              "enum": ["basic", "advanced"]
            },
            "topic": {
              "type": "string",
              "enum": ["general", "news", "finance"]
            }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = 20,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Medium,
    requiresNetwork = true,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
)

@Serializable
private data class LocalWebSearchArguments(
    val query: String = "",
    val maxResults: Int? = null,
    val searchDepth: String? = null,
    val topic: String? = null,
)

private const val MIN_LOCAL_SEARCH_RESULTS = 1
private const val MAX_LOCAL_SEARCH_RESULTS = 20
private val VALID_LOCAL_SEARCH_DEPTHS = setOf("basic", "advanced")
private val VALID_LOCAL_SEARCH_TOPICS = setOf("general", "news", "finance")

@Serializable
private data class LocalWebSearchOutput(
    val query: String,
    val fetchedAt: String,
    val results: List<LocalWebSearchResultOutput>,
)

@Serializable
private data class LocalWebSearchResultOutput(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

private fun SearchResponse.toOutput(): LocalWebSearchOutput =
    LocalWebSearchOutput(
        query = query,
        fetchedAt = fetchedAt.toString(),
        results = results.map { it.toOutput() },
    )

private fun SearchResult.toOutput(): LocalWebSearchResultOutput =
    LocalWebSearchResultOutput(
        title = title,
        summary = summary,
        url = url,
        source = source,
        publishedAt = publishedAt?.toString(),
    )
