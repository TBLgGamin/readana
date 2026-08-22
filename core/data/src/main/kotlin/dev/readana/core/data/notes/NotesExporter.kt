package dev.readana.core.data.notes

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.model.Book
import dev.readana.core.model.Highlight
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Turns a book's notes into a file another app can be handed. */
interface NotesExporter {
    /**
     * Writes [highlights] out as Markdown and returns a `content://` URI for it, or null when
     * there is nothing to export or the write failed.
     */
    suspend fun export(book: Book, highlights: List<Highlight>): String?
}

@Singleton
class FileNotesExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : NotesExporter {
    override suspend fun export(book: Book, highlights: List<Highlight>): String? {
        if (highlights.isEmpty()) return null
        return withContext(ioDispatcher) {
            suspendRunCatching {
                val directory = context.cacheDir.resolve(EXPORTS_DIRECTORY)
                directory.mkdirs()
                // One file per book, overwritten: exports are a hand-off, not a history.
                val file = directory.resolve(notesFileName(book))
                file.writeText(notesMarkdown(book, highlights))
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    .toString()
            }.onFailure { Timber.w(it, "Could not export notes for %s", book.id) }.getOrNull()
        }
    }

    private companion object {
        /** Must match the cache-path in `core_data_file_paths.xml`. */
        const val EXPORTS_DIRECTORY = "exports"
    }
}
