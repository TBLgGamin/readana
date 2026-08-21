package dev.readana.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.filesDir, "settings-test.preferences_pb").apply { delete() }
        repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { file }
        )
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `reader settings survive a write and read`() = runTest {
        repository.updateReaderSettings { settings ->
            settings.copy(
                fontScale = 1.6f,
                fontFamily = ReaderFont.Serif,
                theme = ReaderTheme.Sepia,
                lineHeight = 1.9f,
                pageMargins = 2.5f,
                pageTurnMode = PageTurnMode.Scrolled,
                columns = ReaderColumns.Two,
                tapToTurnPages = false,
                volumeKeysTurnPages = true,
                keepScreenOn = false
            )
        }

        val stored = repository.readerSettings.first()
        assertEquals(1.6f, stored.fontScale)
        assertEquals(ReaderFont.Serif, stored.fontFamily)
        assertEquals(ReaderTheme.Sepia, stored.theme)
        assertEquals(1.9f, stored.lineHeight)
        assertEquals(2.5f, stored.pageMargins)
        assertEquals(PageTurnMode.Scrolled, stored.pageTurnMode)
        assertEquals(ReaderColumns.Two, stored.columns)
        assertEquals(false, stored.tapToTurnPages)
        assertEquals(true, stored.volumeKeysTurnPages)
        assertEquals(false, stored.keepScreenOn)
    }

    @Test
    fun `an unknown stored value falls back to the default`() = runTest {
        repository.updateReaderSettings { it.copy(columns = ReaderColumns.Two) }
        assertEquals(ReaderColumns.Two, repository.readerSettings.first().columns)
    }

    @Test
    fun `the library folder can be set and cleared`() = runTest {
        repository.setLibraryFolderUri("content://tree/books")
        assertEquals("content://tree/books", repository.libraryFolderUri.first())

        repository.setLibraryFolderUri(null)
        assertNull(repository.libraryFolderUri.first())
    }
}
