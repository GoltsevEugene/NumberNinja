package number.ninja.ui

import androidx.annotation.StringRes
import number.ninja.R
import number.ninja.domain.AppLanguage
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.TrainingMode

/**
 * Maps domain enums to display-string resources. Lives in `ui/` (not `domain/`) so the
 * domain layer stays free of Android/resource dependencies. English-only for now —
 * localization (uk/ru) is a later phase; these resources will move to values-uk/values-ru.
 */

@StringRes
fun Operation.labelRes(): Int = when (this) {
    Operation.ADDITION -> R.string.operation_addition
    Operation.SUBTRACTION -> R.string.operation_subtraction
    Operation.MULTIPLICATION -> R.string.operation_multiplication
    Operation.DIVISION -> R.string.operation_division
}

@StringRes
fun Level.labelRes(): Int = when (this) {
    Level.EASY -> R.string.level_easy
    Level.MEDIUM -> R.string.level_medium
    Level.HARD -> R.string.level_hard
    Level.STAR -> R.string.level_star
    Level.TABLES -> R.string.level_tables
}

@StringRes
fun TrainingMode.labelRes(): Int = when (this) {
    TrainingMode.FREE_PRACTICE -> R.string.mode_free_practice
    TrainingMode.QUIZ -> R.string.mode_quiz
}

@StringRes
fun QuizSubMode.labelRes(): Int = when (this) {
    QuizSubMode.PROGRESSION -> R.string.quiz_sub_mode_progression
    QuizSubMode.SHUFFLE -> R.string.quiz_sub_mode_shuffle
    QuizSubMode.FIXED_LEVEL -> R.string.quiz_sub_mode_fixed_level
}

@StringRes
fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_ukrainian
    AppLanguage.RUSSIAN -> R.string.language_russian
    AppLanguage.ENGLISH -> R.string.language_english
}
