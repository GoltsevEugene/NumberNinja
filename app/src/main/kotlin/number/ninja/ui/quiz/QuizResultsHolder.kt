package number.ninja.ui.quiz

/**
 * Carries a finished quiz's attempts from [QuizViewModel] to [number.ninja.ui.quiz.QuizResultsScreen]
 * across a navigation hop. Registered as a Koin `single` (not passed via a `@Serializable` route
 * argument) because [QuizAttempt] wraps [number.ninja.domain.MathExample], which is not
 * `@Serializable` (it contains `IntRange` and a sealed-class `UnknownSlot`, which would need
 * polymorphic serialization support this project has deliberately avoided so far — see
 * `ui/nav/Routes.kt`'s doc comment). A Koin single also outlives the `Route.Session` back-stack
 * entry: `QuizViewModel` is scoped to that entry and is cleared the moment it's popped off
 * (`popUpTo(Route.Session) { inclusive = true }`), so results must live somewhere else to survive
 * the hop to `Route.QuizResults`.
 *
 * [results] is plain `var` state, not a `Flow`/`StateFlow` — the results screen reads it exactly
 * once, right after [QuizViewModel] finishes, and there's no concurrent writer.
 */
class QuizResultsHolder {
    var results: List<QuizAttempt> = emptyList()
}
