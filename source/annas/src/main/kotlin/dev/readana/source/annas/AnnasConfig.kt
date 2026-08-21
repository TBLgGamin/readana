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
        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://annas-archive.org",
            "https://annas-archive.se",
            "https://annas-archive.gl"
        )
    }
}
