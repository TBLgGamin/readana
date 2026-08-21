package dev.readana.source.api

/**
 * Clears a site's "checking your browser" gate on behalf of a plain HTTP request.
 *
 * Some sources sit behind a check that only a real browser engine can answer: it runs JavaScript
 * and, once satisfied, leaves a cookie that ordinary requests may then present. A source that
 * meets such a page hands the URL here and retries once. Implementations run the page in a
 * browser the user never sees and share their cookie store with the HTTP client; when there is no
 * browser to use, they report [VerifyOutcome.Unavailable] and the source falls back to asking the
 * user to open the page themselves.
 */
interface BrowserVerifier {
    suspend fun verify(url: String): VerifyOutcome
}

sealed interface VerifyOutcome {
    /** The check passed; whatever it handed out is in the shared cookie store. */
    data object Verified : VerifyOutcome

    /** The check did not pass in time, or the page kept asking. */
    data class Failed(val reason: String) : VerifyOutcome

    /** There is no browser engine to run the check with on this device. */
    data object Unavailable : VerifyOutcome
}

/** For pure-JVM callers and tests: never verifies, so the source takes its visible fallback. */
object NoBrowserVerifier : BrowserVerifier {
    override suspend fun verify(url: String): VerifyOutcome = VerifyOutcome.Unavailable
}
