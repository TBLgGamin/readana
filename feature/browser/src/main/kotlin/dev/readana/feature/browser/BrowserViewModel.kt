package dev.readana.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.CapturedDownload
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloadManager: BookDownloadManager,
    private val messages: MessageBus
) : ViewModel() {
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
            messages.show(outcome.toMessage())
            // The state now says only "done, close me"; the wording is the app shell's business.
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun onProgress(progress: Int) {
        _uiState.update { it.copy(loadProgress = progress) }
    }

    fun consumeResult() {
        _uiState.update { it.copy(finished = false) }
    }

    private fun EnqueueOutcome.toMessage(): UiMessage = when (this) {
        is EnqueueOutcome.Enqueued -> UiMessage.text(R.string.feature_browser_queued, job.title)

        is EnqueueOutcome.AlreadyInLibrary ->
            UiMessage.text(R.string.feature_browser_in_library, book.title)

        is EnqueueOutcome.AlreadyQueued ->
            UiMessage.text(R.string.feature_browser_already_queued, job.title)

        is EnqueueOutcome.Failed -> UiMessage.text(R.string.feature_browser_failed, error.message)
    }
}

data class BrowserUiState(
    /** 0..100, as reported by the WebView. */
    val loadProgress: Int = 0,
    /** The capture has been handed to the queue, so the browser has nothing left to do. */
    val finished: Boolean = false
)
