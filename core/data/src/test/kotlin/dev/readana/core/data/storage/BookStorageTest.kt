package dev.readana.core.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.data.FakeSettingsRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var storage: BookStorage

    @Before
    fun setUp() {
        storage = BookStorage(context, FakeSettingsRepository(), UnconfinedTestDispatcher())
    }

    @Test
    fun `copies a file into app storage`() = runTest {
        val source = sourceFile("source.epub", "hello")

        val stored = storage.store(source, "Title - Author.epub", "application/epub+zip")

        val file = File(assertNotNull(stored.uri.toUri().path))
        assertTrue(file.exists())
        assertEquals("hello", file.readText())
        assertEquals("hello".length.toLong(), stored.sizeBytes)
        assertEquals("Title - Author.epub", file.name)
    }

    @Test
    fun `keeps both books when names collide`() = runTest {
        val source = sourceFile("source.epub", "hello")

        val first = storage.store(source, "Title.epub", "application/epub+zip")
        val second = storage.store(source, "Title.epub", "application/epub+zip")

        assertEquals("Title.epub", File(assertNotNull(first.uri.toUri().path)).name)
        assertEquals("Title (1).epub", File(assertNotNull(second.uri.toUri().path)).name)
    }

    @Test
    fun `deletes a stored file`() = runTest {
        val stored = storage.store(sourceFile("source.epub", "bye"), "Gone.epub", "application/epub+zip")
        val file = File(assertNotNull(stored.uri.toUri().path))

        storage.delete(stored.uri)

        assertFalse(file.exists())
    }

    @Test
    fun `writes a cover next to the app files`() = runTest {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)

        val coverUri = assertNotNull(storage.writeCover("book-1", bitmap))

        val cover = File(assertNotNull(coverUri.toUri().path))
        assertTrue(cover.exists())
        assertEquals("book-1.png", cover.name)
    }

    private fun sourceFile(name: String, content: String): String {
        val file = File(context.cacheDir, name).apply { writeText(content) }
        return Uri.fromFile(file).toString()
    }

    private fun String.toUri(): Uri = Uri.parse(this)
}
