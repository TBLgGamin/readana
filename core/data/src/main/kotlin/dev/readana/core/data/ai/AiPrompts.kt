package dev.readana.core.data.ai

import dev.readana.core.model.AiFigure
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book

/**
 * What the assistant is told. The rules block is the spoiler guarantee's second half: the context
 * builder makes sure unread text is never sent, and this makes sure the model does not fill the
 * gap from what it happens to remember about the book.
 */
internal object AiPrompts {
    /** Stable across every request, so it sits first in the cacheable prefix. */
    fun rules(kind: AiThreadKind): String = RULES + "\n\n" + task(kind)

    private fun task(kind: AiThreadKind): String = when (kind) {
        AiThreadKind.Ask ->
            "The reader has selected a passage and asked about it. Answer the question directly, " +
                "in a few sentences, drawing on the quoted text."

        AiThreadKind.Explain ->
            "The reader has selected a passage and wants it explained: what it means, who is " +
                "speaking, what it refers back to, why it matters here. Three to six sentences."

        AiThreadKind.Recap ->
            "Write a 'previously on' recap of the story so far, 150 to 250 words, ending exactly " +
                "where the quoted text ends. No foreshadowing, no conclusions the text has not " +
                "reached, no mention of how anything turns out."
    }

    /** The already-read text. Marked cacheable, so follow-ups in a thread are cheap. */
    fun context(book: Book, contextText: String): String = buildString {
        append("<book_so_far>\n")
        append("Title: ").append(book.title).append('\n')
        if (book.authors.isNotEmpty()) {
            append("Author: ").append(book.authors.joinToString(", ")).append('\n')
        }
        append("\nThis is the part of the book the reader has read. Nothing beyond it exists ")
        append("for the purposes of this conversation.\n\n")
        append(contextText)
        append("\n</book_so_far>")
    }

    /** Placement matters: this changes as the reader moves, so it stays outside the cached block. */
    fun position(chapterTitle: String?): String? =
        chapterTitle?.takeIf { it.isNotBlank() }?.let { "The reader has just reached: $it" }

    /**
     * The passage under discussion. Part of the conversation's fixed frame rather than of its
     * first question, so every turn can see it and the saved history reads as plain questions.
     */
    fun selection(quote: String?): String? = quote?.trim()?.takeIf { it.isNotEmpty() }?.let {
        "The reader has selected this passage:\n\"" + it + "\""
    }

    fun question(question: String?): String = question?.trim()?.ifEmpty { null } ?: DEFAULT_REQUEST

    /**
     * Tells the model what pictures it has and how to point at them. The markers are resolved by
     * Readana after the answer arrives, so nothing comes back into the conversation.
     */
    fun pictures(figures: List<AiFigure>, mayIllustrate: Boolean): String? {
        if (figures.isEmpty() && !mayIllustrate) return null
        return buildString {
            if (figures.isNotEmpty()) append(figures.instructions())
            if (mayIllustrate) {
                if (isNotEmpty()) append("\n\n")
                append(ILLUSTRATE)
            }
        }.trim()
    }

    private fun List<AiFigure>.instructions(): String = buildString {
        appendLine("Illustrations from the pages you have been given, attached as images:")
        this@instructions.forEach { figure ->
            append("- [").append(figure.id).append(']')
            figure.caption?.let { append(" — ").append(it) }
            appendLine()
        }
        appendLine()
        append(
            "Write a marker such as [figure:1] on a line of its own to show one to the reader. " +
                "Only use a marker that is listed above."
        )
    }

    private const val ILLUSTRATE =
        "To illustrate something the reader may not be able to picture — a ship, a bird, a " +
            "building, a garment, a plant, a place, a figure from myth or history — write " +
            "[image: subject] on a line of its own and a picture of it will be looked up and " +
            "shown to them.\n" +
            "The subject is looked up as an encyclopaedia article, so name the thing exactly as " +
            "an article would be titled: [image: Hestia], not [image: Greek goddess of the " +
            "hearth]; [image: HMS Victory], not [image: Nelson's flagship]. Add a bracketed " +
            "qualifier when the bare name is ambiguous, as in [image: Mercury (mythology)]. " +
            "Never name the book, a character in it, or anything that happens in it. Use this " +
            "sparingly, at most twice in an answer, and only where a picture genuinely helps."

    private const val DEFAULT_REQUEST = "Explain this passage."

    private val RULES = """
        You are a reading companion inside an e-reader, helping someone who is part-way through a
        book. Everything you know about this book is the text quoted in <book_so_far>; it stops at
        the exact point the reader has reached.

        Rules, in order of importance:
        - Never reveal, hint at, foreshadow or speculate about anything that happens after the
          quoted text ends. This holds even if you recognise the book.
        - Never use outside knowledge of this book, its plot, its ending, its adaptations or its
          reputation. If something is not in the quoted text, say that it has not come up yet.
        - Do not summarise ahead, and do not answer "what happens next" questions.
        - Help the reader read: explain, clarify, remind. Do not replace the reading.
        - Answer in plain prose, short paragraphs, no markdown, no bullet lists, no headings.
        - Reply in the language the reader is writing in; for explanations and recaps, use the
          language of the quoted text unless the reader writes in another one.
    """.trimIndent()
}
