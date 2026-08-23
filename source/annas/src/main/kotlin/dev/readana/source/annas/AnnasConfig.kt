package dev.readana.source.annas

import dev.readana.source.api.SourceConfigField

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
        const val KEY_MIRRORS = "mirrors"
        const val KEY_SECRET_KEY = "secretKey"

        /**
         * Remembered rather than typed: the mirror that last answered. It rides in the same map as
         * the user's own settings but is deliberately not a [CONFIG_FIELDS] entry, so it never
         * shows up as a box to fill in.
         */
        const val KEY_PREFERRED_MIRROR = "preferredMirror"

        /** Rebuilds the config from what the settings page stored. */
        fun from(values: Map<String, String>): AnnasConfig = AnnasConfig(
            mirrors = values[KEY_MIRRORS]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_MIRRORS },
            secretKey = values[KEY_SECRET_KEY]?.takeIf { it.isNotBlank() },
            preferredMirror = values[KEY_PREFERRED_MIRROR]?.takeIf { it.isNotBlank() }
        )

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
         * Which of the member download servers to ask for, in order. The site rotates the domains
         * it hands out and an ISP block usually takes one before the others, so a link that cannot
         * be resolved is worth swapping for the next server rather than failing the download.
         */
        val FAST_DOWNLOAD_SERVERS: List<Int> = listOf(0, 1, 2)

        /**
         * Partner servers to ask for a free download, in order. The site's first four numbers are
         * quicker but may put the file behind a wait-list; the later ones never wait. Starting
         * with a no-wait server usually gets a link straight away.
         */
        val SLOW_DOWNLOAD_SERVERS: List<Int> = listOf(4, 5, 0, 1)

        /** How long a free download may be made to wait in total before the browser takes over. */
        const val MAX_WAIT_SECONDS = 60

        /**
         * Declared last on purpose: it reads [DEFAULT_MIRRORS], and a companion initialises its
         * properties in source order.
         */
        val CONFIG_FIELDS: List<SourceConfigField> = listOf(
            SourceConfigField(
                key = KEY_MIRRORS,
                label = "Mirrors",
                kind = SourceConfigField.Kind.UrlList,
                help = "One per line, tried in order. Leave empty to use the built-in list.",
                placeholder = DEFAULT_MIRRORS.joinToString("\n")
            ),
            SourceConfigField(
                key = KEY_SECRET_KEY,
                label = "Membership key",
                kind = SourceConfigField.Kind.Secret,
                help = "Unlocks fast downloads. Without one, downloads go through the browser."
            )
        )
    }
}
