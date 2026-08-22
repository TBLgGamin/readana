package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.navigator.media.common.MediaNavigator
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.publication.Locator

/** What read-aloud is doing, as the reader sees it. */
internal data class TtsUiState(
    /** True while a voice is speaking or paused mid-book — that is, while the toolbar belongs. */
    val active: Boolean = false,
    val playing: Boolean = false,
    /** The sentence being spoken, so the page can follow it and mark it. */
    val utterance: Locator? = null,
    val speed: Float = 1.0f,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false
)

/**
 * Read-aloud, kept apart from [ReaderViewModel] for the same reason the assistant is: that class is
 * already at its limit, and the two share only the session store and the book id.
 *
 * Playback stops when the reader leaves. Reading aloud in the background needs a media session and
 * a foreground service, which this app does not have — so rather than pretend, the voice stops with
 * the screen it belongs to.
 */
@HiltViewModel
internal class ReaderTtsViewModel @Inject constructor(
    private val sessionStore: ReaderSessionStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TtsUiState())
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var navigator: ReadanaTtsNavigator? = null
    private var followJob: Job? = null

    /** Starts reading aloud from [from], or resumes if a voice is already going. */
    fun start(from: Locator?) {
        if (navigator != null) {
            resume()
            return
        }
        val create = sessionStore.session?.createTts ?: return
        viewModelScope.launch {
            val speed = settingsRepository.readerSettings.first().ttsSpeed
            val created = create(from, speed.toDouble()) { stop() } ?: return@launch
            navigator = created
            _uiState.update { it.copy(active = true, speed = speed) }
            follow(created)
            created.play()
        }
    }

    fun pause() {
        navigator?.pause()
    }

    fun resume() {
        navigator?.play()
    }

    fun next() {
        navigator?.skipToNextUtterance()
    }

    fun previous() {
        navigator?.skipToPreviousUtterance()
    }

    /** Ends the session and gives the audio focus and the engine back. */
    fun stop() {
        followJob?.cancel()
        followJob = null
        navigator?.close()
        navigator = null
        _uiState.value = TtsUiState()
    }

    /** Voice speed is a preference of the reader's, not of the book, so it is stored globally. */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        _uiState.update { it.copy(speed = clamped) }
        navigator?.submitPreferences(AndroidTtsPreferences(speed = clamped.toDouble()))
        viewModelScope.launch {
            settingsRepository.updateReaderSettings { it.copy(ttsSpeed = clamped) }
        }
    }

    /** Steps the speed through a short round of sensible values rather than offering a slider. */
    fun cycleSpeed() {
        val current = _uiState.value.speed
        val next = SPEEDS.firstOrNull { it > current + SPEED_EPSILON } ?: SPEEDS.first()
        setSpeed(next)
    }

    override fun onCleared() = stop()

    private fun follow(created: ReadanaTtsNavigator) {
        followJob?.cancel()
        followJob = viewModelScope.launch {
            launch {
                created.location.collect { location ->
                    _uiState.update {
                        it.copy(
                            utterance = location.utteranceLocator,
                            hasPrevious = created.hasPreviousUtterance(),
                            hasNext = created.hasNextUtterance()
                        )
                    }
                }
            }
            created.playback.collect { playback ->
                _uiState.update {
                    it.copy(
                        playing = playback.playWhenReady && playback.state is MediaNavigator.State.Ready,
                        active = playback.state !is MediaNavigator.State.Ended
                    )
                }
            }
        }
    }

    private companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        const val SPEED_EPSILON = 0.01f
        val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}
