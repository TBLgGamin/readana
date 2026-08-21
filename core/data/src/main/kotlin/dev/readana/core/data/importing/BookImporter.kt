package dev.readana.core.data.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.storage.BookStorage
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.source.api.FileNames
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Outcome of importing one file. */
sealed interface ImportResult {
    data class Imported(val book: Book) : ImportResult

    /** The file was already in the library, so nothing was copied. */
    data class Duplicate(val existing: Book) : ImportResult
}

/** Brings outside files into the library, and removes them again. */
interface BookImporter {
    suspend fun import(uri: String): AppResult<ImportResult>

    suspend fun delete(bookId: String): AppResult<Unit>
}

@Singleton
class DefaultBookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: BookMetadataExtractor,
    private val storage: BookStorage,
    private val libraryRepository: LibraryRepository,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : BookImporter {
    override suspend fun import(uri: String): AppResult<ImportResult> =
        suspendRunCatching { withContext(ioDispatcher) { importFile(uri) } }
            .getOrElse { AppError.Unexpected("Cannot import $uri: ${it.message}", it).asFailure() }

    override suspend fun delete(bookId: String): AppResult<Unit> =
        suspendRunCatching { withContext(ioDispatcher) { deleteBook(bookId) } }
            .getOrElse { AppError.Unexpected("Cannot delete book: ${it.message}", it).asFailure() }

    private suspend fun importFile(uriString: String): AppResult<ImportResult> {
        val uri = uriString.toUri()
        val source = describe(uri)
        val format = resolveFormat(uri, source.displayName)
            ?: return AppError.Unsupported("Not an EPUB or PDF: ${source.displayName}").asFailure()
        // Metadata comes from the original file, before copying, so unreadable books cost nothing.
        val metadata = when (val extracted = metadataExtractor.extract(uriString)) {
            is AppResult.Failure -> return extracted
            is AppResult.Success -> extracted.value
        }
        val title = metadata.title ?: source.displayName.substringBeforeLast('.')
        source.sizeBytes?.let { size ->
            libraryRepository.findDuplicate(title, size)?.let { existing ->
                return ImportResult.Duplicate(existing).asSuccess()
            }
        }
        val fileName = FileNames.suggest(title, metadata.authors, format)
        val stored = storage.store(uriString, fileName, format.mimeType)
        val id = UUID.randomUUID().toString()
        val book = Book(
            id = id,
            title = title,
            authors = metadata.authors,
            language = metadata.language,
            format = format,
            fileUri = stored.uri,
            coverUri = metadata.cover?.let { storage.writeCover(id, it) },
            sizeBytes = stored.sizeBytes,
            addedAt = Instant.now(),
            lastOpenedAt = null,
            progress = null,
            origin = null
        )
        libraryRepository.upsert(book)
        return ImportResult.Imported(book).asSuccess()
    }

    private suspend fun deleteBook(bookId: String): AppResult<Unit> {
        val book = libraryRepository.getBook(bookId)
            ?: return AppError.NotFound("No book with id $bookId").asFailure()
        // Files are best effort: one we cannot delete must not keep the entry alive.
        suspendRunCatching { storage.delete(book.fileUri) }
        book.coverUri?.let { cover -> suspendRunCatching { storage.delete(cover) } }
        libraryRepository.remove(bookId)
        return Unit.asSuccess()
    }

    private fun resolveFormat(uri: Uri, displayName: String): BookFormat? =
        context.contentResolver.getType(uri)?.let(BookFormat::fromMimeType)
            ?: BookFormat.fromExtension(displayName.substringAfterLast('.', ""))

    private fun describe(uri: Uri): SourceFile = when (uri.scheme) {
        "file" -> {
            val file = uri.path?.let(::File)
            SourceFile(file?.name ?: FALLBACK_NAME, file?.takeIf { it.isFile }?.length())
        }

        else -> queryOpenable(uri)
    }

    private fun queryOpenable(uri: Uri): SourceFile {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        return cursor?.use {
            if (!it.moveToFirst()) return@use null
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            SourceFile(
                displayName = nameIndex.takeIf { index -> index >= 0 }?.let(it::getString)
                    ?: FALLBACK_NAME,
                sizeBytes = sizeIndex.takeIf { index -> index >= 0 && !it.isNull(index) }
                    ?.let(it::getLong)
            )
        } ?: SourceFile(FALLBACK_NAME, null)
    }

    private data class SourceFile(val displayName: String, val sizeBytes: Long?)

    private companion object {
        const val FALLBACK_NAME = "book"
    }
}
