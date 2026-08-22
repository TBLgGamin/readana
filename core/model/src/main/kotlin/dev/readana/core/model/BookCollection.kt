package dev.readana.core.model

import java.time.Instant

/**
 * A shelf the reader made, holding whichever books they put on it.
 *
 * One concept rather than separate collections and tags: a book can be on several shelves at once,
 * which is the whole of what tags were for, and two near-identical rows of chips in the same screen
 * is exactly the clutter a library should not have.
 */
data class BookCollection(val id: String, val name: String, val createdAt: Instant) {
    companion object {
        const val UNSAVED_ID = ""
    }
}
