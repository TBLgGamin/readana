package dev.readana.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the shell around every screen needs: the counts its navigation wears, and the messages it
 * has to show on behalf of whichever screen raised them.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(downloadManager: BookDownloadManager, messageBus: MessageBus) :
    ViewModel() {
    /**
     * Anything still in the queue, paused and stalled ones included.
     *
     * The download queue lives on its own screen, so without a badge a transfer that fails while
     * the reader is elsewhere goes unnoticed until they happen to look.
     */
    val pendingDownloads: StateFlow<Int> = downloadManager.jobs
        .map { jobs -> jobs.count { it.isPending || it.isStalled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val messages: Flow<UiMessage> = messageBus.messages

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
