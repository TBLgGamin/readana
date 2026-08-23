package dev.readana.source.api

/**
 * One user-editable setting a source needs. Sources declare these instead of the settings screen
 * knowing about any particular source: the Sources page renders a field per entry and hands the
 * values back through [SourceConfigStore], so a new source needs no UI work at all.
 */
data class SourceConfigField(
    /** Stable key the value is stored under, unique within the source. */
    val key: String,
    val label: String,
    val kind: Kind,
    val help: String? = null,
    /** Shown when the field is empty; the right place for a built-in default. */
    val placeholder: String? = null
) {
    enum class Kind {
        /** A single line of text. */
        Text,

        /** A single line kept out of sight; keys and passwords. */
        Secret,

        /** One URL per line, tried in order. */
        UrlList
    }
}

/** Reads what the user configured for a source. Backed by DataStore in the data layer. */
fun interface SourceConfigStore {
    suspend fun values(sourceId: String): Map<String, String>
}
