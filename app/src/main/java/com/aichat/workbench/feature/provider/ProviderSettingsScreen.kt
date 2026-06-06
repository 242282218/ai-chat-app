package com.aichat.workbench.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.DeleteProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.defaultModelCapability
import com.aichat.workbench.provider.preferredImageModel
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.provider.supportsTextGeneration
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import com.aichat.workbench.ui.component.InlineNotice
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.QuietListRow
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.util.UUID
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<ProviderConfigRepository>()
    val modelRolePreferenceRepository = koinInject<ModelRolePreferenceRepository>()
    val connectionTester = koinInject<ProviderConnectionTester>()
    val modelDiscoveryClient = koinInject<ProviderModelDiscoveryClient>()
    val saveProvider = remember(repository) { SaveProviderConfigUseCase(repository) }
    val deleteProvider = remember(repository) { DeleteProviderConfigUseCase(repository) }
    val providers by repository.observeProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val modelRolePreferences by modelRolePreferenceRepository.observeAllRolePreferences()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("OpenAI") }
    var type by rememberSaveable { mutableStateOf(ProviderType.OpenAI) }
    var baseUrl by rememberSaveable { mutableStateOf("https://api.openai.com/v1") }
    var model by rememberSaveable { mutableStateOf("") }
    var imageModel by rememberSaveable { mutableStateOf("") }
    var toolModel by rememberSaveable { mutableStateOf("") }
    var codeModel by rememberSaveable { mutableStateOf("") }
    var models by rememberSaveable { mutableStateOf<List<ModelConfig>>(emptyList()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    var storedApiKeyRef by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResetForm by rememberSaveable { mutableStateOf(false) }
    var showProviderEditor by rememberSaveable { mutableStateOf(false) }
    var pendingLoadProvider by rememberSaveable { mutableStateOf<ProviderConfig?>(null) }
    var pendingDeleteProvider by rememberSaveable { mutableStateOf<ProviderConfig?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectableDescriptors = remember { ProviderRegistry.supportedBuiltInChatDescriptors() }

    val hasProviderDraft by remember {
        derivedStateOf {
            editingId != null ||
                name != "OpenAI" ||
                type != ProviderType.OpenAI ||
                baseUrl != "https://api.openai.com/v1" ||
                model.isNotBlank() ||
                imageModel.isNotBlank() ||
                toolModel.isNotBlank() ||
                codeModel.isNotBlank() ||
                models.isNotEmpty() ||
                apiKey.isNotBlank() ||
                headers.isNotBlank() ||
                !enabled ||
                allowHttp ||
                storedApiKeyRef != null
        }
    }

    fun loadProvider(provider: ProviderConfig) {
        editingId = provider.id.value
        name = provider.name
        type = provider.type
        baseUrl = provider.baseUrl
        model = provider.rolePreferenceModel(modelRolePreferences, ModelRole.Chat).orEmpty()
            .ifBlank { provider.defaultModel.orEmpty() }
        imageModel = provider.rolePreferenceModel(modelRolePreferences, ModelRole.Image).orEmpty()
            .ifBlank { provider.models.explicitImageModel() }
        toolModel = provider.rolePreferenceModel(modelRolePreferences, ModelRole.Tool).orEmpty()
        codeModel = provider.rolePreferenceModel(modelRolePreferences, ModelRole.Code).orEmpty()
        models = provider.models
        apiKey = ""
        headers = provider.headers.entries.joinToString("\n") { (key, value) -> "$key: $value" }
        enabled = provider.enabled
        allowHttp = provider.baseUrl.startsWith("http://", ignoreCase = true)
        storedApiKeyRef = provider.apiKeyRef
        message = null
    }

    fun applyProviderType(nextType: ProviderType) {
        type = nextType
        val descriptor = ProviderRegistry.builtInDescriptor(nextType)
        if (editingId == null) {
            name = descriptor?.displayName ?: nextType.providerTypeLabel()
            baseUrl = descriptor?.defaultBaseUrl.orEmpty()
            allowHttp = descriptor?.defaultBaseUrl?.startsWith("http://") == true
        }
        models = emptyList()
        model = ""
        imageModel = ""
        toolModel = ""
        codeModel = ""
    }

    fun resetForm() {
        editingId = null
        name = "OpenAI"
        type = ProviderType.OpenAI
        baseUrl = "https://api.openai.com/v1"
        model = ""
        imageModel = ""
        toolModel = ""
        codeModel = ""
        models = emptyList()
        apiKey = ""
        headers = ""
        enabled = true
        allowHttp = false
        storedApiKeyRef = null
        message = null
    }

    fun openNewProviderEditor() {
        resetForm()
        showProviderEditor = true
    }

    fun requestResetForm() {
        if (hasProviderDraft) {
            pendingResetForm = true
        } else {
            resetForm()
            showProviderEditor = true
        }
    }

    fun requestLoadProvider(provider: ProviderConfig) {
        if (hasProviderDraft) {
            pendingLoadProvider = provider
        } else {
            loadProvider(provider)
            showProviderEditor = true
        }
    }

    fun currentProvider(): ProviderConfig {
        val providerId = ProviderId(editingId ?: UUID.randomUUID().toString())
        val trimmedModel = model.trim()
        val trimmedImageModel = imageModel.trim()
        val trimmedToolModel = toolModel.trim()
        val trimmedCodeModel = codeModel.trim()
        val normalizedModels = models
            .withManualModel(type, trimmedModel)
            .withManualImageModel(trimmedImageModel)
            .withManualToolModel(trimmedToolModel)
            .withManualCodeModel(trimmedCodeModel)
        return ProviderConfig(
            id = providerId,
            name = name.trim(),
            type = type,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKeyRef = null,
            headers = parseHeaderLines(headers),
            models = normalizedModels,
            defaultModel = trimmedModel.ifBlank { null },
            enabled = enabled,
        )
    }

    suspend fun saveRolePreferences(providerId: ProviderId) {
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Chat, model)
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Image, imageModel)
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Tool, toolModel)
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Code, codeModel)
    }

    suspend fun discoverModelsFor(provider: ProviderConfig): List<ModelConfig>? {
        val storedKey = if (apiKey.isBlank()) repository.getApiKey(provider.id) else null
        val result = modelDiscoveryClient.discover(
            provider = provider,
            apiKey = apiKey.trim().ifBlank { storedKey.orEmpty() },
        )
        message = if (result.ok) {
            "${result.message}，保存后可用于聊天。"
        } else {
            result.message
        }
        return result.models.takeIf { result.ok }
    }

    fun applyDiscoveredModels(discoveredModels: List<ModelConfig>) {
        models = discoveredModels
        if (model.isBlank()) {
            model = discoveredModels.firstOrNull()?.id.orEmpty()
        }
    }
    val saveStatus = providerSaveStatus(
        name = name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        hasStoredKey = storedApiKeyRef != null,
        headers = headers,
        enabled = enabled,
        allowHttp = allowHttp,
    )
    val testStatus = providerTestStatus(
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        hasStoredKey = storedApiKeyRef != null,
        headers = headers,
        allowHttp = allowHttp,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openNewProviderEditor() },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = "添加模型连接") },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "模型连接",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = providerTopBarSubtitle(providers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    WorkbenchIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ProviderHealthHeader(
                    providers = providers,
                )
            }

            item {
                QuietSectionHeader(
                    title = "连接",
                    description = "本地保存，密钥不进入备份。",
                )
            }

            if (providers.isEmpty()) {
                item {
                    EmptyProviderState(onCreate = { openNewProviderEditor() })
                }
            } else {
                items(providers, key = { it.id.value }) { provider ->
                    ProviderRow(
                        provider = provider,
                        modelRolePreferences = modelRolePreferences,
                        onClick = { requestLoadProvider(provider) },
                        onDelete = { pendingDeleteProvider = provider },
                    )
                }
            }
        }
    }

    if (showProviderEditor) {
        ModalBottomSheet(
            onDismissRequest = { showProviderEditor = false },
            sheetState = editorSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ProviderForm(
                        editing = editingId != null,
                        name = name,
                        onNameChange = { name = it },
                        type = type,
                        providerTypes = selectableDescriptors.map { it.type },
                        onTypeChange = ::applyProviderType,
                        baseUrl = baseUrl,
                        onBaseUrlChange = { baseUrl = it },
                        model = model,
                        onModelChange = { model = it },
                        imageModel = imageModel,
                        onImageModelChange = { imageModel = it },
                        toolModel = toolModel,
                        onToolModelChange = { toolModel = it },
                        codeModel = codeModel,
                        onCodeModelChange = { codeModel = it },
                        models = models,
                        onSelectModel = { model = it },
                        onSelectImageModel = { imageModel = it },
                        onSelectToolModel = { toolModel = it },
                        onSelectCodeModel = { codeModel = it },
                        apiKey = apiKey,
                        hasStoredKey = storedApiKeyRef != null,
                        onApiKeyChange = { apiKey = it },
                        headers = headers,
                        onHeadersChange = { headers = it },
                        enabled = enabled,
                        onEnabledChange = { enabled = it },
                        allowHttp = allowHttp,
                        onAllowHttpChange = { allowHttp = it },
                        formKey = editingId ?: "new",
                        message = message,
                        canSave = saveStatus.isReady,
                        canTest = testStatus.isReady,
                        onSave = {
                            val provider = currentProvider()
                            scope.launch {
                                runCatching {
                                    val discoveredModels = if (testStatus.isReady) {
                                        discoverModelsFor(provider)
                                    } else {
                                        null
                                    }
                                    val providerToSave = if (discoveredModels == null) {
                                        provider
                                    } else {
                                        val defaultModel = model.trim().ifBlank { discoveredModels.firstOrNull()?.id.orEmpty() }
                                        provider.copy(
                                            models = discoveredModels
                                                .withManualModel(type, defaultModel)
                                                .withManualImageModel(imageModel)
                                                .withManualToolModel(toolModel)
                                                .withManualCodeModel(codeModel),
                                            defaultModel = defaultModel.ifBlank { null },
                                        )
                                    }
                                    saveProvider(providerToSave, apiKey.trim(), allowHttp)
                                    saveRolePreferences(providerToSave.id)
                                }.onSuccess {
                                    resetForm()
                                    showProviderEditor = false
                                }.onFailure { error ->
                                    message = error.message ?: "保存失败"
                                }
                            }
                        },
                        onRefreshModels = {
                            val provider = currentProvider()
                            if (provider.baseUrl.startsWith("http://", ignoreCase = true) && !allowHttp) {
                                message = "刷新模型前请先允许 HTTP。"
                                return@ProviderForm
                            }
                            scope.launch {
                                message = "刷新模型中..."
                                discoverModelsFor(provider)?.let(::applyDiscoveredModels)
                            }
                        },
                        onTest = {
                            val provider = currentProvider()
                            if (provider.baseUrl.startsWith("http://", ignoreCase = true) && !allowHttp) {
                                message = "测试此 URL 前请先允许 HTTP。"
                                return@ProviderForm
                            }
                            scope.launch {
                                message = "测试中..."
                                val storedKey = if (apiKey.isBlank()) {
                                    repository.getApiKey(provider.id)
                                } else {
                                    null
                                }
                                val result = connectionTester.test(
                                    provider = provider,
                                    apiKey = apiKey.trim().ifBlank { storedKey.orEmpty() },
                                )
                                message = if (result.ok) {
                                    "${result.message} (${result.statusCode})"
                                } else {
                                    result.message
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDeleteProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "删除模型连接？",
            message = "这会从本机删除「${provider.name}」及已保存的 API Key 引用。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDeleteProvider = null
                scope.launch {
                    deleteProvider(provider.id)
                    if (editingId == provider.id.value) {
                        resetForm()
                    }
                }
            },
            onDismiss = { pendingDeleteProvider = null },
        )
    }

    pendingLoadProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "丢弃模型连接草稿？",
            message = "丢弃当前表单并载入「${provider.name}」。",
            confirmLabel = "载入",
            onConfirm = {
                pendingLoadProvider = null
                loadProvider(provider)
                showProviderEditor = true
            },
            onDismiss = { pendingLoadProvider = null },
            tone = StatusTone.Warning,
        )
    }

    if (pendingResetForm) {
        WorkbenchConfirmDialog(
            title = "清空模型连接草稿？",
            message = "丢弃当前模型连接表单并回到新建草稿。",
            confirmLabel = "清空",
            onConfirm = {
                pendingResetForm = false
                resetForm()
                showProviderEditor = true
            },
            onDismiss = { pendingResetForm = false },
            tone = StatusTone.Warning,
        )
    }
}

