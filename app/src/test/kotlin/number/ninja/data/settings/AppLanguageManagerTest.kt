package number.ninja.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import number.ninja.domain.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AppLanguageManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `system selection applies an empty locale list`() = runTest {
        var applicationTags = "ru"
        val manager = manager(
            testScope = this,
            readTags = { applicationTags },
            applyTags = { applicationTags = it },
        )

        manager.selectLanguage(null)

        assertEquals("", applicationTags)
        assertNull(manager.selectedLanguage.value)
    }

    @Test
    fun `refresh mirrors a language changed in Android settings`() = runTest {
        var applicationTags = "ru"
        val manager = manager(
            testScope = this,
            readTags = { applicationTags },
            applyTags = { applicationTags = it },
        )

        applicationTags = "uk-UA"
        manager.refreshSelectedLanguage()

        assertEquals(AppLanguage.UKRAINIAN, manager.selectedLanguage.value)
    }

    @Test
    fun `legacy DataStore language is handed off once when no platform override exists`() = runTest {
        val dataStore = dataStore(this, "legacy-handoff")
        dataStore.edit { it[stringPreferencesKey("language")] = "uk" }
        var applicationTags = ""
        val manager = AppLanguageManager(
            dataStore = dataStore,
            applicationScope = backgroundScope,
            applicationLanguageTags = { applicationTags },
            applyApplicationLanguageTags = { applicationTags = it },
        )

        assertFalse(manager.legacyMigrationComplete.value)
        assertEquals(AppLanguage.UKRAINIAN, manager.migrateLegacyLanguage())
        assertEquals("uk", applicationTags)
        assertNull(dataStore.data.first()[stringPreferencesKey("language")])
        assertTrue(manager.legacyMigrationComplete.value)
        assertNull(manager.migrateLegacyLanguage())
    }

    @Test
    fun `migration readiness remains cached when no legacy language exists`() = runTest {
        val manager = manager(
            testScope = this,
            readTags = { "ru" },
            applyTags = { error("No locale should be applied") },
        )

        assertFalse(manager.legacyMigrationComplete.value)
        assertNull(manager.migrateLegacyLanguage())
        assertTrue(manager.legacyMigrationComplete.value)

        // Simulates a newly recreated Activity asking the same process-scoped manager again.
        assertNull(manager.migrateLegacyLanguage())
        assertTrue(manager.legacyMigrationComplete.value)
    }

    @Test
    fun `starting migration twice launches one process scoped handoff`() = runTest {
        val dataStore = dataStore(this, "process-scoped-handoff")
        dataStore.edit { it[stringPreferencesKey("language")] = "uk" }
        var applicationTags = ""
        var applyCount = 0
        val manager = AppLanguageManager(
            dataStore = dataStore,
            applicationScope = backgroundScope,
            applicationLanguageTags = { applicationTags },
            applyApplicationLanguageTags = {
                applyCount++
                applicationTags = it
            },
        )

        manager.startLegacyMigration()
        manager.startLegacyMigration()
        manager.legacyMigrationComplete.first { it }

        assertEquals(1, applyCount)
        assertEquals("uk", applicationTags)
        assertNull(dataStore.data.first()[stringPreferencesKey("language")])
        assertTrue(manager.legacyMigrationComplete.value)
    }

    @Test
    fun `existing platform language wins over stale legacy DataStore value`() = runTest {
        val dataStore = dataStore(this, "platform-wins")
        dataStore.edit { it[stringPreferencesKey("language")] = "uk" }
        var applicationTags = "ru"
        val manager = AppLanguageManager(
            dataStore = dataStore,
            applicationScope = backgroundScope,
            applicationLanguageTags = { applicationTags },
            applyApplicationLanguageTags = { applicationTags = it },
        )

        assertNull(manager.migrateLegacyLanguage())
        assertEquals("ru", applicationTags)
        assertNull(dataStore.data.first()[stringPreferencesKey("language")])
        assertNull(manager.migrateLegacyLanguage())
    }

    @Test
    fun `empty Android 13 platform locale wins over stale legacy value`() = runTest {
        val dataStore = dataStore(this, "platform-system-wins")
        dataStore.edit { it[stringPreferencesKey("language")] = "uk" }
        var applicationTags = ""
        val manager = AppLanguageManager(
            dataStore = dataStore,
            applicationScope = backgroundScope,
            applicationLanguageTags = { applicationTags },
            applyApplicationLanguageTags = { applicationTags = it },
            shouldMigrateLegacyLanguage = { false },
        )

        assertNull(manager.migrateLegacyLanguage())
        assertEquals("", applicationTags)
        assertNull(dataStore.data.first()[stringPreferencesKey("language")])
        assertNull(manager.migrateLegacyLanguage())
    }

    private fun manager(
        testScope: TestScope,
        readTags: () -> String,
        applyTags: (String) -> Unit,
    ) = AppLanguageManager(
        dataStore = dataStore(testScope, "manager"),
        applicationScope = testScope.backgroundScope,
        applicationLanguageTags = readTags,
        applyApplicationLanguageTags = applyTags,
    )

    private fun dataStore(testScope: TestScope, name: String) =
        PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tempDir.resolve("$name.preferences_pb").toFile() },
        )
}
