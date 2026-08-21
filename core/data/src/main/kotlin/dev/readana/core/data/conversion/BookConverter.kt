package dev.readana.core.data.conversion

import android.content.Context
import android.net.Uri
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
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.data.storage.BookStorage
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionInfo
import dev.readana.core.model.ConversionReport
import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.pdf2epub.ConversionProgress
import dev.readana.engine.pdf2epub.ConversionReport as EngineReport
import dev.readana.engine.pdf2epub.PdfToEpubConverter
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Converts one book's PDF into an EPUB and points its library entry at the result. */
interface BookConverter {
    suspend fun convert(bookId: String, onProgress: (ConversionProgress) -> Unit = {}): AppResult<Book>
}

@Singleton
class DefaultBookConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val storage: BookStorage,
    private val settingsRepository: SettingsRepository,
    private val converterProvider: Provider<PdfToEpubConverter>,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : BookConverter {
    override suspend fun convert(bookId: String, onProgress: (ConversionProgress) -> Unit): AppResult<Book> =
        suspendRunCatching { withContext(ioDispatcher) { run(bookId, onProgress) } }
            .getOrElse { AppError.Unexpected("Could not convert the book: ${it.message}", it).asFailure() }

    private suspend fun run(bookId: String, onProgress: (ConversionProgress) -> Unit): AppResult<Book> {
        val book = libraryRepository.getBook(bookId)
            ?: return AppError.NotFound("No book with id $bookId").asFailure()
        // Re-converting works from the kept original, not from the EPUB produced last time.
        val sourceUri = book.conversion?.originalFileUri
            ?: book.fileUri.takeIf { book.format == BookFormat.PDF }
            ?: return AppError.Unsupported("“${book.title}” is not a PDF").asFailure()

        val settings = settingsRepository.conversionSettings.first()
        val temp = File.createTempFile("convert-", ".epub", context.cacheDir)
        try {
            val report = when (val outcome = write(book, sourceUri, temp, settings, onProgress)) {
                is AppResult.Failure -> return outcome
                is AppResult.Success -> outcome.value
            }
            val stored = storage.store(
                Uri.fromFile(temp).toString(),
                "${book.title.toFileBase()}.${BookFormat.EPUB.extension}",
                BookFormat.EPUB.mimeType
            )
            // The previous EPUB is dead weight once a new one exists.
            book.conversion?.let { storage.delete(book.fileUri) }

            val converted = book.copy(
                format = BookFormat.EPUB,
                fileUri = stored.uri,
                // Reading position came from the PDF or an older conversion, so it no longer maps.
                progress = null,
                conversion = ConversionInfo(
                    originalFileUri = sourceUri,
                    convertedAt = Instant.now(),
                    report = report.toModel()
                )
            )
            libraryRepository.upsert(converted)
            return converted.asSuccess()
        } finally {
            temp.delete()
        }
    }

    private fun write(
        book: Book,
        sourceUri: String,
        target: File,
        settings: dev.readana.core.model.ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): AppResult<EngineReport> {
        val input = context.contentResolver.openInputStream(sourceUri.toUri())
            ?: return AppError.NotFound("The original file is gone: $sourceUri").asFailure()
        return input.use { source ->
            target.outputStream().use { output ->
                converterProvider.get().convert(
                    input = source,
                    output = output,
                    metadata = EpubMetadata(
                        title = book.title,
                        language = book.language ?: DEFAULT_LANGUAGE,
                        authors = book.authors
                    ),
                    options = settings.toReflowOptions(),
                    onProgress = onProgress
                )
            }
        }
    }

    private fun EngineReport.toModel() = ConversionReport(
        pages = pages,
        blocks = blocks,
        headings = headings,
        chapters = chapters,
        images = images,
        multiColumnPages = multiColumnPages,
        outlineUsed = outlineUsed,
        warnings = warnings + listOfNotNull(
            PROGRESS_RESET_WARNING.takeIf { pages > 0 }
        ),
        durationMillis = durationMillis
    )

    private fun String.toFileBase(): String =
        replace(ILLEGAL_FILE_CHARS, "").trim().ifEmpty { "book" }.take(MAX_BASE_LENGTH)

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
        const val MAX_BASE_LENGTH = 120
        const val PROGRESS_RESET_WARNING = "Reading position was reset: the converted text is paginated differently."
        val ILLEGAL_FILE_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    }
}