@Composable
private fun EmptyProviderState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "还没有模型连接",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "添加连接后即可用于聊天、图片和模型路由。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCreate) {
            Text(text = "添加模型连接")
        }
    }
}

private fun providerTopBarSubtitle(providers: List<ProviderConfig>): String {
    val stats = providers.providerHealthStats()
    return when {
        stats.totalCount == 0 -> "需要添加模型连接"
        stats.enabledChatCount == 0 -> "${stats.totalCount} 个连接 · 没有可用聊天模型"
        else -> "${stats.enabledChatCount} 个可用 · ${stats.totalCount} 个连接"
    }
}

@Composable
private fun ProviderHealthHeader(
    providers: List<ProviderConfig>,
) {
    val stats = providers.providerHealthStats()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietSectionHeader(
            title = "模型连接",
            description = if (providers.isEmpty()) {
                "还没有模型连接。"
            } else {
                "检查连接健康和请求边界。"
            },
            trailing = {
                StatusPill(
                    text = if (stats.enabledChatCount > 0) "${stats.enabledChatCount} 可用" else "需要配置",
                    tone = if (stats.enabledChatCount > 0) StatusTone.Success else StatusTone.Warning,
                )
            },
        )
        if (providers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    StatusPill(text = "${stats.totalCount} 个连接", tone = StatusTone.Neutral)
                }
                item {
                    StatusPill(
                        text = stats.defaultChatProviderName ?: "无默认路由",
                        tone = if (stats.defaultChatProviderName == null) StatusTone.Warning else StatusTone.Success,
                    )
                }
                if (stats.unsupportedEnabledCount > 0) {
                    item {
                        StatusPill(text = "${stats.unsupportedEnabledCount} 个暂不可用", tone = StatusTone.Warning)
                    }
                }
                if (stats.encryptedKeyCount > 0) {
                    item {
                        StatusPill(text = "${stats.encryptedKeyCount} 个密钥引用", tone = StatusTone.Success)
                    }
                }
                if (stats.httpCount > 0) {
                    item {
                        StatusPill(text = "${stats.httpCount} 个 HTTP 风险", tone = StatusTone.Warning)
                    }
                }
                if (stats.customHeaderCount > 0) {
                    item {
                        StatusPill(text = "${stats.customHeaderCount} 个自定义请求头", tone = StatusTone.Warning)
                    }
                }
            }
        }
        MetadataRow(
            label = "请求路径",
            value = "请求会从本机直接发送到配置的接口地址；API Key 不进入备份。",
        )
        if (stats.httpCount > 0 || stats.customHeaderCount > 0) {
            InlineNotice(
                text = "HTTP 接口或自定义请求头可能改变请求安全边界。",
                icon = Icons.Filled.Lock,
                tone = StatusTone.Warning,
            ) {
                StatusPill(text = "检查配置后再启用", tone = StatusTone.Warning)
            }
        }
    }
}

