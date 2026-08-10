package number.ninja.domain

enum class Operation {
    ADDITION,
    SUBTRACTION,
    MULTIPLICATION,
    DIVISION,
}

enum class Level {
    EASY,
    MEDIUM,
    HARD,
    STAR,

    /**
     * Dedicated multiplication/division-table drilling — plain recall facts across the full
     * 2..10 range (spec follow-up request: "multiplication/division table check", added as its
     * own level rather than a separate mode so it's available in both free practice and quiz).
     * For addition/subtraction, which have no "table" concept, [ExampleGenerator] falls back to
     * [MEDIUM]-equivalent generation — see `generateAdditive`.
     */
    TABLES,
}
