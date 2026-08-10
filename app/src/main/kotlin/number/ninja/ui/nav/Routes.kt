package number.ninja.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes (kotlinx.serialization + `composable<T>`). */
sealed interface Route {

    @Serializable
    data object QuickSetup : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Session : Route

    @Serializable
    data object QuizResults : Route

    @Serializable
    data object Progress : Route
}
