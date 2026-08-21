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
import dev.readana.source.gutenberg.GutendexSource
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Every [BookSource] the app knows about. Add a new source by adding a new `@IntoSet` provider. */
@Module
@InstallIn(SingletonComponent::class)
object SourcesModule {
    @Provides
    @IntoSet
    @Singleton
    fun providesGutenberg(client: HttpClient): BookSource = GutendexSource(client)

    @Provides
    @IntoSet
    @Singleton
    fun providesAnnasArchive(client: HttpClient, settings: SettingsRepository): BookSource = AnnasArchiveSource(
        client = client,
        config = {
            val stored = settings.sourceSettings.first()
            AnnasConfig(
                mirrors = stored.annasMirrors.ifEmpty { AnnasConfig.DEFAULT_MIRRORS },
                secretKey = stored.annasSecretKey
            )
        }
    )
}
