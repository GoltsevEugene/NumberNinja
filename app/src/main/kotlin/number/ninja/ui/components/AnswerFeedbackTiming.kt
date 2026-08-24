package number.ninja.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A short pause is enough to acknowledge a correct choice before moving on. */
internal const val CORRECT_ANSWER_FEEDBACK_MILLIS = 1_000L

/** Wrong choices stay visible longer so the learner can compare them with the correct answer. */
internal const val INCORRECT_ANSWER_FEEDBACK_MILLIS = 2_300L

/**
 * Keeps a newly cross-faded answer grid inactive until the transition has finished, preventing
 * the tail end of a rapid tap sequence from selecting an option in the next question.
 */
internal const val ANSWER_OPTIONS_ARM_DELAY_MILLIS = 350L

internal fun answerFeedbackDurationMillis(isCorrect: Boolean): Long =
    if (isCorrect) CORRECT_ANSWER_FEEDBACK_MILLIS else INCORRECT_ANSWER_FEEDBACK_MILLIS

/**
 * Advances after the feedback window only while the hosting screen is resumed.
 *
 * Pausing or backgrounding the Activity cancels the active countdown. If the same feedback is
 * still visible when the Activity resumes, [LifecycleResumeEffect] starts a fresh full window so
 * the learner never returns to an already-skipped answer highlight.
 */
@Composable
internal fun LifecycleAwareFeedbackAutoAdvance(
    feedbackKey: Any,
    isCorrect: Boolean,
    onAdvance: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val latestOnAdvance = rememberUpdatedState(onAdvance)

    LifecycleResumeEffect(feedbackKey, isCorrect) {
        val advanceJob = coroutineScope.launch {
            delay(answerFeedbackDurationMillis(isCorrect))
            latestOnAdvance.value()
        }
        onPauseOrDispose { advanceJob.cancel() }
    }
}
