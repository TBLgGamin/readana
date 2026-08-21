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
    val normalizedMirrors: List<String>
        get() = mirrors.map { it.trim().trimEnd('/') }.filter {
            it.startsWith("https://") ||
                it.startsWith("http://")
        }

    val hasSecretKey: Boolean
        get() = !secretKey.isNullOrBlank()

    companion object {
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
