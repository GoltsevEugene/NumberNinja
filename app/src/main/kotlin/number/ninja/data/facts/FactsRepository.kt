package number.ninja.data.facts

import android.content.Context
import androidx.core.content.ContextCompat
import number.ninja.R
import kotlin.random.Random

/**
 * Rotates through every fact in the bank (all categories pooled together) without repeats until
 * the whole bank is exhausted, then reshuffles (spec §8). Facts are stored as (arrayResId, index)
 * pairs rather than resolved strings, so a mid-session locale change is reflected automatically
 * the next time a fact is displayed, without losing rotation progress.
 */
class FactsRepository(private val context: Context) {

    private val categoryArrayResIds = listOf(
        R.array.facts_animals,
        R.array.facts_space,
        R.array.facts_dinosaurs,
        R.array.facts_nature_records,
        R.array.facts_math,
    )

    private var remaining: MutableList<Pair<Int, Int>> = mutableListOf()

    fun nextFact(random: Random = Random.Default): String {
        if (remaining.isEmpty()) {
            remaining = freshPool().shuffled(random).toMutableList()
        }
        val (arrayResId, index) = remaining.removeAt(remaining.lastIndex)
        return localizedResources().getStringArray(arrayResId)[index]
    }

    private fun freshPool(): List<Pair<Int, Int>> = categoryArrayResIds.flatMap { arrayResId ->
        val size = localizedResources().getStringArray(arrayResId).size
        (0 until size).map { index -> arrayResId to index }
    }

    /**
     * AppCompat's per-app locales are attached to an AppCompatActivity context on API 32 and
     * lower; the application context injected into this repository can therefore keep exposing
     * resources in the device locale after an in-app language change. Asking AndroidX for a
     * language-aware context keeps these non-UI resource reads aligned with Compose's
     * `stringResource` calls on every supported Android version.
     */
    private fun localizedResources() = ContextCompat.getContextForLanguage(context).resources
}
