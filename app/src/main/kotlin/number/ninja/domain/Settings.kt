package number.ninja.domain

import java.util.Locale

enum class TrainingMode { FREE_PRACTICE, QUIZ }

enum class QuizSubMode { PROGRESSION, SHUFFLE, FIXED_LEVEL }

enum class AppLanguage(val tag: String) {
    UKRAINIAN("uk"),
    RUSSIAN("ru"),
    ENGLISH("en");

    companion object {
        /**
         * Resolves the first locale in an Android language-tag list to a language supported by
         * the app. An empty list deliberately returns null: AppCompat uses it for "system
         * language" rather than for English.
         */
        fun fromLanguageTags(languageTags: String): AppLanguage? {
            val primaryTag = languageTags.substringBefore(',').trim()
            if (primaryTag.isEmpty()) return null

            val language = Locale.forLanguageTag(primaryTag).language
            return entries.firstOrNull { it.tag == language }
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val operations: Set<Operation> = setOf(Operation.ADDITION, Operation.SUBTRACTION),
    val level: Level = Level.EASY,
    val mode: TrainingMode = TrainingMode.QUIZ,
    val quizSubMode: QuizSubMode = QuizSubMode.PROGRESSION,
    val quizLength: Int = 12,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hasCompletedFirstRun: Boolean = false,
) {
    companion object {
        const val MIN_QUIZ_LENGTH = 10
        const val MAX_QUIZ_LENGTH = 15
    }
}
