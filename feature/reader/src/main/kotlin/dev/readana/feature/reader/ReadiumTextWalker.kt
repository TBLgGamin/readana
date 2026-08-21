package dev.readana.feature.reader

import dev.readana.ai.client.TextWalker
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

/**
 * Reads a publication's own text around a locator, so the assistant can be handed the pages the
 * reader has actually been through.
 *
 * Two independent iterators are kept: one that only ever moves forward from the anchor, one that
 * only ever moves backward. That is what lets [dev.readana.ai.client.ReadingContextBuilder] stop
 * dead at the selected passage rather than having to rewind past text it should not have read.
 */
internal class ReadiumTextWalker(private val forward: Content.Iterator, private val backward: Content.Iterator) :
    TextWalker {
    override suspend fun next(): String? = forward.nextText()

    override suspend fun previous(): String? = backward.previousText()

    /** Skips elements with nothing to say: images, empty wrappers, footnote markers. */
    private suspend fun Content.Iterator.nextText(): String? {
        var skipped = 0
        while (skipped < MAX_EMPTY_ELEMENTS) {
            val element = nextOrNull() ?: return null
            element.readableText()?.let { return it }
            skipped++
        }
        return null
    }

    private suspend fun Content.Iterator.previousText(): String? {
        var skipped = 0
        while (skipped < MAX_EMPTY_ELEMENTS) {
            val element = previousOrNull() ?: return null
            element.readableText()?.let { return it }
            skipped++
        }
        return null
    }

    private fun Content.Element.readableText(): String? =
        (this as? Content.TextualElement)?.text?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        /** A long run of images or empty markup should not look like the end of the book. */
        const val MAX_EMPTY_ELEMENTS = 200
    }
}

/**
 * Builds a walker starting at [anchor]. Null when the publication has no text service — pdfium
 * PDFs, which also have no text selection, so the assistant simply does not appear for them.
 */
internal fun Publication.textWalkerAt(anchor: Locator?): TextWalker? {
    val content = content(anchor) ?: return null
    return ReadiumTextWalker(forward = content.iterator(), backward = content.iterator())
}

/** Whether this publication can be read as text at all. */
internal fun Publication.hasText(): Boolean = content() != null
