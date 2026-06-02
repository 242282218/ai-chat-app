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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.DeleteProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ProviderConnectionTester
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
    val connectionTester = koinInject<ProviderConnectionTester>()
    val saveProvider = remember(repository) { SaveProviderConfigUseCase(repository) }
    val deleteProvider = remember(repository) { DeleteProviderConfigUseCase(repository) }
    val providers by repository.observeProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("OpenAI") }
    var type by rememberSaveable { mutableStateOf(ProviderType.OpenAI) }
    var baseUrl by rememberSaveable { mutableStateOf("https://api.openai.com/v1") }
    var model by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    var storedApiKeyRef by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResetForm by rememberSaveable { mutableStateOf(false) }
    var showProviderEditor by rememberSaveable { mutableStateOf(false) }
    var pendingLoadProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var pendingDeleteProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectableDescriptors = remember { ProviderRegistry.supportedBuiltInChatDescriptors() }

    val hasProviderDraft = editingId != null ||
        name != "OpenAI" ||
        type != ProviderType.OpenAI ||
        baseUrl != "https://api.openai.com/v1" ||
        model.isNotBlank() ||
        apiKey.isNotBlank() ||
        headers.isNotBlank() ||
        !enabled ||
        allowHttp ||
        storedApiKeyRef != null

    fun loadProvider(provider: ProviderConfig) {
        editingId = provider.id.value
        name = provider.name
        type = provider.type
        baseUrl = provider.baseUrl
        model = provider.defaultModel.orEmpty()
        apiKey = ""
        headers = provider.headers.entries.joinToString("\n") { (key, value) -> "$key: $value" }
        enabled = provider.enabled
        allowHttp = provider.baseUrl.startsWith("http://")
        storedApiKeyRef = provider.apiKeyRef
        message = null
    }

    fun applyProviderType(nextType: ProviderType) {
        type = nextType
        val descriptor = ProviderRegistry.builtInDescriptor(nextType)
        if (editingId == null) {
            name = descriptor?.displayName ?: nextType.providerTypeLabel()
            descriptor?.defaultBaseUrl?.let { baseUrl = it }
            allowHttp = descriptor?.defaultBaseUrl?.startsWith("http://") == true
        }
    }

    fun resetForm() {
        editingId = null
        name = "OpenAI"
        type = ProviderType.OpenAI
        baseUrl = "https://api.openai.com/v1"
        model = ""
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
        return ProviderConfig(
            id = providerId,
            name = name.trim(),
            type = type,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKeyRef = null,
            headers = parseHeaderLines(headers),
            models = if (trimmedModel.isBlank()) {
                emptyList()
            } else {
                listOf(ModelConfig(trimmedModel, trimmedModel, capability = type.defaultCapability(trimmedModel)))
            },
            defaultModel = trimmedModel.ifBlank { null },
            enabled = enabled,
        )
    }
    val canSubmitProvider =
        name.isNotBlank() &&
            baseUrl.isValidProviderBaseUrl(allowHttp) &&
            headers.hasValidHeaderLines()

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
                title = { Text(text = "模型连接") },
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
                        canSave = canSubmitProvider,
                        canTest = canSubmitProvider,
                        onSave = {
                            val provider = currentProvider()

                            scope.launch {
                                runCatching {
                                saveProvider(provider, apiKey.trim(), allowHttp)
                            }.onSuccess {
                                resetForm()
                                showProviderEditor = false
                            }.onFailure { error ->
                                message = error.message ?: "保存失败"
                            }
                            }
                        },
                        onTest = {
                            val provider = currentProvider()
                            if (provider.baseUrl.startsWith("http://") && !allowHttp) {
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

@Composable
private fun ProviderHealthHeader(
    providers: List<ProviderConfig>,
) {
    val enabledCount = providers.count { it.enabled }
    val defaultProvider = providers.firstOrNull { it.enabled } ?: providers.firstOrNull()
    val encryptedKeyCount = providers.count { it.apiKeyRef != null }
    val httpCount = providers.count { it.baseUrl.startsWith("http://") }
    val customHeaderCount = providers.count { it.headers.isNotEmpty() }

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
                    text = if (enabledCount > 0) "$enabledCount 可用" else "需要配置",
                    tone = if (enabledCount > 0) StatusTone.Success else StatusTone.Warning,
                )
            },
        )
        if (providers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    StatusPill(text = "${providers.size} 个连接", tone = StatusTone.Neutral)
                }
                item {
                    StatusPill(
                        text = defaultProvider?.name ?: "无默认路由",
                        tone = if (defaultProvider == null) StatusTone.Warning else StatusTone.Success,
                    )
                }
                if (encryptedKeyCount > 0) {
                    item {
                        StatusPill(text = "$encryptedKeyCount 个密钥引用", tone = StatusTone.Success)
                    }
                }
                if (httpCount > 0) {
                    item {
                        StatusPill(text = "$httpCount 个 HTTP 风险", tone = StatusTone.Warning)
                    }
                }
                if (customHeaderCount > 0) {
                    item {
                        StatusPill(text = "$customHeaderCount 个自定义请求头", tone = StatusTone.Warning)
                    }
                }
            }
        }
        MetadataRow(
            label = "请求路径",
            value = "请求会从本机直接发送到配置的接口地址；API Key 不进入备份。",
        )
        if (httpCount > 0 || customHeaderCount > 0) {
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
            label = { Text(text = "默认模型") },
            singleLine = true,
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
                StatusPill(text = model, tone = StatusTone.Success)
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    QuietListRow(
        title = provider.name,
        description = provider.connectionSummary(),
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

private fun ProviderType.defaultCapability(model: String): ModelCapability? {
    val descriptor = ProviderRegistry.builtInDescriptor(this) ?: return null
    return ModelCapability(
        model = model,
        text = descriptor.capabilities.text,
        vision = descriptor.capabilities.vision,
        imageGeneration = descriptor.capabilities.imageGeneration,
        toolCalling = descriptor.capabilities.toolCalling,
        structuredOutput = descriptor.capabilities.structuredOutput,
        longContext = descriptor.capabilities.longContext,
        maxContextTokens = null,
        source = ModelCapabilitySource.BuiltInDefault,
    )
}
