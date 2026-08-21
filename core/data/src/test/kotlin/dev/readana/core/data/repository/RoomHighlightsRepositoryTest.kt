package dev.readana.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.model.Highlight
import dev.readana.core.model.HighlightStyle
import dev.readana.core.model.HighlightTint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHighlightsRepositoryTest {
    private lateinit var database: ReadanaDatabase
    private lateinit var repository: RoomHighlightsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReadanaDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomHighlightsRepository(database.highlightDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores a highlight and gives it an id`() = runTest {
        val stored = repository.add(highlight(), progression = 0.3)

        assertTrue(stored.id > 0)
        assertEquals("call me Ishmael", stored.text)
        assertNull(stored.note)
    }

    @Test
    fun `orders highlights by where they sit in the book`() = runTest {
        repository.observeForBook("book-1").test {
            assertEquals(emptyList(), awaitItem())

            repository.add(highlight(text = "later"), progression = 0.9)
            assertEquals(listOf("later"), awaitItem().map { it.text })

            repository.add(highlight(text = "earlier"), progression = 0.1)
            assertEquals(listOf("earlier", "later"), awaitItem().map { it.text })
        }
    }

    @Test
    fun `updating the note and tint keeps the reading order`() = runTest {
        repository.add(highlight(text = "first"), progression = 0.1)
        val second = repository.add(highlight(text = "second"), progression = 0.9)

        repository.update(second.copy(note = "why this matters", tint = HighlightTint.Green))

        repository.observeForBook("book-1").test {
            val stored = awaitItem()
            assertEquals(listOf("first", "second"), stored.map { it.text })
            val updated = stored.last()
            assertEquals("why this matters", updated.note)
            assertEquals(HighlightTint.Green, updated.tint)
        }
    }

    @Test
    fun `removes a highlight`() = runTest {
        val stored = repository.add(highlight(), progression = null)

        repository.remove(stored.id)

        repository.observeForBook("book-1").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    private fun highlight(text: String = "call me Ishmael") = Highlight(
        id = Highlight.UNSAVED_ID,
        bookId = "book-1",
        locatorJson = """{"href":"ch1.xhtml"}""",
        text = text,
        tint = HighlightTint.Yellow,
        style = HighlightStyle.Highlight,
        note = null,
        createdAt = Instant.ofEpochMilli(1_000)
    )
}
