package dev.readana.source.trending

import dev.readana.core.common.result.AppResult

/**
 * A book worth suggesting before the user has searched for anything. Only enough to render a cover
 * and seed a search — nothing here is downloadable on its own.
 */
data class TrendingBook(val title: String, val authors: List<String>, val coverUrl: String?) {
    /** What to search the book sources for when the user taps it. */
    val searchQuery: String
        get() = listOfNotNull(title, authors.firstOrNull()).joinToString(" ")
}

/** Books people are reading right now, for the empty Discover screen. */
interface TrendingBooks {
    suspend fun trending(limit: Int = DEFAULT_LIMIT): AppResult<List<TrendingBook>>

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
