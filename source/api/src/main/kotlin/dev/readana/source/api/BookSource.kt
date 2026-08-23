package dev.readana.source.api

import dev.readana.core.common.result.AppResult

/**
 * A place books can be searched and downloaded from (Project Gutenberg, an OPDS catalog,
 * Anna's Archive, ...). Implementations are pure Kotlin/JVM so they can be unit-tested with a
 * mock HTTP engine; the Android layer only wires them into DI.
 */
interface BookSource {
    val id: SourceId

    /** Human-readable name shown in the Discover screen. */
    val displayName: String

    /**
     * Whether the source takes part in searches before the user has made an explicit choice in
     * Settings. Sources with legal or configuration caveats should default to `false`.
     */
    val enabledByDefault: Boolean
        get() = true

    /**
     * What the user has to fill in before this source works. Rendered generically by the Sources
     * settings page; an empty list means the source needs no configuration.
     */
    val configFields: List<SourceConfigField>
        get() = emptyList()

    /** A line under the name in Settings saying what the source is. */
    val summary: String?
        get() = null

    suspend fun search(query: SearchQuery): AppResult<SearchPage>

    /**
     * Turns a search hit into something the download worker can fetch. Sources that need a
     * browser session for the final hop return a request with [DownloadRequest.requiresBrowser].
     */
    suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest>
}

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "SourceId must not be blank" }
    }

    override fun toString(): String = value
}
