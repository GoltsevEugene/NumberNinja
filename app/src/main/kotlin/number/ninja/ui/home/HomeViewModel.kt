package number.ninja.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import number.ninja.data.settings.SettingsRepository
import number.ninja.domain.UserSettings

/**
 * Backs the Home screen. Exposes [settings] as `UserSettings?` — null until the first
 * DataStore emission arrives, so the UI (and the NavHost's start-destination choice)
 * never has to guess a default before real persisted settings are known.
 */
class HomeViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<UserSettings?> = settingsRepository.settings
}
