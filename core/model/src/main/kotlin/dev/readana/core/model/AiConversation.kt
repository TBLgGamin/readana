package dev.readana.core.model

import java.time.Instant

/** What a conversation was started for. */
enum class AiThreadKind {
    /** The reader asked something about a passage. */
    Ask,

    /** One-tap explanation of a passage. */
    Explain,

    /** "Where was I?" — a recap of the book so far. */
    Recap
}

/**
 * One conversation about a book. The context is rendered once, when the thread starts, and stored
 * verbatim: follow-up questions resend the same bytes, so the answer never learns about text the
 * reader had not reached, and provider prompt caches keep hitting.
 */
data class AiThread(
    val id: Long,
    val bookId: String,
    val kind: AiThreadKind,
    /** Short label for the conversations list. */
    val title: String,
    /** The selected passage, for [AiThreadKind.Ask] and [AiThreadKind.Explain]. */
    val quote: String?,
    /** The already-read text handed to the model, exactly as it was sent. */
    val contextText: String,
    /** Where the reader was when the thread started, for reuse checks and display. */
    val chapterTitle: String?,
    val coveredFraction: Float?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        const val UNSAVED_ID = 0L

        /** A stored recap is reused while the reading position has barely moved. */
        const val RECAP_REUSE_EPSILON = 0.005f
    }
}

enum class AiRole { User, Assistant }

data class AiMessage(val id: Long, val threadId: Long, val role: AiRole, val text: String, val createdAt: Instant) {
    companion object {
        const val UNSAVED_ID = 0L
    }
}
