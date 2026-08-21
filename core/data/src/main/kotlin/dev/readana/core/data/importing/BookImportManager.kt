package dev.readana.core.data.importing

import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happened to one queued import, for the UI to report. */
sealed interface ImportEvent {
    data class Succeeded(val book: Book) : ImportEvent

    data class Duplicate(val existing: Book) : ImportEvent

    data class Failed(val error: AppError) : ImportEvent
}

/**
 * Runs imports outside any screen's lifetime, so a book opened with Readana finishes importing
 * even if the user leaves the library. Events are best effort; the library list itself always
 * catches up through the database flow.
 */
interface BookImportManager {
    val activeImports: StateFlow<Int>

    val events: SharedFlow<ImportEvent>

    fun enqueue(uris: List<String>)
}

@Singleton
class DefaultBookImportManager @Inject constructor(
    private val importer: BookImporter,
    private val conversionManager: ConversionManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) : BookImportManager {
    private val _activeImports = MutableStateFlow(0)
    override val activeImports: StateFlow<Int> = _activeImports.asStateFlow()

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<ImportEvent> = _events.asSharedFlow()

    override fun enqueue(uris: List<String>) {
        uris.forEach { uri ->
            scope.launch {
                _activeImports.update { it + 1 }
                try {
                    val outcome = importer.import(uri)
                    _events.emit(outcome.toEvent())
                    outcome.convertIfPdf()
                } finally {
                    _activeImports.update { it - 1 }
                }
            }
        }
    }

    /**
     * A freshly imported PDF is queued for conversion; the original stays on disk, so a failure
     * just leaves a readable PDF behind.
     */
    private suspend fun AppResult<ImportResult>.convertIfPdf() {
        val book = ((this as? AppResult.Success)?.value as? ImportResult.Imported)?.book ?: return
        if (book.format != BookFormat.PDF) return
        if (!settingsRepository.conversionSettings.first().autoConvertPdf) return
        conversionManager.enqueue(book.id)
    }

    private fun AppResult<ImportResult>.toEvent(): ImportEvent = when (this) {
        is AppResult.Failure -> ImportEvent.Failed(error)

        is AppResult.Success -> when (val outcome = value) {
            is ImportResult.Imported -> ImportEvent.Succeeded(outcome.book)
            is ImportResult.Duplicate -> ImportEvent.Duplicate(outcome.existing)
        }
    }

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
