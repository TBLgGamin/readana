package dev.readana.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.ReadingProgress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryTest {
    private lateinit var database: ReadanaDatabase
    private lateinit var repository: RoomLibraryRepository

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                ReadanaDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        repository = RoomLibraryRepository(database.bookDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `round-trips a book through the database`() = runTest {
        val book = book("1")

        repository.upsert(book)

        assertEquals(book, repository.getBook("1"))
    }

    @Test
    fun `observes additions, progress updates and removals`() = runTest {
        repository.books.test {
            assertEquals(emptyList(), awaitItem())

            repository.upsert(book("1"))
            assertEquals(listOf("1"), awaitItem().map { it.id })

            repository.updateProgress(
                "1",
                ReadingProgress("{\"href\":\"ch1\"}", 0.25f),
                Instant.ofEpochMilli(2_000)
            )
            val updated = assertNotNull(awaitItem().single().progress)
            assertEquals(0.25f, updated.fraction)

            repository.remove("1")
            assertEquals(emptyList(), awaitItem())
        }
        assertNull(repository.getBook("1"))
    }

    @Test
    fun `most recently opened books come first`() = runTest {
        repository.upsert(book("old", addedAt = 1_000, lastOpenedAt = null))
        repository.upsert(book("recent", addedAt = 500, lastOpenedAt = 5_000))

        repository.books.test {
            assertEquals(listOf("recent", "old"), awaitItem().map { it.id })
        }
    }

    private fun book(id: String, addedAt: Long = 1_000, lastOpenedAt: Long? = null) = Book(
        id = id,
        title = "Title $id",
        authors = listOf("Ann Author", "Bob Writer"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "content://readana/$id.epub",
        coverUri = null,
        sizeBytes = 1234,
        addedAt = Instant.ofEpochMilli(addedAt),
        lastOpenedAt = lastOpenedAt?.let(Instant::ofEpochMilli),
        progress = null,
        origin = BookOrigin("gutenberg", "1342", BookFormat.PDF)
    )
}
