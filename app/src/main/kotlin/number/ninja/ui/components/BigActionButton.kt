package number.ninja.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Large, hard-to-miss primary call-to-action button (e.g. Home's "Start", quick
 * setup's "Let's go!", free practice's "Check"/"Next"/"Continue"). Public surface:
 * [text], [onClick], [enabled] and [modifier] — [modifier] is applied *before* the
 * fixed `fillMaxWidth().height(64.dp)` sizing, so callers can add outer spacing
 * (e.g. `Modifier.padding(24.dp)`) without fighting the fixed size.
 */
@Composable
fun BigActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val guardedOnClick = rememberThrottledClick(onClick = onClick)
    Button(
        onClick = guardedOnClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}
