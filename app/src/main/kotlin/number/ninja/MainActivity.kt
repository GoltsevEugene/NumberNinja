package number.ninja

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import number.ninja.ui.components.LocalWindowWidthSizeClass
import number.ninja.ui.nav.NumberNinjaNavHost
import number.ninja.ui.theme.NumberNinjaTheme

/**
 * Single Activity per spec §2/§3 — all screens are composables in [NumberNinjaNavHost].
 *
 * Extends [AppCompatActivity] (not plain ComponentActivity) even though nothing here
 * needs AppCompat today: spec §4 requires `AppCompatDelegate.setApplicationLocales()`
 * for in-app language switching, which needs an AppCompatActivity on API < 33.
 * Choosing this base class now avoids reworking the Activity + XML theme parent in
 * the localization phase.
 *
 * [calculateWindowSizeClass] (spec §2/§12 step 9) is computed once here — the same "compute
 * once at the composition root" spot as [enableEdgeToEdge] — and exposed to every screen via
 * [LocalWindowWidthSizeClass] rather than as a parameter threaded through
 * [NumberNinjaNavHost]/each screen's constructor; see that CompositionLocal's doc comment for
 * why. It is still `@ExperimentalMaterial3WindowSizeClassApi` in the current Compose Material3
 * BOM, hence the class-level opt-in.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            NumberNinjaTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass) {
                    NumberNinjaNavHost()
                }
            }
        }
    }
}
