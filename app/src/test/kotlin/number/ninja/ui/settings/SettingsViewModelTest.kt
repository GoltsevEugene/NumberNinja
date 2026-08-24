package number.ninja.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import number.ninja.data.settings.AppLanguageManager
import number.ninja.data.settings.SettingsRepository
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.ThemeMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reset preserves first run and resets settings before selecting system language`() =
        runTest(testDispatcher) {
            val dataStore = RecordingPreferencesDataStore()
            val repository = SettingsRepository(dataStore, backgroundScope)
            val nonDefaultSettings = UserSettings(
                operations = setOf(Operation.MULTIPLICATION, Operation.DIVISION),
                level = Level.STAR,
                mode = TrainingMode.FREE_PRACTICE,
                quizSubMode = QuizSubMode.FIXED_LEVEL,
                quizLength = UserSettings.MAX_QUIZ_LENGTH,
                theme = ThemeMode.DARK,
                hasCompletedFirstRun = true,
            )
            repository.update { nonDefaultSettings }
            dataStore.clearUpdateCompletedMarker()

            var applicationLanguageTags = "uk"
            var settingsWerePersistedWhenSystemLanguageWasSelected = false
            val languageManager = AppLanguageManager(
                dataStore = dataStore,
                applicationScope = backgroundScope,
                applicationLanguageTags = { applicationLanguageTags },
                applyApplicationLanguageTags = { tags ->
                    settingsWerePersistedWhenSystemLanguageWasSelected =
                        dataStore.lastUpdateCompleted
                    applicationLanguageTags = tags
                },
            )
            val viewModel = SettingsViewModel(repository, languageManager)

            viewModel.resetToDefaults()
            advanceUntilIdle()

            assertEquals(
                UserSettings(hasCompletedFirstRun = true),
                repository.awaitSettings(),
            )
            assertEquals("", applicationLanguageTags)
            assertNull(viewModel.selectedLanguage.value)
            assertTrue(settingsWerePersistedWhenSystemLanguageWasSelected)
        }

    private class RecordingPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        @Volatile
        var lastUpdateCompleted: Boolean = false
            private set

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            mutex.lock()
            lastUpdateCompleted = false
            return try {
                transform(state.value).also {
                    state.value = it
                    lastUpdateCompleted = true
                }
            } finally {
                mutex.unlock()
            }
        }

        fun clearUpdateCompletedMarker() {
            lastUpdateCompleted = false
        }
    }
}
