package number.ninja.ui.firstrun

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import number.ninja.R
import number.ninja.ui.components.BigActionButton
import number.ninja.ui.settings.SettingsContent
import number.ninja.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * First-run screen. Per the "first run should just be the Settings screen" fix, this is no
 * longer a hand-rolled Operations/Level/Mode-only picker with its own batching ViewModel —
 * it reuses [SettingsContent] (the exact same sections Settings shows, including quiz
 * style/length/language, so first-run users can configure everything Settings can) and
 * [SettingsViewModel] directly, since every control there already persists per-interaction.
 * The only things unique to first run are the title [header], the "Let's go!" CTA [footer]
 * (which flips `hasCompletedFirstRun` and navigates on), and hiding the reset-to-defaults
 * button (nothing to reset yet on a screen the user hasn't left once).
 */
@Composable
fun QuickSetupScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        settings?.let { current ->
            SettingsContent(
                current = current,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                showResetButton = false,
                header = {
                    Text(
                        text = stringResource(R.string.quick_setup_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                footer = {
                    BigActionButton(
                        text = stringResource(R.string.quick_setup_done),
                        // markFirstRunComplete awaits the DataStore write, then calls onDone
                        // itself — onDone pops this screen (and this ViewModel) off the back
                        // stack, so calling it before the write lands would risk the write being
                        // cancelled mid-flight along with viewModelScope.
                        onClick = { viewModel.markFirstRunComplete(onDone) },
                    )
                },
            )
        }
    }
}
