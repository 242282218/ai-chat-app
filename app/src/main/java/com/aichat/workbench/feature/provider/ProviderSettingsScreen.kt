package com.aichat.workbench.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.provider.DEFAULT_OPENAI_BASE_URL
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import java.util.UUID
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val repository = koinInject<ProviderConfigRepository>()
    val modelRolePreferenceRepository = koinInject<ModelRolePreferenceRepository>()
    val connectionTester = koinInject<ProviderConnectionTester>()
    val modelDiscoveryClient = koinInject<ProviderModelDiscoveryClient>()
    val saveProvider = remember(repository) { SaveProviderConfigUseCase(repository) }
    val providers by repository.observeProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val modelRolePreferences by modelRolePreferenceRepository.observeAllRolePreferences()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("OpenAI") }
    var type by rememberSaveable { mutableStateOf(ProviderType.OpenAI) }
    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_OPENAI_BASE_URL) }
    var model by rememberSaveable { mutableStateOf("") }
    var imageModel by rememberSaveable { mutableStateOf("") }
    var models by remember { mutableStateOf<List<ModelConfig>>(emptyList()) }
    var apiKey by remember { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    var storedApiKeyRef by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResetForm by rememberSaveable { mutableStateOf(false) }
    var showProviderEditor by rememberSaveable { mutableStateOf(false) }
    var pendingLoadProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectableDescriptors = remember { ProviderRegistry.supportedBuiltInChatDescriptors() }
    val pendingLoadProvider = providers.firstOrNull { it.id.value == pendingLoadProviderId }
    val pendingDeleteProvider = providers.firstOrNull { it.id.value == pendingDeleteProviderId }

    val hasProviderDraft by remember {
        derivedStateOf {
            editingId != null ||
                name != "OpenAI" ||
                type != ProviderType.OpenAI ||
                baseUrl != DEFAULT_OPENAI_BASE_URL ||
                model.isNotBlank() ||
                imageModel.isNotBlank() ||
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
    }

    fun resetForm() {
        editingId = null
        name = "OpenAI"
        type = ProviderType.OpenAI
        baseUrl = DEFAULT_OPENAI_BASE_URL
        model = ""
        imageModel = ""
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
            pendingLoadProviderId = provider.id.value
        } else {
            loadProvider(provider)
            showProviderEditor = true
        }
    }

    fun currentProvider(): ProviderConfig {
        val providerId = ProviderId(editingId ?: UUID.randomUUID().toString())
        val trimmedModel = model.trim()
        val trimmedImageModel = imageModel.trim()
        val normalizedModels = models
            .withManualModel(type, trimmedModel)
            .withManualImageModel(trimmedImageModel)
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
            model = discoveredModels.preferredDiscoveredChatModel()
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
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    if (showBackButton) {
                        WorkbenchIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "返回",
                            onClick = onBack,
                        )
                    }
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
                    modelRolePreferences = modelRolePreferences,
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
                        onDelete = { pendingDeleteProviderId = provider.id.value },
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
                        models = models,
                        onSelectModel = { model = it },
                        onSelectImageModel = { imageModel = it },
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
                                        val defaultModel = model.trim().ifBlank {
                                            discoveredModels.preferredDiscoveredChatModel()
                                        }
                                        provider.copy(
                                            models = discoveredModels
                                                .withManualModel(type, defaultModel)
                                                .withManualImageModel(imageModel),
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
                pendingDeleteProviderId = null
                scope.launch {
                    repository.deleteProvider(provider.id)
                    if (editingId == provider.id.value) {
                        resetForm()
                    }
                }
            },
            onDismiss = { pendingDeleteProviderId = null },
        )
    }

    pendingLoadProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "丢弃模型连接草稿？",
            message = "丢弃当前表单并载入「${provider.name}」。",
            confirmLabel = "载入",
            onConfirm = {
                pendingLoadProviderId = null
                loadProvider(provider)
                showProviderEditor = true
            },
            onDismiss = { pendingLoadProviderId = null },
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
