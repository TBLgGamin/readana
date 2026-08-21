package dev.readana.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.source.annas.AnnasArchiveSource
import dev.readana.source.annas.AnnasConfig
import dev.readana.source.api.BookSource
import dev.readana.source.api.BrowserVerifier
import dev.readana.source.trending.OpenLibraryTrending
import dev.readana.source.trending.TrendingBooks
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Every [BookSource] the app knows about. Add a new source by adding a new `@IntoSet` provider.
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
    @IntoSet
    @Singleton
    fun providesAnnasArchive(client: HttpClient, settings: SettingsRepository, verifier: BrowserVerifier): BookSource =
        AnnasArchiveSource(
            client = client,
            config = {
                val stored = settings.sourceSettings.first()
                AnnasConfig(
                    mirrors = stored.annasMirrors.ifEmpty { AnnasConfig.DEFAULT_MIRRORS },
                    secretKey = stored.annasSecretKey,
                    preferredMirror = stored.annasPreferredMirror
                )
            },
            verifier = verifier,
            // Mirrors rotate and get blocked; remembering the one that answered saves the next search
            // from walking the list again after a restart.
            onWorkingMirror = { mirror ->
                settings.updateSourceSettings { it.copy(annasPreferredMirror = mirror) }
            }
        )
}
