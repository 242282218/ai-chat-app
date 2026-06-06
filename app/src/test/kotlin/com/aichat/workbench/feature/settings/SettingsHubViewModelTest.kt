package com.aichat.workbench.feature.settings

import com.aichat.workbench.domain.model.ThemeMode
import com.aichat.workbench.domain.repository.ThemeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHubViewModelTest {
    @get:Rule
    val mainDispatcherRule = SettingsHubMainDispatcherRule()

    @Test
    fun stateReflectsThemeModeRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeThemeSettingsRepository()
        val viewModel = SettingsHubViewModel(repository)

        repository.setThemeMode(ThemeMode.Dark)

        assertEquals(
            SettingsHubUiState(themeMode = ThemeMode.Dark),
            viewModel.state.first { it.themeMode == ThemeMode.Dark },
        )
    }

    @Test
    fun setThemeModePersistsSelection() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeThemeSettingsRepository()
        val viewModel = SettingsHubViewModel(repository)

        viewModel.setThemeMode(ThemeMode.Light)
        advanceUntilIdle()

        assertEquals(listOf(ThemeMode.Light), repository.savedModes)
        assertEquals(ThemeMode.Light, repository.observeThemeMode().value)
    }
}

private class FakeThemeSettingsRepository(
    initialMode: ThemeMode = ThemeMode.System,
) : ThemeSettingsRepository {
    private val mode = MutableStateFlow(initialMode)
    val savedModes = mutableListOf<ThemeMode>()

    override fun observeThemeMode(): StateFlow<ThemeMode> =
        mode

    override suspend fun setThemeMode(mode: ThemeMode) {
        savedModes += mode
        this.mode.value = mode
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHubMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
