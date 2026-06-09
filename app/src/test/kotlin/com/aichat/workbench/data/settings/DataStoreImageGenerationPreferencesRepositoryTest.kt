package com.aichat.workbench.data.settings

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataStoreImageGenerationPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var legacyPreferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        legacyPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        legacyPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        legacyPreferences.edit().clear().commit()
    }

    @Test
    fun saveSelectedProviderTrimsValueAndRemovesBlankValue() = runTest {
        val repository = repository(fileName = "saved.preferences_pb", scope = backgroundScope)

        repository.saveSelectedProvider(" provider-1 ")

        val saved = repository.observePreferences().first {
            it.providerId == "provider-1"
        }
        assertEquals("provider-1", saved.providerId)

        repository.saveSelectedProvider(" ")

        val cleared = repository.observePreferences().first {
            it.providerId == null
        }
        assertEquals(null, cleared.providerId)
    }

    @Test
    fun migratesLegacySharedPreferences() = runTest {
        legacyPreferences.edit()
            .putString("provider_id", "legacy-provider")
            .commit()
        val repository = repository(
            fileName = "migrated.preferences_pb",
            scope = backgroundScope,
            migrateLegacyPreferences = true,
        )

        val migrated = repository.observePreferences().first {
            it.providerId == "legacy-provider"
        }

        assertEquals("legacy-provider", migrated.providerId)
    }

    private fun repository(
        fileName: String,
        scope: CoroutineScope,
        migrateLegacyPreferences: Boolean = false,
    ): DataStoreImageGenerationPreferencesRepository {
        val file = File(temporaryFolder.root, fileName)
        val dataStore = PreferenceDataStoreFactory.create(
            migrations = if (migrateLegacyPreferences) {
                listOf(SharedPreferencesMigration(context, PREFERENCES_NAME))
            } else {
                emptyList()
            },
            scope = scope,
            produceFile = { file },
        )
        return DataStoreImageGenerationPreferencesRepository(
            dataStore = dataStore,
            scope = scope,
        )
    }
}

private const val PREFERENCES_NAME = "image_generation_preferences"
