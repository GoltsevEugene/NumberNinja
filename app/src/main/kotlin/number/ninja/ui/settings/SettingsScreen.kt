package number.ninja.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import number.ninja.R
import number.ninja.domain.AppLanguage
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings
import number.ninja.ui.components.AdaptiveCenteredColumn
import number.ninja.ui.labelRes
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.net.toUri

private const val PRIVACY_POLICY_URL = "https://goltseveugene.github.io/number-ninja-privacy/"

/**
 * Full settings screen (reached from Home's settings icon). Every control commits its
 * own [SettingsViewModel] update immediately — there is no confirm/"Save" step, unlike
 * first-run quick setup which batches picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        settings?.let { current ->
            SettingsContent(
                current = current,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                footer = {
                    PrivacyPolicyButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()),
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun PrivacyPolicyButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_privacy_policy))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Shared body used by both [SettingsScreen] and [number.ninja.ui.firstrun.QuickSetupScreen] —
 * per the "first run should just be the Settings screen" fix, first run is no longer a
 * hand-rolled Operations/Level/Mode-only picker; it reuses this exact content (including the
 * quiz-style/quiz-length/language sections Settings has) and differs only in [header]/[footer]
 * and [showResetButton] (first run has nothing to reset yet).
 */
@Composable
fun SettingsContent(
    current: UserSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    showResetButton: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    AdaptiveCenteredColumn(
        modifier = modifier,
        innerModifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.invoke(this)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_operations_label),
                style = MaterialTheme.typography.titleMedium,
            )
            OperationToggles(
                selected = current.operations,
                onToggle = viewModel::toggleOperation,
            )
        }

        // Level only means something for Free Practice, or for a Quiz explicitly pinned
        // to one level (FIXED_LEVEL) — Progression spans all levels itself and Shuffle
        // picks one per example, so Level is meaningless (and was previously shown but
        // silently ignored) in both those quiz sub-modes.
        val levelApplicable = current.mode == TrainingMode.FREE_PRACTICE ||
            (current.mode == TrainingMode.QUIZ && current.quizSubMode == QuizSubMode.FIXED_LEVEL)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_level_label),
                style = MaterialTheme.typography.titleMedium,
            )
            if (levelApplicable) {
                LevelChips(
                    selected = current.level,
                    onSelect = viewModel::selectLevel,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_level_not_applicable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_mode_label),
                style = MaterialTheme.typography.titleMedium,
            )
            ModeOptions(
                selected = current.mode,
                onSelect = viewModel::selectMode,
            )
        }

        if (current.mode == TrainingMode.QUIZ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_quiz_sub_mode_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                QuizSubModeOptions(
                    selected = current.quizSubMode,
                    onSelect = viewModel::selectQuizSubMode,
                )
            }

            QuizLengthSlider(
                quizLength = current.quizLength,
                onQuizLengthChange = viewModel::setQuizLength,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_language_label),
                style = MaterialTheme.typography.titleMedium,
            )
            LanguageSegmentedControl(
                // current.language == null means "no explicit override — follow system". For
                // display purposes only (never persisted just from viewing this screen), resolve
                // which of the 3 supported languages is actually active so a chip always shows
                // as selected instead of none — see resolveActiveLanguage().
                selected = current.language ?: resolveActiveLanguage(),
                onSelect = { language ->
                    // Persist only — NumberNinjaNavHost's LaunchedEffect(current.language)
                    // is the single owner of AppCompatDelegate.setApplicationLocales()
                    // now. Calling it here too would apply the same change twice
                    // (once here, once when the DataStore emission propagates),
                    // which visibly flickers via a double Activity-recreate on API < 33.
                    viewModel.selectLanguage(language)
                },
            )
        }

        if (showResetButton) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Extra separation (divider + error-tinted outline button, not just spacing)
                // from the controls above — a reset is itself an easy-to-regret accidental tap,
                // same concern the user raised about the rest of this screen.
                HorizontalDivider()
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_reset_button))
                }
            }
        }

        footer?.invoke(this)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            },
        )
    }
}

