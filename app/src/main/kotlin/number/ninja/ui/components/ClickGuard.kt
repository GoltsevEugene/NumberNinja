package number.ninja.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Small leading-edge gate used by navigation and primary action buttons.
 *
 * The first click is delivered immediately; subsequent clicks are ignored until [unlock] is
 * called. Keeping this tiny state holder separate from Compose makes the one-shot behaviour easy
 * to verify without UI timing in a test.
 */
internal class ClickGate {
    private var locked = false

    fun tryLock(): Boolean {
        if (locked) return false
        locked = true
        return true
    }

    fun unlock() {
        locked = false
    }
}

/**
 * Returns a stable callback that accepts the first tap immediately and suppresses a short burst
 * of repeats. This is intended for actions such as navigation/continuation where delivering the
 * same click twice is never useful; controls such as settings toggles should remain unthrottled.
 */
@Composable
internal fun rememberThrottledClick(
    cooldownMillis: Long = CLICK_COOLDOWN_MILLIS,
    onClick: () -> Unit,
): () -> Unit {
    val gate = remember { ClickGate() }
    val latestOnClick = rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()

    return remember(gate, cooldownMillis) {
        {
            if (gate.tryLock()) {
                // Schedule the unlock before invoking arbitrary caller code, so even a callback
                // that throws cannot leave a still-composed control permanently locked.
                coroutineScope.launch {
                    delay(cooldownMillis)
                    gate.unlock()
                }
                latestOnClick.value()
            }
        }
    }
}

private const val CLICK_COOLDOWN_MILLIS = 600L
