package dev.readana.core.data.browser

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a browser check is being answered right now, so a screen can say "verifying…" instead of
 * looking stuck. The check itself runs out of sight; this is the only trace of it in the UI.
 */
interface BrowserVerificationStatus {
    val verifying: StateFlow<Boolean>
}
