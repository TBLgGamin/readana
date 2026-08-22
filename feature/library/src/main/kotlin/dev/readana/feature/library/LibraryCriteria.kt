package dev.readana.feature.library

import dev.readana.core.model.Book
import dev.readana.core.model.BookCollection
import dev.readana.core.model.ReadingStatus

/** Everything the reader has narrowed the grid down by, in one value. */
internal data class LibraryCriteria(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Recent,
    val filter: LibraryFilter = LibraryFilter.All,
    val shelf: Shelf? = null
) {
    /** True while the reader is looking for something, which is when shortcuts get out of the way. */
    val isNarrowed: Boolean get() = query.isNotBlank() || filter != LibraryFilter.All || shelf != null
}

/**
 * Every shelf the library can be narrowed to: the reader's own collections first, then the series
 * the books came in.
 *
 * Series are derived rather than stored because they are the publisher's fact about a book, not the
 * reader's; a series with only one book in it is not a shelf, it is a coincidence.
 */
internal fun List<Book>.shelves(collections: List<BookCollection>): List<Shelf> {
    val made = collections.map { Shelf.Collection(it.id, it.name) }
    val found = groupBy { it.series?.name }
        .filterKeys { !it.isNullOrBlank() }
        .filterValues { it.size > 1 }
        .keys
        .filterNotNull()
        .sorted()
        .map { Shelf.Series(it) }
    return made + found
}

/** The books to show, in the order to show them. */
internal fun List<Book>.forLibrary(criteria: LibraryCriteria, memberships: Map<String, Set<String>>): List<Book> =
    asSequence()
        .filter { it.matches(criteria.query) }
        .filter { it.matches(criteria.filter) }
        .filter { it.isOn(criteria.shelf, memberships) }
        .sortedWith(criteria.sort.comparator())
        .toList()

/**
 * The book to offer to pick back up: the one most recently open that is not finished.
 *
 * Falls back to nothing rather than to "the newest book", because offering to continue a book that
 * has never been opened is not continuing anything.
 */
internal fun List<Book>.continueReading(): Book? = filter { it.readingStatus == ReadingStatus.Reading }
    .maxByOrNull { it.lastOpenedAt ?: it.addedAt }

private fun Book.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        authors.any { it.contains(query, ignoreCase = true) } ||
        series?.name?.contains(query, ignoreCase = true) == true
}

private fun Book.matches(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.All -> true
    LibraryFilter.Reading -> readingStatus == ReadingStatus.Reading
    LibraryFilter.Unread -> readingStatus == ReadingStatus.Unread
    LibraryFilter.Finished -> readingStatus == ReadingStatus.Finished
}

private fun Book.isOn(shelf: Shelf?, memberships: Map<String, Set<String>>): Boolean = when (shelf) {
    null -> true
    is Shelf.Collection -> memberships[shelf.id]?.contains(id) == true
    is Shelf.Series -> series?.name == shelf.label
}

private fun LibrarySort.comparator(): Comparator<Book> = when (this) {
    LibrarySort.Recent -> compareByDescending { it.lastOpenedAt ?: it.addedAt }

    LibrarySort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

    LibrarySort.Author -> compareBy(
        { it.authors.firstOrNull()?.lowercase().orEmpty() },
        { it.title.lowercase() }
    )
}
