package dev.readana.feature.reader

import dev.readana.ai.client.BookFigure
import dev.readana.ai.client.TextWalker
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Url

/**
 * Reads a publication's own text around a locator, so the assistant can be handed the pages the
 * reader has actually been through.
 *
 * Two independent iterators are kept: one that only ever moves forward from the anchor, one that
 * only ever moves backward. That is what lets [dev.readana.ai.client.ReadingContextBuilder] stop
 * dead at the selected passage rather than having to rewind past text it should not have read.
 */
internal class ReadiumTextWalker(
    private val forward: Content.Iterator,
    private val backward: Content.Iterator,
    private val loadImage: suspend (Url) -> ByteArray?
) : TextWalker {
    private val figures = mutableListOf<BookFigure>()

    override suspend fun next(): String? = forward.nextText()

    override suspend fun previous(): String? = backward.previousText()

    override fun figuresSeen(): List<BookFigure> = figures.toList()

    /** Skips elements with nothing to say: images, empty wrappers, footnote markers. */
    private suspend fun Content.Iterator.nextText(): String? {
        var skipped = 0
        while (skipped < MAX_EMPTY_ELEMENTS) {
            val element = nextOrNull() ?: return null
            element.recordIfFigure()
            element.readableText()?.let { return it }
            skipped++
        }
        return null
    }

    private suspend fun Content.Iterator.previousText(): String? {
        var skipped = 0
        while (skipped < MAX_EMPTY_ELEMENTS) {
            val element = previousOrNull() ?: return null
            element.recordIfFigure()
            element.readableText()?.let { return it }
            skipped++
        }
        return null
    }

    private fun Content.Element.readableText(): String? =
        (this as? Content.TextualElement)?.text?.trim()?.takeIf { it.isNotEmpty() }

    /** Notes an illustration in passing; its bytes are only read if an answer actually uses it. */
    private fun Content.Element.recordIfFigure() {
        val image = this as? Content.ImageElement ?: return
        if (figures.size >= MAX_FIGURES_TRACKED) return
        val url = image.embeddedLink.url()
        if (figures.any { it.href == url.toString() }) return
        figures += BookFigure(
            href = url.toString(),
            caption = image.caption?.trim()?.takeIf { it.isNotEmpty() },
            mediaType = image.embeddedLink.mediaType?.toString(),
            load = { loadImage(url) }
        )
    }

    private companion object {
        /** A long run of images or empty markup should not look like the end of the book. */
        const val MAX_EMPTY_ELEMENTS = 200

        /** Only a handful can ever be sent, so there is no point remembering a whole gallery. */
        const val MAX_FIGURES_TRACKED = 12
    }
}

/**
 * Builds a walker starting at [anchor]. Null when the publication has no text service — pdfium
 * PDFs, which also have no text selection, so the assistant simply does not appear for them.
 */
internal fun Publication.textWalkerAt(anchor: Locator?): TextWalker? {
    val content = content(anchor) ?: return null
    return ReadiumTextWalker(
        forward = content.iterator(),
        backward = content.iterator(),
        loadImage = { url -> readBytes(url) }
    )
}

/** Reads one resource out of the publication, or null when it cannot be read. */
internal suspend fun Publication.readBytes(url: Url): ByteArray? {
    val resource = get(url) ?: return null
    return try {
        resource.read().getOrNull()
    } finally {
        resource.close()
    }
}

/** Whether this publication can be read as text at all. */
internal fun Publication.hasText(): Boolean = content() != null
