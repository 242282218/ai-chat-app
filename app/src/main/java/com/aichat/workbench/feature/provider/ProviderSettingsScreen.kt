package com.aichat.workbench.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.ui.component.QuietSectionHeader
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchIconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val viewModel: ProviderSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectableDescriptors = remember { ProviderRegistry.supportedBuiltInChatDescriptors() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openNewProviderEditor() },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = "添加模型连接") },
                shape = MaterialTheme.shapes.medium,
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "模型连接",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = providerTopBarSubtitle(state.providers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (showBack) {
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
                    providers = state.providers,
                    modelRolePreferences = state.modelRolePreferences,
                )
            }

            item {
                QuietSectionHeader(
                    title = "连接",
                    description = "本地保存，密钥不进入备份。",
                )
            }

            if (state.providers.isEmpty()) {
                item {
                    EmptyProviderState(onCreate = { viewModel.openNewProviderEditor() })
                }
            } else {
                items(state.providers, key = { it.id.value }) { provider ->
                    ProviderRow(
                        provider = provider,
                        modelRolePreferences = state.modelRolePreferences,
                        onClick = { viewModel.requestLoadProvider(provider) },
                        onDelete = { viewModel.requestDeleteProvider(provider.id.value) },
                    )
                }
            }
        }
    }

    if (state.showProviderEditor) {
        val focusManager = LocalFocusManager.current
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                viewModel.dismissEditor()
            },
            sheetState = editorSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ProviderForm(
                        editing = state.editingId != null,
                        name = state.name,
                        onNameChange = { viewModel.updateName(it) },
                        type = state.type,
                        providerTypes = selectableDescriptors.map { it.type },
                        onTypeChange = { viewModel.applyProviderType(it) },
                        baseUrl = state.baseUrl,
                        onBaseUrlChange = { viewModel.updateBaseUrl(it) },
                        model = state.model,
                        onModelChange = { viewModel.updateModel(it) },
                        imageModel = state.imageModel,
                        onImageModelChange = { viewModel.updateImageModel(it) },
                        models = state.models,
                        onSelectModel = { viewModel.updateModel(it) },
                        onSelectImageModel = { viewModel.updateImageModel(it) },
                        apiKey = state.apiKey,
                        hasStoredKey = state.hasStoredKey,
                        onApiKeyChange = { viewModel.updateApiKey(it) },
                        headers = state.headers,
                        onHeadersChange = { viewModel.updateHeaders(it) },
                        enabled = state.enabled,
                        onEnabledChange = { viewModel.updateEnabled(it) },
                        allowHttp = state.allowHttp,
                        onAllowHttpChange = { viewModel.updateAllowHttp(it) },
                        formKey = state.editingId ?: "new",
                        message = state.message,
                        isSaving = state.isSaving,
                        isTestingConnection = state.isTestingConnection,
                        isRefreshingModels = state.isRefreshingModels,
                        canSave = state.saveStatus.isReady,
                        canTest = state.testStatus.isReady,
                        onSave = { viewModel.saveProvider() },
                        onRefreshModels = { viewModel.refreshModels() },
                        onTest = { viewModel.testConnection() },
                    )
                }
            }
        }
    }

    state.pendingDeleteProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "删除模型连接？",
            message = "这会从本机删除「${provider.name}」及已保存的 API Key 引用。",
            confirmLabel = "删除",
            onConfirm = { viewModel.confirmDeleteProvider() },
            onDismiss = { viewModel.dismissDeleteProvider() },
        )
    }

    state.pendingLoadProvider?.let { provider ->
        WorkbenchConfirmDialog(
            title = "丢弃模型连接草稿？",
            message = "丢弃当前表单并载入「${provider.name}」。",
            confirmLabel = "载入",
            onConfirm = { viewModel.confirmLoadProvider() },
            onDismiss = { viewModel.dismissLoadProvider() },
            tone = StatusTone.Warning,
        )
    }

    if (state.pendingResetForm) {
        WorkbenchConfirmDialog(
            title = "清空模型连接草稿？",
            message = "丢弃当前模型连接表单并回到新建草稿。",
            confirmLabel = "清空",
            onConfirm = { viewModel.confirmResetForm() },
            onDismiss = { viewModel.dismissResetForm() },
            tone = StatusTone.Warning,
        )
    }
}
