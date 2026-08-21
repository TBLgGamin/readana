package dev.readana.ai.client

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SseReaderTest {
    @Test
    fun `reads named events and joins multi-line data`() = runTest {
        val stream = """
            event: first
            data: {"a":1}

            data: line one
            data: line two

        """.trimIndent()

        assertEquals(
            listOf(
                SseEvent("first", """{"a":1}"""),
                SseEvent(null, "line one\nline two")
            ),
            stream.events()
        )
    }

    @Test
    fun `ignores comments and tolerates carriage returns`() = runTest {
        val stream = ": keep-alive\r\n\r\ndata: hello\r\n\r\n"

        assertEquals(listOf(SseEvent(null, "hello")), stream.events())
    }

    @Test
    fun `stops at the done sentinel`() = runTest {
        val stream = "data: first\n\ndata: [DONE]\n\ndata: never\n\n"

        assertEquals(listOf(SseEvent(null, "first")), stream.events())
    }

    @Test
    fun `emits a trailing event that has no blank line after it`() = runTest {
        assertEquals(listOf(SseEvent(null, "last")), "data: last".events())
    }

    private suspend fun String.events(): List<SseEvent> = buildList {
        SseReader.read(ByteReadChannel(this@events)) { add(it) }
    }
}