@Composable
private fun ProviderForm(
    editing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    type: ProviderType,
    providerTypes: List<ProviderType>,
    onTypeChange: (ProviderType) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    imageModel: String,
    onImageModelChange: (String) -> Unit,
    toolModel: String,
    onToolModelChange: (String) -> Unit,
    codeModel: String,
    onCodeModelChange: (String) -> Unit,
    models: List<ModelConfig>,
    onSelectModel: (String) -> Unit,
    onSelectImageModel: (String) -> Unit,
    onSelectToolModel: (String) -> Unit,
    onSelectCodeModel: (String) -> Unit,
    apiKey: String,
    hasStoredKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit,
    formKey: String,
    message: String?,
    canSave: Boolean,
    canTest: Boolean,
    onSave: () -> Unit,
    onRefreshModels: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val hasAdvancedDraft = headers.isNotBlank() || allowHttp
    var advancedExpanded by rememberSaveable(formKey) { mutableStateOf(hasAdvancedDraft) }

    WorkbenchPanel(
        title = if (editing) "编辑模型连接" else "新建模型连接",
        description = "使用自己的 API Key，请求直接发送到配置的接口地址。",
        icon = Icons.Filled.Tune,
        modifier = modifier,
        trailing = {
            if (!enabled) {
                StatusPill(text = "已禁用", tone = StatusTone.Neutral)
            }
        },
    ) {
        ProviderFormSummary(
            name = name,
            type = type,
            baseUrl = baseUrl,
            model = model,
            imageModel = imageModel,
            toolModel = toolModel,
            codeModel = codeModel,
            apiKey = apiKey,
            hasStoredKey = hasStoredKey,
            headers = headers,
            allowHttp = allowHttp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providerTypes, key = { it.value }) { providerType ->
                FilterChip(
                    selected = type == providerType,
                    onClick = { onTypeChange(providerType) },
                    label = { Text(text = providerType.providerTypeLabel()) },
                )
            }
        }

        MetadataRow(
            label = "服务类型",
            value = type.providerTypeLabel(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "名称 *") },
            singleLine = true,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "接口地址 *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "对话模型") },
            supportingText = { Text(text = "默认聊天模型。") },
            singleLine = true,
        )
        ProviderModelPicker(
            models = models,
            selectedModel = model,
            canRefresh = canTest,
            onSelectModel = onSelectModel,
            onRefreshModels = onRefreshModels,
        )
        OutlinedTextField(
            value = imageModel,
            onValueChange = onImageModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "图片模型") },
            supportingText = { Text(text = "单独用于图片生成，不覆盖聊天默认模型。") },
            singleLine = true,
        )
        ProviderImageModelPicker(
            models = models,
            selectedImageModel = imageModel,
            onSelectImageModel = onSelectImageModel,
        )
        OutlinedTextField(
            value = toolModel,
            onValueChange = onToolModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "工具模型") },
            supportingText = { Text(text = "留空时使用对话模型。") },
            singleLine = true,
        )
        ProviderRoleModelPicker(
            models = models.filter { it.supportsTextGeneration() },
            selectedModel = toolModel,
            onSelectModel = onSelectToolModel,
        )
        OutlinedTextField(
            value = codeModel,
            onValueChange = onCodeModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "代码模型") },
            supportingText = { Text(text = "用于后续代码生成、解释和 diff。") },
            singleLine = true,
        )
        ProviderRoleModelPicker(
            models = models.filter { it.supportsTextGeneration() },
            selectedModel = codeModel,
            onSelectModel = onSelectCodeModel,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "API Key") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            visualTransformation = if (showApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                WorkbenchIconButton(
                    icon = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (showApiKey) "隐藏 API Key" else "显示 API Key",
                    onClick = { showApiKey = !showApiKey },
                )
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已启用",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = enabled, onCheckedChange = null)
        }

        ProviderAdvancedFields(
            expanded = advancedExpanded,
            headers = headers,
            allowHttp = allowHttp,
            onToggleExpanded = { advancedExpanded = !advancedExpanded },
            onHeadersChange = onHeadersChange,
            onAllowHttpChange = onAllowHttpChange,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存")
            }
            OutlinedButton(
                onClick = onTest,
                enabled = canTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "测试")
            }
        }

        message?.let {
            ProviderFormFeedback(message = it)
        }
    }
}

