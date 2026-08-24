package number.ninja.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import number.ninja.domain.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {

    @Test
    fun `concurrent updates of different fields preserve both changes`() = runTest {
        val dataStore = CoordinatedPreferencesDataStore()
        val repository = SettingsRepository(dataStore)

        listOf(
            async { repository.update { it.copy(level = Level.HARD) } },
            async { repository.update { it.copy(quizLength = 15) } },
        ).awaitAll()

        dataStore.stopCoordinatingReads()
        val settings = repository.settings.first()
        assertEquals(Level.HARD, settings.level)
        assertEquals(15, settings.quizLength)
    }

    /**
     * Gives the first two [data] collectors the exact same snapshot. This makes the test fail
     * deterministically if a repository reads outside [updateData] before writing: both callers
     * would derive a whole settings object from stale data and the second write would erase one
     * field. A transactional implementation never needs these external reads and transforms the
     * latest state under [updateData]'s mutex instead.
     */
    private class CoordinatedPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private var value: Preferences = emptyPreferences()
        private val coordinatedReaders = AtomicInteger(0)
        private val bothReadersReady = CompletableDeferred<Unit>()

        @Volatile
        private var coordinateReads = true

        override val data: Flow<Preferences> = flow {
            val snapshot = value
            if (coordinateReads) {
                if (coordinatedReaders.incrementAndGet() == 2) bothReadersReady.complete(Unit)
                bothReadersReady.await()
            }
            emit(if (coordinateReads) snapshot else value)
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            mutex.lock()
            return try {
                transform(value).also { value = it }
            } finally {
                mutex.unlock()
            }
        }

        fun stopCoordinatingReads() {
            coordinateReads = false
        }
    }
}
