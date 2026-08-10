package number.ninja.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private const val SETTINGS_DATASTORE_NAME = "number_ninja_settings"

val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)
