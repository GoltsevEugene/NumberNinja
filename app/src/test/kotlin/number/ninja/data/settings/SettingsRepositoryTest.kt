package number.ninja.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import number.ninja.domain.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @Test
    fun `concurrent updates of different fields preserve both changes`() = runTest {
        val dataStore = CoordinatedPreferencesDataStore()
        val repository = SettingsRepository(dataStore, backgroundScope)

        listOf(
            async { repository.update { it.copy(level = Level.HARD) } },
            async { repository.update { it.copy(quizLength = 15) } },
        ).awaitAll()

        dataStore.stopCoordinatingReads()
        val settings = repository.awaitSettings()
        assertEquals(Level.HARD, settings.level)
        assertEquals(15, settings.quizLength)
    }

    @Test
    fun `loaded settings remain cached for a recreated UI`() = runTest {
        val dataStore = CoordinatedPreferencesDataStore().apply { stopCoordinatingReads() }
        val repository = SettingsRepository(dataStore, backgroundScope)
        repository.update { it.copy(level = Level.STAR, quizLength = 18) }

        val firstScreen = repository.settings.filterNotNull().first()

        assertEquals(firstScreen, repository.settings.value)
        // A second collector represents a newly recreated Activity and must not see null first.
        assertEquals(firstScreen, repository.settings.filterNotNull().first())
        assertEquals(Level.STAR, firstScreen.level)
        assertEquals(18, firstScreen.quizLength)
    }

    @Test
    fun `initial read failure stays unloaded then retries until persisted settings load`() = runTest {
        val dataStore = RecoveringReadDataStore(
            firstSnapshot = null,
            recoveredSnapshot = preferencesWithLevel(Level.STAR),
        )
        val repository = SettingsRepository(dataStore, backgroundScope)
        val firstNonNull = async { repository.settings.filterNotNull().first() }

        runCurrent()

        assertFalse(firstNonNull.isCompleted)
        assertNull(repository.settings.value)
        assertEquals(1, dataStore.readAttempts.get())

        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, dataStore.readAttempts.get())

        dataStore.allowRecovery.complete(Unit)
        runCurrent()

        assertEquals(Level.STAR, firstNonNull.await().level)
        assertEquals(Level.STAR, repository.settings.value?.level)
        assertEquals(2, dataStore.readAttempts.get())
    }

    @Test
    fun `post emission read failure keeps last settings while retrying then recovers`() = runTest {
        val dataStore = RecoveringReadDataStore(
            firstSnapshot = preferencesWithLevel(Level.HARD),
            recoveredSnapshot = preferencesWithLevel(Level.STAR),
        )
        val repository = SettingsRepository(dataStore, backgroundScope)
        val firstSnapshot = async { repository.settings.filterNotNull().first() }

        runCurrent()

        assertEquals(Level.HARD, firstSnapshot.await().level)
        assertEquals(Level.HARD, repository.settings.value?.level)
        assertEquals(1, dataStore.readAttempts.get())

        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, dataStore.readAttempts.get())

        dataStore.allowRecovery.complete(Unit)
        runCurrent()

        assertEquals(Level.STAR, repository.settings.value?.level)
        assertEquals(2, dataStore.readAttempts.get())
    }

    @Test
    fun `await settings retries a transient read failure without returning guessed defaults`() = runTest {
        val dataStore = RecoveringReadDataStore(
            firstSnapshot = null,
            recoveredSnapshot = preferencesWithLevel(Level.STAR),
        )
        val repository = SettingsRepository(dataStore, backgroundScope)
        val settings = async { repository.awaitSettings() }

        runCurrent()

        assertFalse(settings.isCompleted)
        assertEquals(1, dataStore.readAttempts.get())

        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, dataStore.readAttempts.get())

        dataStore.allowRecovery.complete(Unit)
        runCurrent()

        assertEquals(Level.STAR, settings.await().level)
    }

    @Test
    fun `await settings falls back to confirmed cache after bounded read retries`() = runTest {
        val dataStore = CachedThenFailingReadDataStore(preferencesWithLevel(Level.HARD))
        val repository = SettingsRepository(dataStore, backgroundScope)

        assertEquals(Level.HARD, repository.settings.filterNotNull().first().level)
        val settings = async { repository.awaitSettings() }
        runCurrent()

        assertFalse(settings.isCompleted)
        advanceTimeBy(250)
        runCurrent()
        assertFalse(settings.isCompleted)
        advanceTimeBy(500)
        runCurrent()

        assertEquals(Level.HARD, settings.await().level)
        assertEquals(4, dataStore.readAttempts.get())
    }

    @Test
    fun `await settings surfaces a persistent read failure when no cache exists`() = runTest {
        val dataStore = FailingReadDataStore(IOException("Persistent settings read failure"))
        val repository = SettingsRepository(dataStore, backgroundScope)
        val result = async { runCatching { repository.awaitSettings() } }

        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is IOException)
        assertEquals(3, dataStore.readAttempts.get())
        assertNull(repository.settings.value)
    }

    @Test
    fun `await settings surfaces unresolved corruption without retrying forever`() = runTest {
        val dataStore = FailingReadDataStore(CorruptionException("Broken preferences"))
        val repository = SettingsRepository(dataStore, backgroundScope)

        val failure = try {
            repository.awaitSettings()
            null
        } catch (error: Throwable) {
            error
        }

        assertInstanceOf(CorruptionException::class.java, failure)
        assertEquals(1, dataStore.readAttempts.get())
    }

    @Test
    fun `await settings reads latest DataStore snapshot instead of a stale UI cache`() = runTest {
        val dataStore = CoordinatedPreferencesDataStore().apply { stopCoordinatingReads() }
        val repository = SettingsRepository(dataStore, backgroundScope)

        assertEquals(Level.EASY, repository.settings.filterNotNull().first().level)
        repository.update { it.copy(level = Level.STAR) }

        // This fake's process-cache collection has completed with the initial snapshot. A direct
        // DataStore collection must still observe the durably updated value, just like production
        // DataStore's `data.first()` contract guarantees after `updateData` completes.
        assertEquals(Level.EASY, repository.settings.value?.level)
        assertEquals(Level.STAR, repository.awaitSettings().level)
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

    /**
     * The first collection optionally emits a real value and then fails. Retrying starts a second
     * collection which waits until the test permits it to emit the recovered value.
     */
    private class RecoveringReadDataStore(
        private val firstSnapshot: Preferences?,
        private val recoveredSnapshot: Preferences,
    ) : DataStore<Preferences> {
        val readAttempts = AtomicInteger(0)
        val allowRecovery = CompletableDeferred<Unit>()

        override val data: Flow<Preferences> = flow {
            when (readAttempts.incrementAndGet()) {
                1 -> {
                    firstSnapshot?.let { emit(it) }
                    throw IOException("Temporary settings read failure")
                }

                else -> {
                    allowRecovery.await()
                    emit(recoveredSnapshot)
                }
            }
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = error("Updates are not used by this test double")
    }

    private class FailingReadDataStore(
        private val failure: Throwable,
    ) : DataStore<Preferences> {
        val readAttempts = AtomicInteger(0)

        override val data: Flow<Preferences> = flow {
            readAttempts.incrementAndGet()
            throw failure
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = error("Updates are not used by this test double")
    }

    private class CachedThenFailingReadDataStore(
        private val cachedSnapshot: Preferences,
    ) : DataStore<Preferences> {
        val readAttempts = AtomicInteger(0)

        override val data: Flow<Preferences> = flow {
            if (readAttempts.incrementAndGet() == 1) {
                emit(cachedSnapshot)
            } else {
                throw IOException("Persistent settings read failure")
            }
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = error("Updates are not used by this test double")
    }

    private fun preferencesWithLevel(level: Level): Preferences =
        preferencesOf(stringPreferencesKey("level") to level.name)
}
