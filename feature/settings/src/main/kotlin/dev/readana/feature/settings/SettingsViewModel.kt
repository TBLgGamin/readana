package dev.readana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
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
        combine(
            settingsRepository.sourceSettings,
            settingsRepository.conversionSettings,
            settingsRepository.libraryFolderUri
        ) { sourceSettings, conversionSettings, libraryFolderUri ->
            SettingsUiState.Loaded(
                sources = sourceSettings,
                conversion = conversionSettings,
                libraryFolderUri = libraryFolderUri,
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

    /** Persisted SAF tree URI of the library folder; null puts new books back in app storage. */
    fun setLibraryFolder(uri: String?) {
        viewModelScope.launch { settingsRepository.setLibraryFolderUri(uri) }
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) = updateSources {
        it.withSourceEnabled(sourceId, enabled)
    }

    /** One mirror per line; blank input restores the built-in defaults. */
    fun setAnnasMirrors(text: String) = updateSources {
        it.copy(annasMirrors = text.lines().map(String::trim).filter(String::isNotEmpty))
    }

    fun setAnnasSecretKey(key: String) = updateSources {
        val trimmed = key.trim().ifEmpty { null }
        if (trimmed == it.annasSecretKey) {
            it
        } else {
            // The allowance belonged to the old key; showing it against a new one would be a lie.
            it.copy(annasSecretKey = trimmed, annasDownloadsLeft = null, annasDownloadsPerDay = null)
        }
    }

    fun setAutoConvertPdf(enabled: Boolean) = updateConversion { it.copy(autoConvertPdf = enabled) }

    fun setRemoveHeadersAndFooters(enabled: Boolean) = updateConversion {
        it.copy(removeHeadersAndFooters = enabled)
    }

    fun setHeadingSensitivity(sensitivity: HeadingSensitivity) = updateConversion {
        it.copy(headingSensitivity = sensitivity)
    }

    fun setExtractImages(enabled: Boolean) = updateConversion { it.copy(extractImages = enabled) }

    private fun updateConversion(transform: (ConversionSettings) -> ConversionSettings) {
        viewModelScope.launch { settingsRepository.updateConversionSettings(transform) }
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

    data class Loaded(
        val sources: SourceSettings,
        val conversion: ConversionSettings,
        val sourceToggles: List<SourceToggle>,
        val libraryFolderUri: String? = null
    ) : SettingsUiState
}
