package com.aichat.workbench.tool.runtime

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.local.LocalToolExecutor
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ToolCatalogService(
    private val gatewaySettingsProvider: suspend () -> GatewaySettings,
    gatewayClientProvider: () -> GatewayClient,
    private val localToolExecutor: LocalToolExecutor,
    private val toolSettingsProvider: suspend () -> Map<String, ToolRuntimeSetting>,
    private val clock: Clock,
) {
    private val gatewayClient: GatewayClient by lazy(gatewayClientProvider)
    private val remoteToolsMutex = Mutex()
    private var remoteToolsCache: RemoteToolsCache? = null

    suspend fun availableTools(): List<ToolDescriptor> =
        (localTools() + remoteTools()).filterEnabledTools()

    suspend fun descriptorForAvailable(name: String): ToolDescriptor? =
        availableTools().firstOrNull { it.name == name.canonicalToolName() }

    suspend fun descriptorForExecution(name: String): ToolDescriptor? =
        (localTools() + remoteTools()).firstOrNull { it.name == name.canonicalToolName() }

    private fun localTools(): List<ToolDescriptor> =
        localToolExecutor.descriptors + BuiltInToolRegistry.tools.filter { it.name in LOCAL_TOOL_NAMES }

    private suspend fun List<ToolDescriptor>.filterEnabledTools(): List<ToolDescriptor> {
        val settings = toolSettingsProvider()
        return filter { descriptor -> settings.runtimeSettingFor(descriptor).enabled }
    }

    private suspend fun remoteTools(): List<ToolDescriptor> {
        val settings = gatewaySettingsProvider()
        if (!settings.enabled || !settings.baseUrl.isValidGatewayUrl()) {
            remoteToolsCache = null
            return emptyList()
        }
        val cacheKey = settings.toCacheKey()
        remoteToolsCache
            ?.takeIf { it.cacheKey == cacheKey && clock.instant().isBefore(it.expiresAt) }
            ?.let { return it.tools }

        return remoteToolsMutex.withLock {
            remoteToolsCache
                ?.takeIf { it.cacheKey == cacheKey && clock.instant().isBefore(it.expiresAt) }
                ?.let { return@withLock it.tools }

            runCatching { gatewayClient.toolManifest(cacheKey.baseUrl).tools.filterExecutableRemoteTools() }
                .onSuccess { tools ->
                    remoteToolsCache = RemoteToolsCache(
                        cacheKey = cacheKey,
                        tools = tools,
                        expiresAt = clock.instant().plus(REMOTE_TOOLS_CACHE_TTL),
                    )
                }
                .getOrDefault(emptyList())
        }
    }

    private fun GatewaySettings.toCacheKey(): GatewaySettingsCacheKey =
        GatewaySettingsCacheKey(
            enabled = enabled,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiToken = apiToken.trim(),
        )

    private fun List<ToolDescriptor>.filterExecutableRemoteTools(): List<ToolDescriptor> =
        filter { it.name in EXECUTABLE_REMOTE_TOOL_NAMES }
}

private data class RemoteToolsCache(
    val cacheKey: GatewaySettingsCacheKey,
    val tools: List<ToolDescriptor>,
    val expiresAt: Instant,
)

private data class GatewaySettingsCacheKey(
    val enabled: Boolean,
    val baseUrl: String,
    val apiToken: String,
)

private val REMOTE_TOOLS_CACHE_TTL: Duration = Duration.ofMinutes(5)
private val LOCAL_TOOL_NAMES = setOf("image_upload_to_model", "image_generation")
private val EXECUTABLE_REMOTE_TOOL_NAMES = setOf("web_search", "code_sandbox")
