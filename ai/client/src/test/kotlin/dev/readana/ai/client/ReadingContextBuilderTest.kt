package dev.readana.ai.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ReadingContextBuilderTest {
    private val book = listOf(
        "Chapter one. Call me Ishmael.",
        "Some years ago, never mind how long precisely.",
        "I thought I would sail about a little.",
        "The selected sentence lives here.",
        "SPOILER: the whale wins.",
        "SPOILER: everyone drowns."
    )

    /** The reader is at element 3; everything from index 4 on is unread. */
    private fun walker(anchor: Int = 3) = ListWalker(book, anchor)

    @Test
    fun `a recap never reads past the current page`() = runTest {
        val context = ReadingContextBuilder(budgetChars = 10_000).build(walker())

        assertFalse(context.text.contains("SPOILER"))
        assertTrue(context.text.startsWith("Chapter one."))
        assertFalse(context.text.contains("The selected sentence"))
    }

    @Test
    fun `a selection is included and nothing after it`() = runTest {
        val context = ReadingContextBuilder(budgetChars = 10_000)
            .build(walker(), selection = "the selected SENTENCE lives")

        assertTrue(context.text.endsWith("The selected sentence lives here."))
        assertFalse(context.text.contains("SPOILER"))
    }

    @Test
    fun `an unrecognised selection still stops at the current element`() = runTest {
        val context = ReadingContextBuilder(budgetChars = 10_000)
            .build(walker(), selection = "words that are not in this book at all")

        // Falls back to the element the reader is looking at, never to the pages after it.
        assertTrue(context.text.endsWith("The selected sentence lives here."))
        assertFalse(context.text.contains("SPOILER"))
    }

    @Test
    fun `the budget drops the earliest text and says so`() = runTest {
        val context = ReadingContextBuilder(budgetChars = 60).build(walker())

        assertTrue(context.truncatedAtStart)
        assertTrue(context.text.startsWith("[Earlier chapters omitted.]"))
        assertFalse(context.text.contains("Call me Ishmael"))
        assertTrue(context.text.contains("I thought I would sail"))
    }

    @Test
    fun `a book read from the very beginning is not marked truncated`() = runTest {
        val context = ReadingContextBuilder(budgetChars = 10_000).build(ListWalker(book, anchor = 1))

        assertFalse(context.truncatedAtStart)
        assertEquals("Chapter one. Call me Ishmael.", context.text)
    }

    /** Walks a list of paragraphs from [anchor], the first element visible on the page. */
    private class ListWalker(private val elements: List<String>, anchor: Int) : TextWalker {
        private var forward = anchor - 1
        private var backward = anchor

        override suspend fun next(): String? {
            forward++
            return elements.getOrNull(forward)
        }

        override suspend fun previous(): String? {
            backward--
            return elements.getOrNull(backward)
        }
    }
}
