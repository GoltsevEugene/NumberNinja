package number.ninja.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import number.ninja.data.settings.AppLanguageManager
import number.ninja.data.settings.SettingsRepository
import number.ninja.ui.firstrun.QuickSetupScreen
import number.ninja.ui.home.HomeScreen
import number.ninja.ui.progress.ProgressScreen
import number.ninja.ui.quiz.QuizResultsScreen
import number.ninja.ui.session.SessionScreen
import number.ninja.ui.settings.SettingsScreen
import org.koin.compose.koinInject

/**
 * Pops only when there is another destination to display.
 *
 * A tap queued just before a destination is removed can otherwise pop the start destination and
 * leave the [NavHost] with an empty back stack.
 */
private fun NavController.popBackStackIfPossible() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}

/**
 * Root composable: waits for the first DataStore emission (so we know whether first
 * run is complete) before deciding a start destination, then hosts the NavHost.
 *
 * The start destination is captured once via [remember] on the first non-null
 * settings snapshot — it deliberately does NOT recompute if settings change later
 * (e.g. quick setup flips hasCompletedFirstRun), since changing a NavHost's
 * startDestination at runtime resets its graph. Quick setup instead navigates away
 * explicitly with popUpTo/inclusive.
 */
@Composable
fun NumberNinjaNavHost(
    settingsRepository: SettingsRepository = koinInject(),
    appLanguageManager: AppLanguageManager = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val localeMigrationComplete by appLanguageManager.legacyMigrationComplete.collectAsStateWithLifecycle()

    val current = settings
    if (current == null || !localeMigrationComplete) {
        // Wait for both DataStore and the one-time locale handoff so a cold start never exposes
        // stale-language content while AppCompat is restoring the persisted app locale.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize())
        }
        return
    }

    val startDestination = remember { if (current.hasCompletedFirstRun) Route.Home else Route.QuickSetup }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable<Route.QuickSetup> {
            QuickSetupScreen(
                onDone = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.QuickSetup) { inclusive = true }
                    }
                },
            )
        }
        composable<Route.Home> {
            HomeScreen(
                onStartClick = { navController.navigate(Route.Session) },
                onSettingsClick = { navController.navigate(Route.Settings) },
                onProgressClick = { navController.navigate(Route.Progress) },
            )
        }
        composable<Route.Settings> {
            SettingsScreen(onBack = navController::popBackStackIfPossible)
        }
        composable<Route.Progress> {
            ProgressScreen(onBack = navController::popBackStackIfPossible)
        }
        composable<Route.Session> {
            SessionScreen(
                onExit = navController::popBackStackIfPossible,
                onQuizFinished = {
                    // Removes Session (and its QuizViewModel) from the back stack so back-press
                    // from the results screen lands on Home, never back into a finished quiz.
                    navController.navigate(Route.QuizResults) {
                        popUpTo(Route.Session) { inclusive = true }
                    }
                },
            )
        }
        composable<Route.QuizResults> {
            QuizResultsScreen(onDone = navController::popBackStackIfPossible)
        }
    }
}
