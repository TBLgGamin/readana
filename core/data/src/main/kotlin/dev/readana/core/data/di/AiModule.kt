package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.ai.client.DefaultLlmClientFactory
import dev.readana.ai.client.LlmClientFactory
import dev.readana.core.data.ai.AiAssistant
import dev.readana.core.data.ai.AiProviderProbe
import dev.readana.core.data.ai.DefaultAiAssistant
import dev.readana.core.data.ai.DefaultAiProviderProbe
import dev.readana.core.data.ai.PictureSearch
import dev.readana.core.data.ai.WikipediaPictureSearch
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.data.repository.RoomAiThreadsRepository
import io.ktor.client.HttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The HTTP client the assistant talks through. Deliberately not the shared one: that carries the
 * WebView's cookies and browser user agent (which have no business going to an AI vendor) and
 * cuts reads after ten seconds, which would kill a streamed answer mid-sentence.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiHttp

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun providesLlmClientFactory(@AiHttp httpClient: HttpClient): LlmClientFactory = DefaultLlmClientFactory(httpClient)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {
    @Binds
    abstract fun bindsAiAssistant(impl: DefaultAiAssistant): AiAssistant

    @Binds
    abstract fun bindsAiProviderProbe(impl: DefaultAiProviderProbe): AiProviderProbe

    @Binds
    abstract fun bindsPictureSearch(impl: WikipediaPictureSearch): PictureSearch

    @Binds
    abstract fun bindsAiThreadsRepository(impl: RoomAiThreadsRepository): AiThreadsRepository
}
