package dev.readana.core.data.importing

import app.cash.turbine.test
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.conversion.FakeConversionManager
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class DefaultBookImportManagerTest {
    private val conversions = FakeConversionManager()
    private val settings = FakeSettingsRepository()

    private val book = Book(
        id = "1",
        title = "Moby Dick",
        authors = listOf("Herman Melville"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/moby.epub",
        coverUri = null,
        sizeBytes = 10,
        addedAt = Instant.ofEpochMilli(1),
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    @Test
    fun `reports one event per queued file`() = runTest {
        val importer = FakeImporter(
            mapOf(
                "a" to ImportResult.Imported(book).asSuccess(),
                "b" to ImportResult.Duplicate(book).asSuccess(),
                "c" to AppError.Parse("broken").asFailure()
            )
        )
        val manager = DefaultBookImportManager(importer, conversions, settings, backgroundScope)

        manager.events.test {
            manager.enqueue(listOf("a", "b", "c"))

            val events = List(3) { awaitItem() }
            assertEquals(1, events.filterIsInstance<ImportEvent.Succeeded>().size)
            assertEquals(1, events.filterIsInstance<ImportEvent.Duplicate>().size)
            assertIs<AppError.Parse>(events.filterIsInstance<ImportEvent.Failed>().single().error)
        }
    }

    @Test
    fun `counts imports while they run and settles back to zero`() = runTest {
        val importer = FakeImporter(mapOf("a" to ImportResult.Imported(book).asSuccess()))
        val manager = DefaultBookImportManager(importer, conversions, settings, backgroundScope)

        manager.activeImports.test {
            assertEquals(0, awaitItem())

            manager.enqueue(listOf("a"))

            assertEquals(1, awaitItem())
            assertEquals(0, awaitItem())
        }
    }

    private class FakeImporter(private val outcomes: Map<String, AppResult<ImportResult>>) : BookImporter {
        override suspend fun import(uri: String, origin: BookOrigin?): AppResult<ImportResult> = outcomes.getValue(uri)

        override suspend fun delete(bookId: String): AppResult<Unit> = Unit.asSuccess()
    }
}
