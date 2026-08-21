package dev.readana.feature.discover

import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult

/**
 * One book, and every copy of it a search turned up.
 *
 * [best] is what the grid shows and what the download button takes; [editions] holds them all,
 * best first, so a reader after a particular language or format can pick.
 */
data class ResultGroup(val key: String, val editions: List<SearchResult>) {
    val best: SearchResult get() = editions.first()

    val hasChoices: Boolean get() = editions.size > 1

    /** The languages on offer, in the order the editions are listed, without repeats. */
    val languages: List<String> get() = editions.mapNotNull { it.language }.distinct()
}

/**
 * Collapses the copies of a book into one entry each.
 *
 * Shadow libraries index the same work many times over — different languages, formats, scans and
 * uploads — and a page of results is mostly the same handful of books repeated. Grouping them
 * turns that into a browsable list without hiding anything: every copy stays reachable.
 */
object ResultGrouping {
    private val NOISE = Regex("[^\\p{L}\\p{N}]+")
    private val LEADING_ARTICLE = Regex("^(the|a|an|de|het|een|le|la|les|el|los|der|die|das) ")

    fun group(results: List<SearchResult>): List<ResultGroup> = results
        .groupBy(::keyOf)
        .map { (key, editions) -> ResultGroup(key, editions.sortedWith(PREFERRED)) }

    /**
     * Same title and same author means the same book. Author names arrive in every order and
     * punctuation ("Austen, Jane" against "Jane Austen"), so they are compared as a set of words,
     * and a leading article is dropped because catalogues disagree about those too.
     */
    private fun keyOf(result: SearchResult): String {
        val title = result.title.lowercase()
            // "Circe : a novel" and "Circe" are the same book; catalogues disagree about subtitles.
            .substringBefore(':')
            .replace(NOISE, " ")
            .trim()
            .replace(LEADING_ARTICLE, "")
        val authors = result.authors
            .flatMap { it.lowercase().split(NOISE) }
            .filter { it.isNotBlank() }
            .toSortedSet()
            .joinToString(" ")
        return "$title|$authors"
    }

    /**
     * What to put first: something the reader can open, that carries a cover, and is the fuller
     * file of the two. Size stands in for quality — a fuller EPUB usually means it kept its images.
     */
    private val PREFERRED = compareBy<SearchResult>(
        { if (it.format == BookFormat.EPUB) 0 else 1 },
        { if (it.coverUrl != null) 0 else 1 }
    ).thenByDescending { it.sizeBytes ?: 0 }
}
