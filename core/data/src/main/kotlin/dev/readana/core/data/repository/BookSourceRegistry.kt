package dev.readana.core.data.repository

import dev.readana.core.model.CatalogRef
import dev.readana.core.model.SourceSettings
import dev.readana.source.api.BookSource
import dev.readana.source.api.SourceId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Turns a catalog the user added into a source. Implemented by the OPDS module's binding. */
fun interface CatalogSourceFactory {
    fun create(catalog: CatalogRef): BookSource
}

/**
 * Every source the app can search: the ones compiled in (bound into the Hilt multibinding set)
 * plus one per catalog the user added. Catalogs come and go while the app runs, so the list is a
 * [Flow] rather than something fixed at construction.
 */
@Singleton
class BookSourceRegistry @Inject constructor(
    builtIn: Set<@JvmSuppressWildcards BookSource>,
    private val catalogFactory: CatalogSourceFactory,
    private val settings: SettingsRepository
) {
    private val builtInSources: List<BookSource> = builtIn.sortedBy { it.displayName }

    val sources: Flow<List<BookSource>> = settings.sourceSettings.map { it.allSources() }

    /** Sources the user has enabled (or that are on by default and not switched off). */
    val enabledSources: Flow<List<BookSource>> = settings.sourceSettings.map { stored ->
        stored.allSources().filter { stored.isEnabled(it.id.value, it.enabledByDefault) }
    }

    suspend fun all(): List<BookSource> = sources.first()

    suspend fun enabled(): List<BookSource> = enabledSources.first()

    suspend fun byId(id: SourceId): BookSource? = all().firstOrNull { it.id == id }

    private fun SourceSettings.allSources(): List<BookSource> =
        (builtInSources + catalogs.map(catalogFactory::create)).sortedBy { it.displayName }
}
