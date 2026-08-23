package dev.readana.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.readana.core.data.repository.CatalogSourceFactory
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.source.annas.AnnasArchiveSource
import dev.readana.source.annas.AnnasConfig
import dev.readana.source.api.BookSource
import dev.readana.source.api.BrowserVerifier
import dev.readana.source.arxiv.ArxivSource
import dev.readana.source.opds.OpdsSource
import dev.readana.source.openlibrary.OpenLibrarySource
import dev.readana.source.trending.OpenLibraryTrending
import dev.readana.source.trending.TrendingBooks
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Every [BookSource] the app knows about. Add a source by adding a new `@IntoSet` provider; what
 * the user has to configure for it travels with the source itself (`BookSource.configFields`), so
 * nothing here or in the settings screen needs to know its name.
 *
 * Catalogs the user added are not listed here: they are data, and `BookSourceRegistry` builds one
 * source per stored catalog through [CatalogSourceFactory].
 *
 * Project Gutenberg is deliberately not registered: its catalogue is public-domain only, which
 * mostly added noise to results. The `source:gutenberg` module is still there if it is ever wanted
 * back — re-adding its provider here is all it takes.
 */
@Module
@InstallIn(SingletonComponent::class)
object SourcesModule {
    /** Not a [BookSource]: it suggests what to search for, it cannot serve files. */
    @Provides
    @Singleton
    fun providesTrendingBooks(client: HttpClient): TrendingBooks = OpenLibraryTrending(client)

    @Provides
    @Singleton
    fun providesCatalogSourceFactory(client: HttpClient, settings: SettingsRepository): CatalogSourceFactory =
        CatalogSourceFactory { catalog ->
            OpdsSource(
                client = client,
                catalogId = catalog.id,
                catalogName = catalog.name,
                feedUrl = catalog.feedUrl,
                config = { settings.sourceSettings.first().configFor(catalog.id) }
            )
        }

    @Provides
    @IntoSet
    @Singleton
    fun providesOpenLibrary(client: HttpClient): BookSource = OpenLibrarySource(client)

    @Provides
    @IntoSet
    @Singleton
    fun providesArxiv(client: HttpClient, settings: SettingsRepository): BookSource = ArxivSource(
        client = client,
        config = { settings.sourceSettings.first().configFor(ArxivSource.ID.value) }
    )

    @Provides
    @IntoSet
    @Singleton
    fun providesAnnasArchive(client: HttpClient, settings: SettingsRepository, verifier: BrowserVerifier): BookSource =
        AnnasArchiveSource(
            client = client,
            config = {
                AnnasConfig.from(settings.sourceSettings.first().configFor(AnnasArchiveSource.ID.value))
            },
            verifier = verifier,
            // Mirrors rotate and get blocked; remembering the one that answered saves the next search
            // from walking the list again after a restart.
            onWorkingMirror = { mirror ->
                settings.updateSourceSettings {
                    it.withConfigValue(
                        AnnasArchiveSource.ID.value,
                        AnnasConfig.KEY_PREFERRED_MIRROR,
                        mirror
                    )
                }
            }
        )
}
