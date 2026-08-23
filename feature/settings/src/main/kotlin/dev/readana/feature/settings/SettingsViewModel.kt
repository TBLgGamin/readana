package dev.readana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.data.repository.SourceDefaults
import dev.readana.core.model.CatalogPreset
import dev.readana.core.model.CatalogRef
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.model.StoredQuota
import dev.readana.source.api.CatalogSource
import dev.readana.source.api.SourceConfigField
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    sources: BookSourceRegistry
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.sourceSettings,
            settingsRepository.conversionSettings,
            settingsRepository.readerSettings,
            settingsRepository.libraryFolderUri,
            sources.sources
        ) { sourceSettings, conversionSettings, readerSettings, libraryFolderUri, allSources ->
            SettingsUiState.Loaded(
                sources = sourceSettings,
                conversion = conversionSettings,
                reading = readerSettings,
                libraryFolderUri = libraryFolderUri,
                sourceRows = allSources.map { source ->
                    SourceRow(
                        id = source.id.value,
                        name = source.displayName,
                        summary = source.summary,
                        enabled = sourceSettings.isEnabled(source.id.value, source.enabledByDefault),
                        fields = source.configFields,
                        values = sourceSettings.configFor(source.id.value),
                        quota = sourceSettings.quotas[source.id.value],
                        // Only a catalog can be forgotten: a compiled-in source has no record to
                        // remove, only a switch to turn off.
                        isRemovable = source is CatalogSource &&
                            sourceSettings.catalogs.any { it.id == source.id.value }
                    )
                },
                // Presets already added are not offered again.
                availablePresets = SourceDefaults.CATALOG_PRESETS.filter { preset ->
                    sourceSettings.catalogs.none { it.presetId == preset.id }
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

    /**
     * Stores one configuration value. Changing a credential clears whatever allowance the source
     * last reported: that allowance belonged to the old account, and showing it against a new one
     * would be a lie.
     */
    fun setSourceConfig(sourceId: String, field: SourceConfigField, value: String) = updateSources {
        val updated = it.withConfigValue(sourceId, field.key, value)
        if (field.kind == SourceConfigField.Kind.Secret && value.trim() != it.configFor(sourceId)[field.key]) {
            updated.withQuota(sourceId, null)
        } else {
            updated
        }
    }

    fun addPresetCatalog(preset: CatalogPreset) = updateSources {
        it.withCatalog(
            CatalogRef(
                id = "catalog-${preset.id}",
                name = preset.name,
                feedUrl = preset.feedUrl,
                presetId = preset.id
            )
        )
    }

    /** Adds a catalog from a pasted feed URL. Returns false when the address is unusable. */
    fun addCatalog(name: String, feedUrl: String): Boolean {
        val url = feedUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val label = name.trim().ifEmpty { url.substringAfter("://").substringBefore('/') }
        updateSources {
            it.withCatalog(
                CatalogRef(id = "catalog-${UUID.randomUUID()}", name = label, feedUrl = url)
            )
        }
        return true
    }

    fun removeCatalog(catalogId: String) = updateSources { it.withoutCatalog(catalogId) }

    /** The defaults every book starts from; a book that has been adjusted keeps its own copy. */
    fun updateReadingDefaults(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.updateReaderSettings(transform) }
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

/**
 * One source as the settings page sees it. Everything a source needs configured travels with it,
 * so the screen renders any source — built in or added today — without knowing which it is.
 */
data class SourceRow(
    val id: String,
    val name: String,
    val summary: String?,
    val enabled: Boolean,
    val fields: List<SourceConfigField>,
    val values: Map<String, String>,
    val quota: StoredQuota?,
    val isRemovable: Boolean
) {
    val isConfigurable: Boolean get() = fields.isNotEmpty()
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Loaded(
        val sources: SourceSettings,
        val conversion: ConversionSettings,
        val reading: ReaderSettings,
        val sourceRows: List<SourceRow>,
        val availablePresets: List<CatalogPreset>,
        val libraryFolderUri: String? = null
    ) : SettingsUiState
}
