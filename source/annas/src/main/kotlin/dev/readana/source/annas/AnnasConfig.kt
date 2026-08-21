package dev.readana.source.annas

/**
 * User-configurable access settings for Anna's Archive.
 *
 * Mirrors rotate and get blocked or parked (in August 2026 `.li` and `.pm` were parking pages
 * and `.org` was unreachable from Dutch ISPs), so the list is editable in Settings and the
 * source fails over automatically between entries.
 */
data class AnnasConfig(
    val mirrors: List<String> = DEFAULT_MIRRORS,
    /** Member "secret key" that unlocks the JSON fast-download API. Null → browser download flow. */
    val secretKey: String? = null
) {
    /**
     * Mirrors ready to use. Typing `annas-archive.gl` is the obvious thing to do, so a missing
     * scheme is filled in rather than silently dropping the entry and reporting "no mirror
     * configured". Falls back to the built-in list when nothing usable is left.
     */
    val normalizedMirrors: List<String>
        get() = mirrors.mapNotNull { it.normalizeMirror() }.ifEmpty { DEFAULT_MIRRORS }

    private fun String.normalizeMirror(): String? {
        val trimmed = trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed

            // A scheme we cannot use (ftp://, file://) is a mistake, not a host to guess at.
            SCHEME.containsMatchIn(trimmed) -> return null

            else -> "https://$trimmed"
        }
        return withScheme.takeIf { HOST.containsMatchIn(it) }
    }

    val hasSecretKey: Boolean
        get() = !secretKey.isNullOrBlank()

    companion object {
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        /** A host with at least one dot, so a stray word does not become `https://word`. */
        private val HOST = Regex("^https?://[^/\\s]+\\.[^/\\s]+")

        /**
         * Checked 2026-08-21 from a Dutch connection: `.gl` and `.pk` serve the site (and rate-limit
         * hard), `.li` answers with a redirect interstitial, and `.org`/`.se` no longer resolve at
         * all. Working mirrors go first so a search does not begin with two DNS failures; the dead
         * ones stay as a fallback for connections where they do resolve.
         */
        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://annas-archive.gl",
            "https://annas-archive.pk",
            "https://annas-archive.li",
            "https://annas-archive.org",
            "https://annas-archive.se"
        )
    }
}
