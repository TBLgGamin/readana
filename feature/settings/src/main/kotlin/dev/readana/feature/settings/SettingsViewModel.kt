package dev.readana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import dev.readana.core.model.SourceSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sources: BookSourceRegistry
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.readerSettings, settingsRepository.sourceSettings) {
                reader,
                sourceSettings
            ->
            SettingsUiState.Loaded(
                reader = reader,
                sources = sourceSettings,
                sourceToggles = sources.all.map { source ->
                    SourceToggle(
                        id = source.id.value,
                        name = source.displayName,
                        enabled = sourceSettings.isEnabled(
                            source.id.value,
                            source.enabledByDefault
                        )
                    )
                }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState.Loading
        )

    fun setFontScale(scale: Float) = updateReader {
        it.copy(fontScale = scale.coerceIn(ReaderSettings.FONT_SCALE_RANGE))
    }

    fun setTheme(theme: ReaderTheme) = updateReader { it.copy(theme = theme) }

    fun setPageTurnMode(mode: PageTurnMode) = updateReader { it.copy(pageTurnMode = mode) }

    fun setKeepScreenOn(enabled: Boolean) = updateReader { it.copy(keepScreenOn = enabled) }

    fun setVolumeKeysTurnPages(enabled: Boolean) = updateReader {
        it.copy(volumeKeysTurnPages = enabled)
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) = updateSources {
        it.withSourceEnabled(sourceId, enabled)
    }

    /** One mirror per line; blank input restores the built-in defaults. */
    fun setAnnasMirrors(text: String) = updateSources {
        it.copy(annasMirrors = text.lines().map(String::trim).filter(String::isNotEmpty))
    }

    fun setAnnasSecretKey(key: String) = updateSources {
        it.copy(annasSecretKey = key.trim().ifEmpty { null })
    }

    private fun updateReader(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.updateReaderSettings(transform) }
    }

    private fun updateSources(transform: (SourceSettings) -> SourceSettings) {
        viewModelScope.launch { settingsRepository.updateSourceSettings(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class SourceToggle(val id: String, val name: String, val enabled: Boolean)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Loaded(val reader: ReaderSettings, val sources: SourceSettings, val sourceToggles: List<SourceToggle>) :
        SettingsUiState
}
