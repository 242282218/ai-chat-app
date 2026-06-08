package com.aichat.workbench.tool.runtime

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.repository.EmptyModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.domain.tool.ToolExecution
import com.aichat.workbench.domain.tool.ToolExecutionService
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.local.LocalToolExecutor
import com.aichat.workbench.tool.local.defaultLocalTools
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.tool.model.runtimeSettingFor
import java.time.Clock

class ToolExecutor(
    private val gatewaySettingsProvider: suspend () -> GatewaySettings,
    private val gatewayClientProvider: () -> GatewayClient,
    toolInvocationRepository: ToolInvocationRepository,
    providerRepository: ProviderConfigRepository,
    preferencesRepository: ImageGenerationPreferencesRepository,
    modelRolePreferenceRepository: ModelRolePreferenceRepository = EmptyModelRolePreferenceRepository,
    imageGenerationRepository: ImageGenerationRepository,
    imageProvider: ImageGenerationProvider,
    imageStorage: ImageStorage,
    private val clock: Clock,
    private val localToolExecutor: LocalToolExecutor = LocalToolExecutor(defaultLocalTools(clock)),
    private val toolSettingsProvider: suspend () -> Map<String, ToolRuntimeSetting> = { emptyMap() },
) : ToolExecutionService {
    private val gatewayClient: GatewayClient by lazy(gatewayClientProvider)
    private val toolCatalog = ToolCatalogService(
        gatewaySettingsProvider = gatewaySettingsProvider,
        gatewayClientProvider = { gatewayClient },
        localToolExecutor = localToolExecutor,
        toolSettingsProvider = toolSettingsProvider,
        clock = clock,
    )
    private val engine = ToolExecutionEngine(
        toolCatalog = toolCatalog,
        resultWriter = ToolResultWriter(toolInvocationRepository, clock),
        localToolRunner = LocalToolRunner(localToolExecutor),
        gatewayToolRunner = GatewayToolRunner(gatewaySettingsProvider, { gatewayClient }),
        imageGenerationToolRunner = ImageGenerationToolRunner(
            providerRepository = providerRepository,
            preferencesRepository = preferencesRepository,
            modelRolePreferenceRepository = modelRolePreferenceRepository,
            imageGenerationRepository = imageGenerationRepository,
            imageProvider = imageProvider,
            imageStorage = imageStorage,
            clock = clock,
        ),
        toolSettingsProvider = toolSettingsProvider,
        clock = clock,
    )

    override suspend fun availableTools(): List<ToolDescriptor> =
        toolCatalog.availableTools()

    override suspend fun descriptorFor(name: String): ToolDescriptor? =
        toolCatalog.descriptorForAvailable(name)

    override suspend fun requiresConfirmation(descriptor: ToolDescriptor): Boolean =
        descriptor.requiresConfirmation(toolSettingsProvider().runtimeSettingFor(descriptor).permissionPolicy)

    override suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): ToolExecution =
        engine.execute(conversationId, toolCall)

    override suspend fun execute(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        engine.execute(conversationId, toolCall, descriptor)

    override suspend fun deny(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        engine.deny(conversationId, toolCall, descriptor)

    override suspend fun cancel(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        engine.cancel(conversationId, toolCall, descriptor)
}
