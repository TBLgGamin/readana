package dev.readana.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.model.Bookmark
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBookmarksRepositoryTest {
    private lateinit var database: ReadanaDatabase
    private lateinit var repository: RoomBookmarksRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReadanaDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomBookmarksRepository(database.bookmarkDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores a bookmark and gives it an id`() = runTest {
        val stored = repository.add(bookmark(progression = 0.4))

        assertTrue(stored.id > 0)
        assertEquals(0.4, stored.progression)
    }

    @Test
    fun `observes one book's bookmarks in reading order`() = runTest {
        repository.observeForBook("book-1").test {
            assertEquals(emptyList(), awaitItem())

            repository.add(bookmark(progression = 0.8, title = "Later"))
            assertEquals(listOf("Later"), awaitItem().map { it.title })

            repository.add(bookmark(progression = 0.2, title = "Earlier"))
            assertEquals(listOf("Earlier", "Later"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `ignores bookmarks belonging to another book`() = runTest {
        repository.add(bookmark(bookId = "book-2"))

        repository.observeForBook("book-1").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `removes a bookmark`() = runTest {
        val stored = repository.add(bookmark())

        repository.remove(stored.id)

        repository.observeForBook("book-1").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    private fun bookmark(bookId: String = "book-1", progression: Double? = 0.5, title: String? = "Chapter 1") =
        Bookmark(
            id = Bookmark.UNSAVED_ID,
            bookId = bookId,
            locatorJson = """{"href":"ch1.xhtml"}""",
            progression = progression,
            title = title,
            createdAt = Instant.ofEpochMilli(1_000)
        )
}
