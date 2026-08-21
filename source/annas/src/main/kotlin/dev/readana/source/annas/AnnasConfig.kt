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
    val secretKey: String? = null,
    /** The mirror that last worked, remembered across restarts so searches do not re-probe. */
    val preferredMirror: String? = null
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
         * The three domains the site's own FAQ listed as authorised on 2026-08-21. `.org` and `.se`
         * were dropped because they no longer resolve, and `.li` because it only serves a redirect
         * interstitial; the list is editable in Settings for when they rotate again.
         */
        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://annas-archive.gl",
            "https://annas-archive.pk",
            "https://annas-archive.gd"
        )

        /**
         * Library Genesis holds most of what Anna's Archive indexes and serves it by md5 with no
         * browser check, so it is where a free download is tried first. The hosts share one
         * catalogue; the list is only there for when one of them is down.
         */
        val LIBGEN_HOSTS: List<String> = listOf(
            "https://libgen.li",
            "https://libgen.la",
            "https://libgen.vg",
            "https://libgen.bz"
        )

        /**
         * Partner servers to ask for a free download, in order. The site's first four numbers are
         * quicker but may put the file behind a wait-list; the later ones never wait. Starting
         * with a no-wait server usually gets a link straight away.
         */
        val SLOW_DOWNLOAD_SERVERS: List<Int> = listOf(4, 5, 0, 1)

        /** How long a free download may be made to wait in total before the browser takes over. */
        const val MAX_WAIT_SECONDS = 60
    }
}
