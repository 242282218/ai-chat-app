package com.aichat.workbench.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aichat.workbench.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreThemeSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsToSystemThemeMode() = runTest {
        val repository = repository(fileName = "default.preferences_pb", scope = backgroundScope)

        assertEquals(ThemeMode.System, repository.observeThemeMode().first())
    }

    @Test
    fun savesExplicitThemeModeAndClearsSystemMode() = runTest {
        val repository = repository(fileName = "saved.preferences_pb", scope = backgroundScope)

        repository.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, repository.observeThemeMode().first { it == ThemeMode.Dark })

        repository.setThemeMode(ThemeMode.System)
        assertEquals(ThemeMode.System, repository.observeThemeMode().first { it == ThemeMode.System })
    }

    private fun repository(
        fileName: String,
        scope: CoroutineScope,
    ): DataStoreThemeSettingsRepository {
        val file = File(temporaryFolder.root, fileName)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreThemeSettingsRepository(
            dataStore = dataStore,
            scope = scope,
        )
    }
}
