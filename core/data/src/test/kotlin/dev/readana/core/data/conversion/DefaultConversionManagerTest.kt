package dev.readana.core.data.conversion

import app.cash.turbine.test
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.engine.pdf2epub.ConversionPhase
import dev.readana.engine.pdf2epub.ConversionProgress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class DefaultConversionManagerTest {
    @Test
    fun `reports success with the book's report`() = runTest {
        val converter = FakeConverter { book("1").asSuccess() }
        val manager = DefaultConversionManager(converter, backgroundScope)

        manager.events.test {
            manager.enqueue("1")
            val event = assertIs<ConversionEvent.Succeeded>(awaitItem())
            assertEquals("1", event.book.id)
        }
    }

    @Test
    fun `reports a failure without stopping the queue`() = runTest {
        val converter = FakeConverter { id ->
            if (id == "1") AppError.Parse("broken").asFailure() else book(id).asSuccess()
        }
        val manager = DefaultConversionManager(converter, backgroundScope)

        manager.events.test {
            manager.enqueue("1")
            manager.enqueue("2")

            assertIs<AppError.Parse>(assertIs<ConversionEvent.Failed>(awaitItem()).error)
            assertEquals("2", assertIs<ConversionEvent.Succeeded>(awaitItem()).book.id)
        }
    }

    @Test
    fun `conversions run one at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val converter = FakeConverter { id ->
            started += id
            if (id == "1") gate.await()
            book(id).asSuccess()
        }
        val manager = DefaultConversionManager(converter, backgroundScope)

        manager.enqueue("1")
        manager.enqueue("2")
        runCurrent()

        // PDFBox plus bitmap decoding twice over is a memory hazard, so the second must wait.
        assertEquals(listOf("1"), started)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("1", "2"), started)
    }

    @Test
    fun `progress is published while a book converts and cleared afterwards`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val converter = FakeConverter(
            onProgress = { report -> report(ConversionProgress(ConversionPhase.Extracting, 3, 10)) }
        ) { id ->
            gate.await()
            book(id).asSuccess()
        }
        val manager = DefaultConversionManager(converter, backgroundScope)

        manager.enqueue("1")
        runCurrent()
        assertEquals(3, progressFor(manager, "1").currentPage)

        gate.complete(Unit)
        runCurrent()
        assertTrue(manager.active.value.isEmpty())
    }

    @Test
    fun `convertAndWait returns the outcome to its caller`() = runTest {
        val converter = FakeConverter { book(it).asSuccess() }
        val manager = DefaultConversionManager(converter, backgroundScope)

        val outcome = manager.convertAndWait("7")

        assertEquals("7", assertIs<AppResult.Success<Book>>(outcome).value.id)
    }

    private fun progressFor(manager: ConversionManager, id: String): ConversionProgress =
        requireNotNull(manager.active.value[id]) { "no progress for $id" }

    private fun book(id: String) = Book(
        id = id,
        title = "Book $id",
        authors = emptyList(),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/$id.epub",
        coverUri = null,
        sizeBytes = 1,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    private class FakeConverter(
        private val onProgress: ((ConversionProgress) -> Unit) -> Unit = {},
        private val outcome: suspend (String) -> AppResult<Book>
    ) : BookConverter {
        override suspend fun convert(bookId: String, onProgress: (ConversionProgress) -> Unit): AppResult<Book> {
            this.onProgress(onProgress)
            return outcome(bookId)
        }
    }
}
