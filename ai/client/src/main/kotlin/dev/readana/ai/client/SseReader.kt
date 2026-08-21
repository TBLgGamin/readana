package dev.readana.ai.client

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/** One server-sent event: the optional `event:` name and the joined `data:` lines. */
internal data class SseEvent(val name: String?, val data: String)

/**
 * Minimal server-sent-events reader.
 *
 * Only the parts the chat APIs actually use are implemented: `event:` names (Anthropic dispatches
 * on them), `data:` lines joined with newlines, a blank line ends an event, `:` lines are
 * keep-alive comments, and `[DONE]` ends the stream (the OpenAI family's terminator).
 *
 * Kept independent of any provider so it can be unit-tested on its own.
 */
internal object SseReader {
    const val DONE = "[DONE]"

    /** Reads [channel] to the end, handing every complete event to [onEvent]. */
    suspend fun read(channel: ByteReadChannel, onEvent: suspend (SseEvent) -> Unit) {
        val pending = Pending()
        var reading = true
        while (reading) {
            val line = channel.readUTF8Line()?.trimEnd('\r')
            reading = when {
                line == null -> false

                line.isEmpty() -> pending.flushTo(onEvent)

                else -> {
                    pending.accept(line)
                    true
                }
            }
        }
        // A stream that ends without a blank line still has an event to hand over; one that
        // stopped at the terminator has already been emptied, so this is a no-op there.
        pending.flushTo(onEvent)
    }

    /** The event being assembled, one line at a time. */
    private class Pending {
        private var name: String? = null
        private val data = StringBuilder()

        fun accept(line: String) {
            if (line.startsWith(COMMENT)) return
            val field = line.substringBefore(FIELD_SEPARATOR)
            val value = line.substringAfter(FIELD_SEPARATOR, missingDelimiterValue = "").removePrefix(" ")
            when (field) {
                FIELD_EVENT -> name = value

                FIELD_DATA -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(value)
                }

                // id/retry and anything unknown carry nothing this app needs.
                else -> Unit
            }
        }

        /** Emits what has been collected. False once the terminator has been seen. */
        suspend fun flushTo(onEvent: suspend (SseEvent) -> Unit): Boolean {
            if (data.isEmpty() && name == null) return true
            val event = SseEvent(name, data.toString())
            name = null
            data.setLength(0)
            if (event.data.trim() == DONE) return false
            onEvent(event)
            return true
        }
    }

    private const val COMMENT = ":"
    private const val FIELD_SEPARATOR = ":"
    private const val FIELD_EVENT = "event"
    private const val FIELD_DATA = "data"
}
