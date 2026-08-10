package number.ninja.domain

data class OperationStats(
    val operation: Operation,
    val totalAttempts: Int,
    val correctAttempts: Int,
) {
    val accuracyPercent: Int get() = if (totalAttempts == 0) 0 else (correctAttempts * 100) / totalAttempts
}

/**
 * A specific weak spot flagged as needing more practice. For multiplication/division this is an
 * operand-range fact bucket (e.g. "×7-9"), set via [rangeLabel]. Addition/subtraction have no
 * natural operand-range bucket, so they're flagged per [level] instead — exactly one of
 * [rangeLabel] or [level] is set.
 */
data class WeakSpot(
    val operation: Operation,
    val accuracyPercent: Int,
    val attempts: Int,
    val rangeLabel: String? = null,
    val level: Level? = null,
) {
    companion object {
        const val MIN_ATTEMPTS_TO_FLAG = 1
        const val ACCURACY_THRESHOLD_PERCENT = 70
    }
}

/** Per-[Level] accuracy breakdown for one operation, e.g. "Addition - Easy 75%, correct 3 of 4". */
data class LevelStats(val level: Level, val totalAttempts: Int, val correctAttempts: Int) {
    val accuracyPercent: Int get() = if (totalAttempts == 0) 0 else (correctAttempts * 100) / totalAttempts
}
