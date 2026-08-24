package number.ninja.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import number.ninja.data.settings.SettingsRepository
import number.ninja.domain.TrainingMode
import number.ninja.ui.practice.FreePracticeScreen
import number.ninja.ui.quiz.QuizScreen
import org.koin.compose.koinInject

/**
 * Routes Home's Start button to the right training-mode screen, based on the current
 * [TrainingMode]. Reads [SettingsRepository] directly (like [number.ninja.ui.nav.NumberNinjaNavHost]
 * does for its own startup gating) rather than through a dedicated ViewModel — this composable
 * has no state or business logic of its own, it's a pure router.
 *
 * [onQuizFinished] is only meaningful for the QUIZ branch (fired once the quiz's fixed example
 * list is exhausted, to navigate to the results screen) — free practice has no end state and
 * never calls it. Kept as a required param rather than optional/nullable so
 * [number.ninja.ui.nav.NumberNinjaNavHost] can't forget to wire it.
 */
@Composable
fun SessionScreen(
    onExit: () -> Unit,
    onQuizFinished: () -> Unit,
    settingsRepository: SettingsRepository = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()

    when (settings?.mode) {
        TrainingMode.FREE_PRACTICE -> FreePracticeScreen(onExit = onExit)
        TrainingMode.QUIZ -> QuizScreen(onExit = onExit, onFinished = onQuizFinished)
        null -> Unit // first settings snapshot not loaded yet
    }
}
