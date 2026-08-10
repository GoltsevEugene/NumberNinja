package number.ninja.domain

enum class TrainingMode { FREE_PRACTICE, QUIZ }

enum class QuizSubMode { PROGRESSION, SHUFFLE, FIXED_LEVEL }

enum class AppLanguage(val tag: String) {
    UKRAINIAN("uk"),
    RUSSIAN("ru"),
    ENGLISH("en"),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val operations: Set<Operation> = setOf(Operation.ADDITION, Operation.SUBTRACTION),
    val level: Level = Level.EASY,
    val mode: TrainingMode = TrainingMode.QUIZ,
    val quizSubMode: QuizSubMode = QuizSubMode.PROGRESSION,
    val quizLength: Int = 12,
    // null means "no explicit override yet — follow the device's system locale".
    // Only set once the user actively picks a language in Settings; first-run/defaults
    // must NOT force English over whatever system language (e.g. uk/ru) is active.
    val language: AppLanguage? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hasCompletedFirstRun: Boolean = false,
) {
    companion object {
        const val MIN_QUIZ_LENGTH = 10
        const val MAX_QUIZ_LENGTH = 15
    }
}
