package number.ninja.data.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import number.ninja.domain.AppLanguage

/**
 * The single source of truth for the app-language override.
 *
 * AppCompat owns the preference so it stays synchronized with Android 13+'s per-app language
 * settings and can restore it before Activity content is created. DataStore is consulted only
 * once to hand off the language written by older app versions.
 */
class AppLanguageManager(
    private val dataStore: DataStore<Preferences>,
    private val applicationLanguageTags: () -> String = {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    },
    private val applyApplicationLanguageTags: (String) -> Unit = { languageTags ->
        val locales = if (languageTags.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTags)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    },
    private val shouldMigrateLegacyLanguage: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    },
) {
    private val _selectedLanguage = MutableStateFlow(readSelectedLanguage())

    /**
     * Observable mirror of AppCompat's current override; null means resources follow the system.
     * It is not another persisted source of truth and is refreshed whenever Settings resumes.
     */
    val selectedLanguage: StateFlow<AppLanguage?> = _selectedLanguage.asStateFlow()

    /** Passes an empty locale list for the explicit "same as system" option. */
    fun selectLanguage(language: AppLanguage?) {
        val targetTags = language?.tag.orEmpty()
        // Update first: changing explicit "uk" to system on a Ukrainian device may leave the
        // effective resource configuration unchanged, so Android need not recreate the Activity.
        _selectedLanguage.value = language
        if (applicationLanguageTags() != targetTags) {
            applyApplicationLanguageTags(targetTags)
        }
    }

    /** Re-reads AppCompat after returning from Android's own per-app language settings. */
    fun refreshSelectedLanguage() {
        _selectedLanguage.value = readSelectedLanguage()
    }

    /**
     * One-time handoff from the custom DataStore preference used by version 1.
     *
     * If Android/AppCompat already has an override (for example one chosen in Android's App
     * Languages settings), that newer source wins and is never overwritten by stale app data. On
     * Android 13+ even an empty framework locale list is authoritative: it can represent an
     * explicit "same as system" choice, so a stale legacy value must not replace it.
     *
     * A valid legacy value is applied before its key is removed. If locale application recreates
     * the Activity and cancels this coroutine before cleanup, the next launch sees the now-active
     * AppCompat locale, skips re-applying it, and safely removes the leftover key.
     */
    suspend fun migrateLegacyLanguage(): AppLanguage? {
        val legacyTag = dataStore.data.first()[LEGACY_LANGUAGE] ?: return null
        val legacyLanguage = AppLanguage.fromLanguageTags(legacyTag)
            ?.takeIf { shouldMigrateLegacyLanguage() && applicationLanguageTags().isEmpty() }

        if (legacyLanguage != null) selectLanguage(legacyLanguage)
        dataStore.edit { preferences -> preferences.remove(LEGACY_LANGUAGE) }
        return legacyLanguage
    }

    private fun readSelectedLanguage(): AppLanguage? =
        AppLanguage.fromLanguageTags(applicationLanguageTags())

    private companion object {
        val LEGACY_LANGUAGE = stringPreferencesKey("language")
    }
}
