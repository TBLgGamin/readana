package dev.readana.core.data.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Bookmarks (v2), highlights (v3), downloads (v4) and assistant conversations (v6) were added
 * later; existing libraries must survive.
 */
@RunWith(AndroidJUnit4::class)
class ReadanaDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File(context.cacheDir, "migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = ReadanaDatabase::class
    )

    @Test
    fun `upgrading to the newest version keeps the books and adds the annotation tables`() {
        helper.createDatabase(version = 1).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (id, title, authors, language, format, file_uri, cover_uri,
                    size_bytes, added_at, last_opened_at, progress_locator, progress_fraction,
                    source_id, source_record_id, original_format)
                VALUES ('1', 'Moby Dick', 'Herman Melville', 'en', 'EPUB',
                    'file:///books/moby.epub', NULL, 10, 1000, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(version = 6).use { connection ->
            connection.prepare("SELECT title FROM books").use { statement ->
                assertTrue(statement.step())
                assertEquals("Moby Dick", statement.getText(0))
            }
            listOf("bookmarks", "highlights", "downloads", "ai_threads", "ai_messages").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { statement ->
                    assertTrue(statement.step())
                    assertEquals(0, statement.getLong(0).toInt())
                }
            }
        }
    }
}