/**
 * Which [AppLanguage] is actually active right now, for display when [UserSettings.language]
 * is null ("follow system") — checks [AppCompatDelegate.getApplicationLocales] first (the
 * app-level override, e.g. set via the OS's per-app language screen) and falls back to
 * [Locale.getDefault] when that's empty. Falls back to [AppLanguage.ENGLISH] if the active
 * locale's language tag matches none of the 3 supported languages (e.g. device is in French) —
 * matching the app's actual resource-fallback behavior. Purely a display computation: never
 * writes anything, so viewing this screen never turns a null/"system" setting into an explicit
 * persisted choice.
 */
private fun resolveActiveLanguage(): AppLanguage {
    val applicationLocales = AppCompatDelegate.getApplicationLocales()
    val activeLocale = if (!applicationLocales.isEmpty) applicationLocales[0] else Locale.getDefault()
    return AppLanguage.entries.find { it.tag == activeLocale?.language } ?: AppLanguage.ENGLISH
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationToggles(selected: Set<Operation>, onToggle: (Operation) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Operation.entries.forEach { operation ->
            FilterChip(
                selected = operation in selected,
                onClick = { onToggle(operation) },
                label = { Text(stringResource(operation.labelRes())) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(selected: Level, onSelect: (Level) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Level.entries.forEach { level ->
            FilterChip(
                selected = level == selected,
                onClick = { onSelect(level) },
                label = { Text(stringResource(level.labelRes())) },
            )
        }
    }
}

/**
 * Vertical list of selectable rows (RadioButton + label), matching [QuizSubModeOptions]'s
 * pattern rather than a [SingleChoiceSegmentedButtonRow] like [LanguageSegmentedControl] —
 * with the checkmark added, "Свободная практика" (the longest Mode label, Russian Free
 * Practice) doesn't comfortably fit a 2-wide segmented cell (confirmed via on-device
 * screenshot). Do not shrink text/icon to fit; use this control type instead, same
 * reasoning as the earlier Quiz-style fix.
 */
@Composable
private fun ModeOptions(selected: TrainingMode, onSelect: (TrainingMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrainingMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == selected, onClick = null)
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Vertical list of selectable rows (RadioButton + label), not a [SingleChoiceSegmentedButtonRow]
 * like [LanguageSegmentedControl] — with 3 options, some of them long (e.g. Russian
 * "Прогрессия сложности"/"Выбранный уровень"), a segmented row has no room to avoid text
 * wrapping/clipping. Stacking vertically gives every label its own full-width line.
 */
@Composable
private fun QuizSubModeOptions(selected: QuizSubMode, onSelect: (QuizSubMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuizSubMode.entries.forEach { subMode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = subMode == selected,
                        onClick = { onSelect(subMode) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = subMode == selected, onClick = null)
                Text(
                    text = stringResource(subMode.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LanguageSegmentedControl(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AppLanguage.entries.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == selected,
                onClick = { onSelect(language) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppLanguage.entries.size),
                label = { Text(stringResource(language.labelRes())) },
            )
        }
    }
}

/**
 * Discrete slider over [UserSettings.MIN_QUIZ_LENGTH]..[UserSettings.MAX_QUIZ_LENGTH].
 * Drag feedback updates local state (and the live label) immediately;
 * [onQuizLengthChange] (the DataStore write) fires once on drag release, not per-tick.
 */
@Composable
private fun QuizLengthSlider(quizLength: Int, onQuizLengthChange: (Int) -> Unit) {
    val min = UserSettings.MIN_QUIZ_LENGTH
    val max = UserSettings.MAX_QUIZ_LENGTH
    var sliderPosition by remember(quizLength) { mutableFloatStateOf(quizLength.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_quiz_length_label, sliderPosition.roundToInt()),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onQuizLengthChange(sliderPosition.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
