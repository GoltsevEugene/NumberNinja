---
name: project_phaseB_mcq_answering
description: Phase B of the 4-phase fix batch — replaced manual numeric entry with 4-option MCQ in free practice + quiz
metadata:
  type: project
---

Phase B landed on 2026-08-08: `FreePracticeScreen`/`FreePracticeViewModel` and
`QuizScreen`/`QuizViewModel` no longer use an `OutlinedTextField` + "Check" button — answering is
now tap-one-of-4-options, using `domain.distractors()` from Phase A
([[project_phaseA_domain_fixes]]).

**Why:** Explicit user feedback (translated from Ukrainian): remove manual entry, show 4 options,
a tap *is* the answer immediately (no Check step), tapped option highlights green/red instantly,
wrong tap also reveals the correct option in green, highlight holds 3s, then auto-advances —
including straight to results after the last quiz question, no "See results" tap. The `Fact`
interstitial (every-5th-example card) is explicitly unaffected — still a manual "Continue" button,
not auto-advancing.

**How to apply / key decisions for future work touching these screens:**

- **The 4-choice `options: List<Int>` is computed once in the ViewModel, stored on both the
  `Question` and `Feedback` sealed-state variants, not shuffled inside a composable.** Both
  `FreePracticeUiState`/`QuizUiState`'s `Question` and `Feedback` carry the same `options` list.
  Reason: `Crossfade` puts `QuestionContent` and `FeedbackContent` in two *different* composables,
  so a `remember(example) { options.shuffled() }` inside one can't be shared with the other —
  the grid would reshuffle (or at minimum couldn't be trusted to match) across the state
  transition. Compute `(listOf(example.answer()) + distractors(example)).shuffled()` once,
  wherever the example itself is generated (`FreePracticeViewModel.nextQuestion()`,
  `QuizViewModel.generateQuizQuestions()` via the new private `QuizQuestion(example, options)`
  holder replacing the old `List<MathExample>`).
- **`Feedback` states gained a `selectedAnswer: Int` field** (previously only `correct`/
  `correctAnswer`) — needed so the Feedback-state grid render knows which tapped option to mark
  red. Don't drop this field if refactoring these states again.
- **New shared composable: `ui/components/AnswerOptionsGrid.kt`** — public
  `AnswerOptionsGrid(options: List<Int>, selected: Int?, correctAnswer: Int, onSelect: (Int) ->
  Unit, modifier)`. 2x2 grid (`chunked(2)` + `Row`/`Column`, no `LazyGrid` — only 4 items).
  `selected == null` → all neutral and tappable; `selected != null` → grid becomes non-interactive
  (each `Card`'s own `enabled` flips false) and highlights: tapped-correct → green
  (`successContainerColor()`), tapped-wrong → tapped option red (`errorContainer`) AND
  `correctAnswer` simultaneously green. Callers pass `onSelect = {}` for the Feedback-state render
  (already non-interactive via `selected != null`, but kept explicit/inert rather than nullable).
- **New semantic "success" color tokens added to `ui/theme/Color.kt`/`Theme.kt`**: `md_light/
  dark_success`, `..._onSuccess`, `..._successContainer`, `..._onSuccessContainer`, exposed via
  `@Composable fun successColor()/successContainerColor()/onSuccessContainerColor()` in
  `Theme.kt` (mirrors `isSystemInDarkTheme()` switching itself, like `MaterialTheme.colorScheme`
  does, so callers don't branch on dark mode themselves). Reason: M3's default `ColorScheme` has
  no "success"/green slot, and `tertiary` was already spoken for as the grape "facts/rewards"
  role (see `Color.kt`'s existing top comment) — reusing it for "correct answer" would overload
  it with two unrelated meanings. The wrong-answer highlight reuses the *existing*
  `MaterialTheme.colorScheme.error`/`errorContainer` (no new red tokens needed).
- **This is a deliberate, narrow exception to the app's "never use `colorScheme.error`, no
  punitive feedback" rule** (see the `FreePracticeScreen`/`QuizScreen` doc comments and
  `FeedbackContent`'s inline comment) — that rule governs the *textual* "Correct!"/"Not quite"
  feedback line, which still never uses `error`. The red/green *visual answer-grid highlight* is
  new, explicit user-requested behavior layered on top, not a relaxation of the text rule. Don't
  let a future "no error color" review flag the grid highlight — check this memory first.
- **Auto-advance timer lives in the screen's `Crossfade` `is Feedback ->` branch as
  `LaunchedEffect(state) { delay(3000); viewModel.onFeedbackDismissed() }`**, not inside
  `AnswerOptionsGrid` or the ViewModel. Keyed on `state` (the specific `Feedback` instance) rather
  than `Unit`, though in practice either works here since a `Feedback`-typed branch is never
  recomposed with a materially different `Feedback` instance without first leaving the branch
  entirely (state always progresses to `Question`/`Fact`/`Finished` next). Quiz's version calls
  the same `onFeedbackDismissed()` on the last example too — that's what now drives navigation to
  results, reusing the existing `Finished`-state `LaunchedEffect(Unit) { onFinished() }` one-shot
  pattern already in place from the Quiz phase (see [[architecture_ui_layer]]).
- **The old double-tap-during-Crossfade state guards in both ViewModels' `submitAnswer`/
  `onFeedbackDismissed` were kept as-is**, just re-justified in their doc comments: they're now
  less about a double-tap (grid disables itself after one tap) and more a backstop against the
  timer or a stray recomposition firing the transition twice.
- **`free_practice_check`/`quiz_check`/`free_practice_next`/`quiz_next`/`quiz_see_results`
  strings are now unused** (no Check/Next/See-results buttons left) — deliberately left in
  `strings.xml` per the task's explicit scope (cleanup out of scope), don't "helpfully" delete
  them in a future pass without checking if that's actually wanted.
- **M3 `Card(onClick=...)` gotcha caught via on-device screenshot, not the compiler**: passing
  only `containerColor`/`contentColor` to `CardDefaults.cardColors(...)` and setting
  `enabled = false` renders the *default dimmed disabled* colors, not your custom ones — M3
  silently swaps to `disabledContainerColor`/`disabledContentColor` (which default to a
  low-alpha `surface`/`onSurface`) whenever `enabled = false`, regardless of what
  `containerColor`/`contentColor` say. This is exactly `AnswerOptionsGrid`'s case: every option
  card is `enabled = (selected == null)`, so the moment one is tapped, *all four* go disabled —
  including the two that are supposed to show a vivid green/red highlight. Fix: also pass
  `disabledContainerColor = containerColor, disabledContentColor = contentColor` explicitly.
  Apply this to any future `Card`/`Button`/similar M3 component where a highlighted state is
  deliberately paired with `enabled = false` — check `*Defaults.*Colors()`'s disabled params
  every time, don't assume the enabled-state colors "just carry over."