@Composable
private fun ProviderModelPicker(
    models: List<ModelConfig>,
    selectedModel: String,
    canRefresh: Boolean,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (models.isEmpty()) "未同步模型" else "已同步 ${models.size} 个模型",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRefreshModels,
                enabled = canRefresh,
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "刷新模型")
            }
        }
        if (models.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == selectedModel.trim(),
                        onClick = { onSelectModel(item.id) },
                        label = {
                            Text(
                                text = item.displayName.ifBlank { item.id },
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (item.id == selectedModel.trim()) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderImageModelPicker(
    models: List<ModelConfig>,
    selectedImageModel: String,
    onSelectImageModel: (String) -> Unit,
) {
    val imageModels = models.filter { it.supportsImageGeneration() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (imageModels.isEmpty()) {
                "没有已识别的图片模型，可手动填写。"
            } else {
                "已识别 ${imageModels.size} 个图片模型"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (imageModels.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageModels, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == selectedImageModel.trim(),
                        onClick = { onSelectImageModel(item.id) },
                        label = {
                            Text(
                                text = item.displayName.ifBlank { item.id },
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (item.id == selectedImageModel.trim()) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRoleModelPicker(
    models: List<ModelConfig>,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
) {
    if (models.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(models, key = { it.id }) { item ->
            FilterChip(
                selected = item.id == selectedModel.trim(),
                onClick = { onSelectModel(item.id) },
                label = {
                    Text(
                        text = item.displayName.ifBlank { item.id },
                        modifier = Modifier.widthIn(max = 180.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    if (item.id == selectedModel.trim()) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                    }
                },
            )
        }
    }
}

@Composable
private fun ProviderAdvancedFields(
    expanded: Boolean,
    headers: String,
    allowHttp: Boolean,
    onToggleExpanded: () -> Unit,
    onHeadersChange: (String) -> Unit,
    onAllowHttpChange: (Boolean) -> Unit,
) {
    val headerStatus = headers.headerStatus()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onToggleExpanded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (expanded) "收起高级网络设置" else "高级网络设置",
                modifier = Modifier.weight(1f),
            )
            if (headers.isNotBlank() || allowHttp) {
                StatusPill(
                    text = advancedProviderLabel(headers, allowHttp, headerStatus),
                    tone = advancedProviderTone(headers, allowHttp, headerStatus),
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = headers,
                onValueChange = onHeadersChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                label = { Text(text = "请求头") },
                supportingText = { Text(text = providerHeaderPolicyText) },
                isError = headerStatus.tone == StatusTone.Critical,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = allowHttp,
                        role = Role.Checkbox,
                        onValueChange = onAllowHttpChange,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allowHttp, onCheckedChange = null)
                Text(text = "允许 HTTP")
            }
        }
    }
}

private fun advancedProviderLabel(
    headers: String,
    allowHttp: Boolean,
    headerStatus: HeaderStatus,
): String =
    when {
        headerStatus.tone == StatusTone.Critical -> headerStatus.label
        headers.isNotBlank() && allowHttp -> "有风险"
        allowHttp -> "HTTP"
        headers.isNotBlank() -> "请求头"
        else -> "默认"
    }

private fun advancedProviderTone(
    headers: String,
    allowHttp: Boolean,
    headerStatus: HeaderStatus,
): StatusTone =
    when {
        headerStatus.tone == StatusTone.Critical -> StatusTone.Critical
        allowHttp -> StatusTone.Warning
        headers.isNotBlank() -> StatusTone.Accent
        else -> StatusTone.Neutral
    }

@Composable
private fun ProviderFormSummary(
    name: String,
    type: ProviderType,
    baseUrl: String,
    model: String,
    imageModel: String,
    toolModel: String,
    codeModel: String,
    apiKey: String,
    hasStoredKey: Boolean,
    headers: String,
    allowHttp: Boolean,
) {
    val urlStatus = baseUrl.providerUrlStatus(allowHttp)
    val headerStatus = headers.headerStatus()
    val requiresApiKey = ProviderRegistry.builtInDescriptor(type)?.requiresApiKey ?: true
    val keyStatus = providerKeyStatus(apiKey, hasStoredKey, requiresApiKey)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (name.isBlank()) "需要名称" else "已命名",
                tone = if (name.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(text = urlStatus.label, tone = urlStatus.tone)
        }
        if (model.isNotBlank()) {
            item {
                StatusPill(text = "聊天 $model", tone = StatusTone.Success)
            }
        }
        if (imageModel.isNotBlank()) {
            item {
                StatusPill(text = "图片 $imageModel", tone = StatusTone.Accent)
            }
        }
        if (toolModel.isNotBlank()) {
            item {
                StatusPill(text = "工具 $toolModel", tone = StatusTone.Accent)
            }
        }
        if (codeModel.isNotBlank()) {
            item {
                StatusPill(text = "代码 $codeModel", tone = StatusTone.Accent)
            }
        }
        item {
            StatusPill(
                text = keyStatus.label,
                tone = keyStatus.tone,
            )
        }
        if (headers.isNotBlank()) {
            item {
                StatusPill(
                    text = headerStatus.label,
                    tone = headerStatus.tone,
                )
            }
        }
    }
}

@Composable
private fun ProviderFormFeedback(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
            text = providerMessageLabel(message),
            tone = providerMessageTone(message),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun providerMessageLabel(message: String): String =
    when {
        message == "已保存" -> "已保存"
        message == "测试中..." -> "测试中"
        providerMessageTone(message) == StatusTone.Critical -> "需要处理"
        else -> "连接"
    }

private fun providerMessageTone(message: String): StatusTone {
    val normalized = message.lowercase()
    return when {
        message == "已保存" -> StatusTone.Success
        message == "测试中..." -> StatusTone.Accent
        normalized.contains("failed") ||
            normalized.contains("invalid") ||
            normalized.contains("missing") ||
            normalized.contains("returned") ||
            normalized.contains("enable allow http") ||
            normalized.contains("失败") ||
            normalized.contains("无效") ||
            normalized.contains("缺失") ||
            normalized.contains("返回") ||
            normalized.contains("启用 allow http") ||
            normalized.contains("暂未接入") ||
            normalized.contains("must") ->
            StatusTone.Critical
        else -> StatusTone.Success
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    modelRolePreferences: List<ModelRolePreference>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    QuietListRow(
        title = provider.name,
        description = provider.connectionSummary(modelRolePreferences),
        icon = Icons.Filled.CheckCircle,
        onClick = onClick,
        trailing = {
            WorkbenchIconButton(
                icon = Icons.Filled.Delete,
                label = "删除模型连接 ${provider.name}",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun ProviderConfig.connectionSummary(modelRolePreferences: List<ModelRolePreference>): String {
    val roleSummary = listOfNotNull(
        roleModel(modelRolePreferences, ModelRole.Chat)?.let { "对话 $it" },
        roleModel(modelRolePreferences, ModelRole.Image)?.let { "图片 $it" },
        roleModel(modelRolePreferences, ModelRole.Tool)?.let { "工具 $it" },
        roleModel(modelRolePreferences, ModelRole.Code)?.let { "代码 $it" },
    )
    return if (roleSummary.isEmpty()) {
        connectionSummary()
    } else {
        "${connectionSummary()} · ${roleSummary.joinToString(" · ")}"
    }
}

private fun ProviderConfig.roleModel(
    preferences: List<ModelRolePreference>,
    role: ModelRole,
): String? =
    rolePreferenceModel(preferences, role)

internal fun List<ModelConfig>.withManualModel(type: ProviderType, model: String): List<ModelConfig> {
    val trimmedModel = model.trim()
    val normalizedModels = map { item ->
        val id = item.id.trim()
        item.copy(
            id = id,
            displayName = item.displayName.trim().ifBlank { id },
            capability = item.capability?.copy(model = item.capability.model.trim()),
        )
    }.filter { it.id.isNotBlank() }

    if (trimmedModel.isBlank() || normalizedModels.any { it.id == trimmedModel }) {
        return normalizedModels.distinctBy { it.id }
    }

    return (
        normalizedModels +
            ModelConfig(
                id = trimmedModel,
                displayName = trimmedModel,
                capability = type.defaultModelCapability(trimmedModel),
            )
        ).distinctBy { it.id }
}

internal fun List<ModelConfig>.withManualImageModel(model: String): List<ModelConfig> =
    withManualRoleModel(model, ::imageGenerationCapability)

internal fun List<ModelConfig>.withManualToolModel(model: String): List<ModelConfig> =
    withManualRoleModel(model, ::toolCallingCapability)

internal fun List<ModelConfig>.withManualCodeModel(model: String): List<ModelConfig> =
    withManualRoleModel(model, ::codeGenerationCapability)

private fun List<ModelConfig>.withManualRoleModel(
    model: String,
    capabilityFor: (String) -> ModelCapability,
): List<ModelConfig> {
    val trimmedModel = model.trim()
    val normalizedModels = map { item -> item.normalizedForRoleModel(trimmedModel, capabilityFor) }
        .filter { it.id.isNotBlank() }
    if (trimmedModel.isBlank() || normalizedModels.any { it.id == trimmedModel }) {
        return normalizedModels.distinctBy { it.id }
    }

    return (
        normalizedModels +
            ModelConfig(
                id = trimmedModel,
                displayName = trimmedModel,
                capability = capabilityFor(trimmedModel),
            )
        ).distinctBy { it.id }
}

private fun ModelConfig.normalizedForRoleModel(
    roleModel: String,
    capabilityFor: (String) -> ModelCapability,
): ModelConfig {
    val normalizedId = id.trim()
    return copy(
        id = normalizedId,
        displayName = displayName.trim().ifBlank { normalizedId },
        capability = if (normalizedId == roleModel) {
            capabilityFor(normalizedId)
        } else {
            capability?.copy(model = capability.model.trim())
        },
    )
}

private fun List<ModelConfig>.explicitImageModel(): String =
    filter { it.supportsImageGeneration() }
        .map { it.id }
        .preferredImageModel()
        .orEmpty()

private fun imageGenerationCapability(model: String): ModelCapability =
    ModelCapability(
        model = model,
        text = false,
        vision = false,
        imageGeneration = true,
        toolCalling = false,
        structuredOutput = false,
        longContext = false,
        maxContextTokens = null,
        source = ModelCapabilitySource.UserOverride,
    )

private fun toolCallingCapability(model: String): ModelCapability =
    ModelCapability(
        model = model,
        text = true,
        vision = false,
        imageGeneration = false,
        toolCalling = true,
        structuredOutput = true,
        longContext = false,
        maxContextTokens = null,
        source = ModelCapabilitySource.UserOverride,
    )

private fun codeGenerationCapability(model: String): ModelCapability =
    ModelCapability(
        model = model,
        text = true,
        vision = false,
        imageGeneration = false,
        toolCalling = false,
        structuredOutput = true,
        longContext = false,
        maxContextTokens = null,
        source = ModelCapabilitySource.UserOverride,
    )
