package dev.readana.ai.client

/**
 * Walks a publication's text one element at a time, in both directions, starting from wherever the
 * reader currently is. Implemented over Readium's content iterator; kept as an interface here so
 * the context rules can be tested without a publication.
 */
interface TextWalker {
    /** The next element after the starting point, or null at the end of the book. */
    suspend fun next(): String?

    /** The element before the starting point, or null at the beginning of the book. */
    suspend fun previous(): String?
}

/**
 * The part of the book handed to the model, and how it was cut.
 *
 * [text] never contains anything the reader has not reached: the builder walks forward only as far
 * as the selected passage, and otherwise only backwards.
 */
data class ReadingContext(
    val text: String,
    /** True when the budget forced the earliest chapters out. */
    val truncatedAtStart: Boolean,
    val charCount: Int = text.length
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * Gathers the already-read text around the reading position.
 *
 * The rules are the whole spoiler guarantee:
 * 1. From the anchor (the first element visible on the page), walk **forward** only until the
 *    selected passage has been included — and not one element further. A recap passes no
 *    selection, so it never walks forward at all.
 * 2. Walk **backwards** from the anchor until [budgetChars] is spent, cutting on element
 *    boundaries so no sentence is halved.
 * 3. Join everything in reading order.
 */
class ReadingContextBuilder(private val budgetChars: Int) {
    suspend fun build(walker: TextWalker, selection: String? = null): ReadingContext {
        val ahead = if (selection.isNullOrBlank()) emptyList() else walker.forwardTo(selection)
        val aheadChars = ahead.sumOf { it.length }
        val behind = walker.backwards(budget = (budgetChars - aheadChars).coerceAtLeast(0))
        val parts = behind.elements.asReversed() + ahead
        val body = parts.filter { it.isNotBlank() }.joinToString(PARAGRAPH_SEPARATOR)
        val text = if (behind.truncated && body.isNotEmpty()) OMITTED_NOTICE + body else body
        return ReadingContext(text = text, truncatedAtStart = behind.truncated)
    }

    /**
     * Reads forward until the element holding [selection] has been taken. Stops at the limit even
     * when the passage is not found, so a failed match can never leak the pages ahead.
     */
    private suspend fun TextWalker.forwardTo(selection: String): List<String> {
        val needle = selection.normalised().take(MATCH_CHARS)
        val taken = mutableListOf<String>()
        var scanned = 0
        while (scanned < MAX_FORWARD_ELEMENTS) {
            val element = next() ?: break
            scanned++
            taken += element
            if (needle.isNotEmpty() && element.normalised().contains(needle)) return taken
        }
        // The passage was not recognised (footnotes, odd markup); keep only the current element.
        return taken.take(1)
    }

    private suspend fun TextWalker.backwards(budget: Int): Backward {
        val taken = mutableListOf<String>()
        var spent = 0
        while (spent < budget) {
            val element = previous() ?: return Backward(taken, truncated = false)
            if (spent + element.length > budget && taken.isNotEmpty()) {
                return Backward(taken, truncated = true)
            }
            taken += element
            spent += element.length + PARAGRAPH_SEPARATOR.length
        }
        return Backward(taken, truncated = previous() != null)
    }

    private data class Backward(val elements: List<String>, val truncated: Boolean)

    private fun String.normalised(): String = lowercase().replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val PARAGRAPH_SEPARATOR = "\n\n"
        const val OMITTED_NOTICE = "[Earlier chapters omitted.]\n\n"

        /** Enough of the selection to identify its element without tripping over markup. */
        const val MATCH_CHARS = 60

        /** A selection is never far from the top of the page; this only bounds a failed match. */
        const val MAX_FORWARD_ELEMENTS = 40
    }
}
