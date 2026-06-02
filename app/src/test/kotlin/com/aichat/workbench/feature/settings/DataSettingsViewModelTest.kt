package com.aichat.workbench.feature.settings

import com.aichat.workbench.data.backup.BackupImportSummary
import com.aichat.workbench.data.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DataSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = DataSettingsMainDispatcherRule()

    @Test
    fun importCurrentJsonRequiresSuccessfulPreview() = runTest(mainDispatcherRule.testDispatcher) {
        val service = FakeBackupService()
        val viewModel = DataSettingsViewModel(service)
        viewModel.updateImportJson(validBackupJson)

        viewModel.importCurrentJson()
        advanceUntilIdle()

        assertEquals("请先预览并确认导入摘要。", viewModel.state.value.status)
        assertEquals(emptyList<String>(), service.importRequests)
    }

    @Test
    fun failedPreviewDoesNotOpenImportGate() = runTest(mainDispatcherRule.testDispatcher) {
        val service = FakeBackupService(previewError = IllegalArgumentException("备份 JSON 无效。"))
        val viewModel = DataSettingsViewModel(service)
        viewModel.updateImportJson("{")

        viewModel.previewImportJson("{")
        advanceUntilIdle()
        viewModel.importCurrentJson()
        advanceUntilIdle()

        assertNull(viewModel.state.value.importPreviewSummary)
        assertEquals("请先预览并确认导入摘要。", viewModel.state.value.status)
        assertEquals(emptyList<String>(), service.importRequests)
    }

    @Test
    fun previewedJsonCanBeImportedOnce() = runTest(mainDispatcherRule.testDispatcher) {
        val summary = BackupImportSummary(
            providers = 1,
            prompts = 0,
            modelPreferences = 0,
            conversations = 0,
            messages = 0,
        )
        val service = FakeBackupService(previewSummary = summary, importSummary = summary)
        val viewModel = DataSettingsViewModel(service)
        viewModel.updateImportJson(validBackupJson)

        viewModel.previewImportJson(validBackupJson)
        advanceUntilIdle()
        viewModel.importCurrentJson()
        advanceUntilIdle()

        assertEquals(listOf(validBackupJson), service.importRequests)
        assertEquals(summary, viewModel.state.value.importSummary)
        assertNull(viewModel.state.value.importPreviewSummary)
        assertNull(viewModel.state.value.importPreviewJson)
        assertEquals("导入完成", viewModel.state.value.status)
    }
}

private val validBackupJson: String = """
    {"version":1,"providers":[],"prompts":[],"modelPreferences":[],"conversations":[]}
""".trimIndent()

private class FakeBackupService(
    private val previewSummary: BackupImportSummary = BackupImportSummary(
        providers = 0,
        prompts = 0,
        modelPreferences = 0,
        conversations = 0,
        messages = 0,
    ),
    private val importSummary: BackupImportSummary = previewSummary,
    private val previewError: Throwable? = null,
) : BackupService {
    val importRequests = mutableListOf<String>()

    override suspend fun exportJson(includeChats: Boolean): String =
        validBackupJson

    override suspend fun importJson(value: String): BackupImportSummary {
        importRequests += value
        return importSummary
    }

    override suspend fun previewImportJson(value: String): BackupImportSummary {
        previewError?.let { throw it }
        return previewSummary
    }

    override suspend fun clearChatHistory() = Unit

    override suspend fun clearProvidersAndApiKeys() = Unit

    override suspend fun clearPromptsModelsAndImages() = Unit

    override suspend fun clearAllData() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
private class DataSettingsMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
