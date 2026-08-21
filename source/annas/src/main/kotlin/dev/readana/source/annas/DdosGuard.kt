package dev.readana.source.annas

/**
 * Recognises the "checking your browser" page that stands in front of Anna's Archive.
 *
 * Anna's Archive answers a request without a recent browser check with a redirect to the same URL
 * plus `check=1`, which its front-end then serves as a JavaScript challenge. Deciding this from
 * the `Server` header alone is not possible — every response carries it, including perfectly good
 * ones — so the markers below are the page itself and the query parameter the site adds.
 */
internal object DdosGuard {
    /** The scripts the challenge page loads, and the title it carries. */
    private val BODY_MARKERS = listOf(
        "ddos-guard/js-challenge",
        "check.ddos-guard.net",
        "<title>DDoS-Guard",
        // Cloudflare, in case a mirror moves back to it.
        "challenge-platform",
        "cf_chl_opt"
    )

    private const val CHECK_PARAM = "check=1"
    private val CHALLENGE_STATUSES = setOf(403, 503)

    /**
     * Whether this response is the gate rather than the page that was asked for.
     *
     * @param url the URL the response finally came from, redirects included.
     */
    fun isChallenge(status: Int, header: (String) -> String?, url: String, body: String): Boolean {
        if (BODY_MARKERS.any { body.contains(it, ignoreCase = true) }) return true
        if (status !in CHALLENGE_STATUSES) return false
        return url.contains(CHECK_PARAM, ignoreCase = true) ||
            header("cf-mitigated")?.contains("challenge", ignoreCase = true) == true
    }
}
