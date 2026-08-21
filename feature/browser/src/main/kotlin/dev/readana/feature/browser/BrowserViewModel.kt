package dev.readana.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.CapturedDownload
import dev.readana.core.data.downloads.EnqueueOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BrowserViewModel @Inject constructor(private val downloadManager: BookDownloadManager) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /**
     * Hands a file the page started downloading to the normal queue, cookies and all, so the
     * transfer, notification and import behave exactly like a member-API download.
     */
    internal fun onCapture(file: CapturedFile) {
        viewModelScope.launch {
            val outcome = downloadManager.enqueueCaptured(
                CapturedDownload(
                    sourceId = file.sourceId,
                    recordId = file.recordId,
                    title = file.title,
                    authors = file.authors,
                    format = file.format,
                    url = file.url,
                    headers = file.headers,
                    expectedSizeBytes = file.sizeBytes?.takeIf { it > 0 }
                )
            )
            _uiState.update { it.copy(result = outcome.toResult()) }
        }
    }

    fun onProgress(progress: Int) {
        _uiState.update { it.copy(loadProgress = progress) }
    }

    fun consumeResult() {
        _uiState.update { it.copy(result = null) }
    }

    private fun EnqueueOutcome.toResult(): BrowserResult = when (this) {
        is EnqueueOutcome.Enqueued -> BrowserResult.Queued(job.title)
        is EnqueueOutcome.AlreadyInLibrary -> BrowserResult.AlreadyInLibrary(book.title)
        is EnqueueOutcome.AlreadyQueued -> BrowserResult.AlreadyQueued(job.title)
        is EnqueueOutcome.Failed -> BrowserResult.Failed(error.message)
    }
}

data class BrowserUiState(
    /** 0..100, as reported by the WebView. */
    val loadProgress: Int = 0,
    val result: BrowserResult? = null
)

sealed interface BrowserResult {
    data class Queued(val title: String) : BrowserResult

    data class AlreadyInLibrary(val title: String) : BrowserResult

    data class AlreadyQueued(val title: String) : BrowserResult

    data class Failed(val message: String) : BrowserResult
}
