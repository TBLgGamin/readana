package dev.readana.core.data.repository

import dev.readana.core.model.SourceSettings
import dev.readana.source.api.BookSource
import dev.readana.source.api.SourceId
import javax.inject.Inject
import javax.inject.Singleton

/** All [BookSource]s bound into the Hilt multibinding set, in a stable display order. */
@Singleton
class BookSourceRegistry @Inject constructor(sources: Set<@JvmSuppressWildcards BookSource>) {
    val all: List<BookSource> = sources.sortedBy { it.displayName }

    fun byId(id: SourceId): BookSource? = all.firstOrNull { it.id == id }

    /** Sources the user has enabled (or that are on by default and not switched off). */
    fun enabled(settings: SourceSettings): List<BookSource> =
        all.filter { settings.isEnabled(it.id.value, it.enabledByDefault) }
}
