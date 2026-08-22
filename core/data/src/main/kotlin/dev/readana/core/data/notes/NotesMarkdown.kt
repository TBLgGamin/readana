package dev.readana.core.data.notes

import dev.readana.core.model.Book
import dev.readana.core.model.Highlight
import kotlin.math.roundToInt

private const val PERCENT = 100

/**
 * Renders a book's marked passages as Markdown.
 *
 * Markdown rather than a proprietary format because the point of exporting notes is to get them
 * somewhere else — a notes app, a document, an email — and every one of those reads it. Pure, so
 * it is tested without a device.
 */
fun notesMarkdown(book: Book, highlights: List<Highlight>): String = buildString {
    append("# ").append(book.title).append("\n")
    if (book.authors.isNotEmpty()) {
        append("*").append(book.authorsLabel).append("*\n")
    }
    highlights.forEach { highlight ->
        append("\n---\n\n")
        // A quote block per passage, so a note underneath it reads as commentary on it.
        highlight.text.trim().lines().forEach { line ->
            append("> ").append(line).append("\n")
        }
        highlight.note?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            append("\n").append(note).append("\n")
        }
        highlight.progression?.let { progression ->
            append("\n`").append((progression * PERCENT).roundToInt()).append("%`\n")
        }
    }
}

/**
 * A file name that survives every filesystem the export might land on — including the FAT one on a
 * memory card, which is the strictest thing a shared file is likely to meet.
 */
fun notesFileName(book: Book): String {
    val stem = book.title
        .replace(RESERVED, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .take(MAX_STEM)
        .trim()
        .ifEmpty { FALLBACK_STEM }
    return "$stem - notes.md"
}

private const val MAX_STEM = 60
private const val FALLBACK_STEM = "notes"
private val WHITESPACE = Regex("\\s+")

/** Characters no common filesystem accepts, plus the control range. */
private val RESERVED = Regex("""[\\/:*?"<>|\x00-\x1F]""")
