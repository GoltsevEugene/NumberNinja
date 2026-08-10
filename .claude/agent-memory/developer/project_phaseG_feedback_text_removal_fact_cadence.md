---
name: project_phaseG_feedback_text_removal_fact_cadence
description: Phase G — removed the layout-jumping feedback Text from FreePractice/Quiz answer screens, changed fact cadence 5→4
metadata:
  type: project
---

Phase G landed on 2026-08-09 (fix batch item 4 + item 6). Two independent changes to
`ui/practice/FreePracticeScreen.kt`/`FreePracticeViewModel.kt` and
`ui/quiz/QuizScreen.kt`/`QuizViewModel.kt`.

**Item 4 — no more feedback text line:** User feedback (translated): the "Correct!"/"Почти —
правильный ответ: N" line between the question and the `AnswerOptionsGrid` pushed the grid down
after answering, reading as a layout jump. Fix: deleted that `Text` composable entirely from both
screens' `FeedbackContent`, so `FeedbackContent`'s `Column` is now structurally IDENTICAL to
`QuestionContent`'s (same children, same `spacedBy(24.dp)`) — only `AnswerOptionsGrid`'s own
green/red highlight communicates the result now, no text at all. Verified on-device: screenshotted
before/after a tap for both a correct and a wrong answer in Quiz mode and a wrong answer in Free
Practice mode — question text and grid position never move, only card colors change.

**How to apply:** If a future change wants to reintroduce any answered-state text (e.g. a streak
counter, a "1/12" reminder) between the question and the grid, it MUST also appear in the
unanswered `QuestionContent` state (even if blank/placeholder) so the two states stay
layout-identical — don't let one state gain a row the other lacks.

**Dead-field cleanup that came with it:** `FreePracticeUiState.Feedback.correct` and
`QuizUiState.Feedback.correct` (`Boolean`) were ONLY read by the now-deleted text's
if/else — removed both fields and their constructor args in `submitAnswer` (local `correct: Boolean`
var is still used for `statsRepository.recordAttempt` and to build `QuizAttempt`, just not for the
`Feedback` state anymore). If you see `Feedback.correct` referenced anywhere old, that code predates
this phase.

**Left untouched (deliberately out of scope):** `free_practice_correct`/`free_practice_incorrect`/
`quiz_correct`/`quiz_incorrect` string resources (with the `%1$d` correct-answer placeholder) are
now fully unused in Kotlin but still in `strings.xml` — same "don't clean up unused resources
without being asked" precedent as [[project_phaseB_mcq_answering]]'s Check/Next button strings.

**Item 6 — fact cadence 5→4:** Both `FreePracticeViewModel.FACT_EVERY` and
`QuizViewModel.FACT_EVERY` companion consts changed from `5` to `4`. Quiz mode DOES have its own
fact-interval logic (not vestigial) — `attempts.size % FACT_EVERY == 0` in
`QuizViewModel.onFeedbackDismissed()`, same mechanic as free practice, and `quiz_fact_label` is
actively used by `QuizScreen.FactContent`. Verified on-device: quiz's "Did you know?" card appeared
right after the 4th answered question (previously would've been the 5th), and dismissing it
advanced cleanly into question 5 with no crash. The `nextIndex >= questions.size -> finish()`
branch in `QuizViewModel.onFeedbackDismissed()`'s `when` always runs before the `FACT_EVERY` check,
so a fact card can never collide with the last example regardless of `FACT_EVERY`'s value or
`quizLength` — updated the stale doc comment that used to justify this via "both multiples of 5."
