package number.ninja.ui.nav

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
fun NumberNinjaNavHost(settingsRepository: SettingsRepository = koinInject()) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

    val current = settings
    if (current == null) {
        // First DataStore emission hasn't arrived yet — render an empty themed
        // surface rather than flashing a default (possibly wrong) start screen.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize())
        }
        return
    }

    // Single owner of setApplicationLocales() for the whole app (SettingsScreen's language
    // selector only persists via the ViewModel now — it does NOT call this itself, to avoid
    // firing the recreate-on-locale-change cycle twice for one logical change). Re-applies the
    // persisted language on every cold start, and reacts to it changing while the app is open.
    // On API 33+ this is mostly a no-op (setApplicationLocales() already delegates to the
    // framework LocaleManager, which survives process death on its own). Below 33, AppCompat only
    // auto-restores a stored locale if `autoStoreLocales` manifest meta-data is declared — it
    // isn't here — so without this, the DataStore-persisted language would be write-only: saved,
    // but never re-applied after the process dies.
    //
    // `current.language == null` means "no explicit override — follow system", so this
    // deliberately does NOT call setApplicationLocales(emptyLocaleList()) in that branch: on a
    // device running API 33+, doing so would forcibly reset whatever locale the user picked via
    // the system's own Settings > Apps > NumberNinja > Language screen (wired up via
    // generateLocaleConfig) back to the true system default on the app's very next cold start —
    // stomping the OS-level per-app-language feature this app also supports. Confirmed via device
    // testing (`cmd locale set-app-locales`) that an unconditional empty-list call undoes that
    // pick. Only assert a locale here when this app's own persisted setting is non-null — an
    // explicit in-Settings pick is the one case where our stored value should win over whatever
    // the framework currently reports.
    LaunchedEffect(current.language) {
        val language = current.language ?: return@LaunchedEffect
        val target = LocaleListCompat.forLanguageTags(language.tag)
        // Guard against redundant re-application (e.g. on cold start where the locale is
        // already correctly applied) — setApplicationLocales() with a *different* value
        // synchronously recreates the Activity on API < 33, so a spurious call here is a
        // visible flicker, not just a no-op.
        if (AppCompatDelegate.getApplicationLocales() != target) {
            AppCompatDelegate.setApplicationLocales(target)
        }
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
